import React, { useRef, useEffect, useCallback, useMemo } from "react";

const CHUNK_SIZE = 100;
const PLAYER_ICON_SIZE = 20;
const BIOME_TILE_SIZE = 16;
const GRID_CELL_SIZE = 512;
const RENDER_MARGIN = 1;

const MapCanvas = React.forwardRef(
	(
		{
			viewportDimensions,
			transform,
			visibleBiomes,
			players,
			locations,
			hoveredEntity,
			setHoveredEntity,
			currentDimension,
			isInteractionDisabled,
			biomeHoverEnabled,
		},
		ref,
	) => {
		const mainCanvasRef = useRef(null);
		const offscreenCanvasRef = useRef(null);
		const biomeGridRef = useRef(new Map()); // Spatial partition grid
		const playerAvatarCache = useRef({});
		const lastRenderRef = useRef({ transform: null, timestamp: 0 });
		const animationFrameRef = useRef(null);

		// World to canvas coordinate conversion
		const worldToCanvas = useCallback(
			(worldX, worldZ) => ({
				x:
					viewportDimensions.width / 2 +
					(worldX - transform.x) * transform.scale,
				y:
					viewportDimensions.height / 2 +
					(worldZ - transform.z) * transform.scale,
			}),
			[transform, viewportDimensions],
		);

		// Canvas to world coordinate conversion
		const canvasToWorld = useCallback(
			(canvasX, canvasY) => ({
				x:
					(canvasX - viewportDimensions.width / 2) / transform.scale +
					transform.x,
				z:
					(canvasY - viewportDimensions.height / 2) / transform.scale +
					transform.z,
			}),
			[transform, viewportDimensions],
		);

		const findBiomeAtPosition = useCallback(
			(worldX, worldZ) => {
				// Calculate the size of a biome tile in world coordinates
				const tileSize = BIOME_TILE_SIZE / transform.scale;

				// Find the closest biome tile center
				return visibleBiomes.find((biome) => {
					const dx = Math.abs(biome.x - worldX);
					const dz = Math.abs(biome.z - worldZ);

					// Check if the point is within the bounds of this biome tile
					return dx <= tileSize / 2 && dz <= tileSize / 2;
				});
			},
			[visibleBiomes, transform.scale],
		);

		// Create spatial partition grid
		const updateBiomeGrid = useCallback((biomes) => {
			const grid = new Map();

			biomes.forEach((biome) => {
				const gridX = Math.floor(biome.x / GRID_CELL_SIZE);
				const gridZ = Math.floor(biome.z / GRID_CELL_SIZE);
				const key = `${gridX},${gridZ}`;

				if (!grid.has(key)) {
					grid.set(key, []);
				}
				grid.get(key).push(biome);
			});

			biomeGridRef.current = grid;
		}, []);

		// Get visible grid cells based on current viewport
		const getVisibleGridCells = useCallback(() => {
			const minX = transform.x - viewportDimensions.width / 2 / transform.scale;
			const maxX = transform.x + viewportDimensions.width / 2 / transform.scale;
			const minZ =
				transform.z - viewportDimensions.height / 2 / transform.scale;
			const maxZ =
				transform.z + viewportDimensions.height / 2 / transform.scale;

			const startGridX = Math.floor(minX / GRID_CELL_SIZE) - RENDER_MARGIN;
			const endGridX = Math.floor(maxX / GRID_CELL_SIZE) + RENDER_MARGIN;
			const startGridZ = Math.floor(minZ / GRID_CELL_SIZE) - RENDER_MARGIN;
			const endGridZ = Math.floor(maxZ / GRID_CELL_SIZE) + RENDER_MARGIN;

			const cells = [];
			for (let x = startGridX; x <= endGridX; x++) {
				for (let z = startGridZ; z <= endGridZ; z++) {
					const key = `${x},${z}`;
					const biomesInCell = biomeGridRef.current.get(key);
					if (biomesInCell) {
						cells.push(biomesInCell);
					}
				}
			}
			return cells;
		}, [transform, viewportDimensions]);

		const getMousePosition = useCallback((e) => {
			const canvas = mainCanvasRef.current;
			if (!canvas) return null;

			const rect = canvas.getBoundingClientRect();
			const scaleX = canvas.width / rect.width; // relationship bitmap vs. element for X
			const scaleY = canvas.height / rect.height; // relationship bitmap vs. element for Y

			return {
				x: (e.clientX - rect.left) * scaleX, // scale mouse coordinates after they have
				y: (e.clientY - rect.top) * scaleY, // been adjusted to be relative to element
			};
		}, []);

		// Handle mouse move for entity hovering
		const handleMouseMove = useCallback(
			(e) => {
				if (isInteractionDisabled) return;

				const mousePos = getMousePosition(e);
				if (!mousePos) return;

				const worldPos = canvasToWorld(mousePos.x, mousePos.y);

				// Check for players in current dimension
				const hoverRadius = 10 / transform.scale;
				const hoveredPlayer = players
					.filter((player) => player.world === currentDimension)
					.find(
						(player) =>
							Math.abs(player.x - worldPos.x) < hoverRadius &&
							Math.abs(player.z - worldPos.z) < hoverRadius,
					);

				if (hoveredPlayer) {
					setHoveredEntity({
						...hoveredPlayer,
						type: "player",
						description: "",
					});
					return;
				}

				// Check for locations in current dimension
				const locationRadius = 20 / transform.scale;
				const hoveredLocation = locations
					.filter((location) => location.world === currentDimension)
					.find(
						(location) =>
							Math.abs(location.x - worldPos.x) < locationRadius &&
							Math.abs(location.z - worldPos.z) < locationRadius,
					);

				if (hoveredLocation) {
					setHoveredEntity({
						...hoveredLocation,
						type: "location",
						description:
							hoveredLocation.description ||
							`${hoveredLocation.owner === "server" ? "Server" : "Player"} Location`,
					});
					return;
				}

				// Check for biomes if hover is enabled
				if (biomeHoverEnabled) {
					const hoveredBiome = findBiomeAtPosition(worldPos.x, worldPos.z);
					if (hoveredBiome) {
						setHoveredEntity({
							...hoveredBiome,
							type: "biome",
							description: hoveredBiome.biome
								.toLowerCase()
								.split("_")
								.map((word) => word.charAt(0).toUpperCase() + word.slice(1))
								.join(" "),
						});
						return;
					}
				}

				setHoveredEntity(null);
			},
			[
				players,
				locations,
				transform.scale,
				canvasToWorld,
				setHoveredEntity,
				currentDimension,
				isInteractionDisabled,
				biomeHoverEnabled,
				findBiomeAtPosition,
				getMousePosition,
			],
		);

		// Batch render biomes for a grid cell
		const renderBiomeCell = useCallback(
			(ctx, biomes) => {
				// Group biomes by color for batch rendering
				const colorGroups = new Map();

				biomes.forEach((biome) => {
					const color = `${biome.r},${biome.g},${biome.b}`;
					if (!colorGroups.has(color)) {
						colorGroups.set(color, []);
					}
					colorGroups.get(color).push(biome);
				});

				// Render each color group in a single batch
				colorGroups.forEach((biomesInGroup, color) => {
					const [r, g, b] = color.split(",");
					ctx.fillStyle = `rgba(${r}, ${g}, ${b}, 0.5)`;

					ctx.beginPath();
					biomesInGroup.forEach((biome) => {
						const pos = worldToCanvas(biome.x, biome.z);
						const size = BIOME_TILE_SIZE * transform.scale;

						const isHovered =
							hoveredEntity?.type === "biome" &&
							hoveredEntity.x === biome.x &&
							hoveredEntity.z === biome.z;

						if (isHovered) {
							// Draw filled rectangle for hovered biome
							ctx.fillRect(pos.x - size / 2, pos.y - size / 2, size, size);
							ctx.strokeStyle = "#EAB308"; // Yellow outline
							ctx.lineWidth = 2;
							ctx.strokeRect(pos.x - size / 2, pos.y - size / 2, size, size);
						} else {
							// Add to path for non-hovered biomes
							ctx.rect(pos.x - size / 2, pos.y - size / 2, size, size);
						}
					});

					// Fill all non-hovered biomes at once
					ctx.fill();
				});
			},
			[transform.scale, worldToCanvas, hoveredEntity],
		);

		// Render players and locations
		const renderEntities = useCallback(
			(ctx) => {
				// Pre-load player avatars
				players
					.filter((player) => player.world === currentDimension)
					.forEach((player) => {
						if (!playerAvatarCache.current[player.name]) {
							const img = new Image();
							img.src = `https://mc-heads.net/avatar/${player.name}`;
							playerAvatarCache.current[player.name] = img;
						}
					});

				// Draw locations
				ctx.save();
				locations
					.filter((location) => location.world === currentDimension)
					.forEach((location) => {
						const pos = worldToCanvas(location.x, location.z);
						const isHovered =
							hoveredEntity?.type === "location" &&
							hoveredEntity.x === location.x &&
							hoveredEntity.z === location.z;

						ctx.beginPath();
						ctx.arc(pos.x, pos.y, 4, 0, 2 * Math.PI);
						ctx.fillStyle = isHovered
							? "#EAB308"
							: location.owner === "server"
								? "#EAB308"
								: "#3BCAF6";
						ctx.fill();

						ctx.font = `${location.owner === "server" ? "16px" : "12px"} sans-serif`;
						ctx.fillStyle = isHovered
							? "#EAB308"
							: location.owner === "server"
								? "#EAB308"
								: "#3BCAF6";
						ctx.textAlign = "center";
						ctx.fillText(location.name, pos.x, pos.y - 10);
					});
				ctx.restore();

				// Draw players
				ctx.save();
				players
					.filter((player) => player.world === currentDimension)
					.forEach((player) => {
						const pos = worldToCanvas(player.x, player.z);
						const isHovered =
							hoveredEntity?.type === "player" &&
							hoveredEntity.name === player.name;
						const img = playerAvatarCache.current[player.name];

						if (img?.complete) {
							if (isHovered) {
								ctx.strokeStyle = "#EAB308";
								ctx.lineWidth = 2;
								ctx.strokeRect(
									pos.x - PLAYER_ICON_SIZE / 2 - 2,
									pos.y - PLAYER_ICON_SIZE / 2 - 2,
									PLAYER_ICON_SIZE + 4,
									PLAYER_ICON_SIZE + 4,
								);
							}

							ctx.drawImage(
								img,
								pos.x - PLAYER_ICON_SIZE / 2,
								pos.y - PLAYER_ICON_SIZE / 2,
								PLAYER_ICON_SIZE,
								PLAYER_ICON_SIZE,
							);

							ctx.fillStyle = isHovered ? "#EAB308" : "#ffffff";
							ctx.font = "12px sans-serif";
							ctx.textAlign = "center";
							ctx.fillText(player.name, pos.x, pos.y - PLAYER_ICON_SIZE);
						}
					});
				ctx.restore();
			},
			[currentDimension, hoveredEntity, locations, players, worldToCanvas],
		);

		// Main render function with throttling and batch processing
		const render = useCallback(() => {
			if (!mainCanvasRef.current || !offscreenCanvasRef.current) return;

			const now = performance.now();
			const mainCtx = mainCanvasRef.current.getContext("2d", { alpha: false });
			const offscreenCtx = offscreenCanvasRef.current.getContext("2d", {
				alpha: false,
			});

			// Skip render if transform hasn't changed and it's been less than 16ms
			if (
				lastRenderRef.current.transform === JSON.stringify(transform) &&
				now - lastRenderRef.current.timestamp < 16
			) {
				animationFrameRef.current = requestAnimationFrame(render);
				return;
			}

			// Clear both canvases
			mainCtx.fillStyle = "#121212"; // Dark background
			offscreenCtx.fillStyle = "#121212";
			mainCtx.fillRect(
				0,
				0,
				viewportDimensions.width,
				viewportDimensions.height,
			);
			offscreenCtx.fillRect(
				0,
				0,
				viewportDimensions.width,
				viewportDimensions.height,
			);

			// Get visible grid cells and render biomes
			const visibleCells = getVisibleGridCells();
			visibleCells.forEach((biomesInCell) => {
				renderBiomeCell(offscreenCtx, biomesInCell);
			});

			// Copy offscreen canvas to main canvas
			mainCtx.drawImage(offscreenCanvasRef.current, 0, 0);

			// Render players and locations on main canvas
			renderEntities(mainCtx);

			// Update last render info
			lastRenderRef.current = {
				transform: JSON.stringify(transform),
				timestamp: now,
			};

			animationFrameRef.current = requestAnimationFrame(render);
		}, [
			transform,
			viewportDimensions,
			getVisibleGridCells,
			renderBiomeCell,
			renderEntities,
		]);

		// Initialize offscreen canvas and update biome grid
		useEffect(() => {
			if (!mainCanvasRef.current) return;

			offscreenCanvasRef.current = document.createElement("canvas");
			offscreenCanvasRef.current.width = viewportDimensions.width;
			offscreenCanvasRef.current.height = viewportDimensions.height;

			updateBiomeGrid(visibleBiomes);
		}, [viewportDimensions, visibleBiomes, updateBiomeGrid]);

		// Start/stop render loop
		useEffect(() => {
			render();
			return () => {
				if (animationFrameRef.current) {
					cancelAnimationFrame(animationFrameRef.current);
				}
			};
		}, [render]);

		// Update canvas dimensions
		useEffect(() => {
			if (!mainCanvasRef.current || !offscreenCanvasRef.current) return;

			mainCanvasRef.current.width = viewportDimensions.width;
			mainCanvasRef.current.height = viewportDimensions.height;
			offscreenCanvasRef.current.width = viewportDimensions.width;
			offscreenCanvasRef.current.height = viewportDimensions.height;
		}, [viewportDimensions]);

		// Handle canvas ref forwarding
		useEffect(() => {
			if (ref) {
				if (typeof ref === "function") {
					ref(mainCanvasRef.current);
				} else {
					ref.current = mainCanvasRef.current;
				}
			}
		}, [ref]);

		return (
			<canvas
				ref={mainCanvasRef}
				width={viewportDimensions.width}
				height={viewportDimensions.height}
				className="border border-gray-200 dark:border-neutral-700 rounded-t-lg md:rounded-l-lg md:rounded-tr-none"
				onMouseMove={handleMouseMove}
				onMouseLeave={() => setHoveredEntity(null)}
			/>
		);
	},
);

export default MapCanvas;
