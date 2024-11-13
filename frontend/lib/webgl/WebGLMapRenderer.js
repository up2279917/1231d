import { WebGLUtils } from "./utils";
import { TextureManager } from "./TextureManager";
import {
	BIOME_VERTEX_SHADER,
	BIOME_FRAGMENT_SHADER,
	ENTITY_VERTEX_SHADER,
	ENTITY_FRAGMENT_SHADER,
	VERTEX_SIZES,
	BUFFER_SIZES,
	CHUNK_SIZE,
	PLAYER_ICON_SIZE,
	LOCATION_ICON_SIZE,
} from "./shaders";

const BUFFER_SIZE = 1000000;

export class WebGLMapRenderer {
	constructor(canvas, perfMonitor) {
		this.canvas = canvas;
		this.perfMonitor = perfMonitor;
		this.gl = canvas.getContext("webgl", {
			antialias: false, // Disable antialiasing for better performance
			preserveDrawingBuffer: false,
			depth: false,
			alpha: false,
		});

		if (!this.gl) throw new Error("WebGL not supported");

		this.setupWebGL();
		this.createPrograms();
		this.createBuffers();

		this.biomeGrid = new Map();
		this.currentOffset = 0;
		this.frameRequest = null;
		this.lastFrameTime = 0;

		// Cache for viewport calculations
		this.viewport = {
			width: 0,
			height: 0,
			devicePixelRatio: window.devicePixelRatio || 1,
		};

		// Throttle resize handler
		this.resizeTimeout = null;
	}

	setupWebGL() {
		const gl = this.gl;
		gl.enable(gl.BLEND);
		gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
		gl.clearColor(0.07, 0.07, 0.07, 1.0);
	}

	updateViewport() {
		const dpr = window.devicePixelRatio || 1;
		const displayWidth = Math.floor(this.canvas.clientWidth * dpr);
		const displayHeight = Math.floor(this.canvas.clientHeight * dpr);

		// Only update if dimensions actually changed
		if (
			this.canvas.width !== displayWidth ||
			this.canvas.height !== displayHeight ||
			this.viewport.devicePixelRatio !== dpr
		) {
			this.canvas.width = displayWidth;
			this.canvas.height = displayHeight;
			this.viewport = {
				width: displayWidth,
				height: displayHeight,
				devicePixelRatio: dpr,
			};

			this.gl.viewport(0, 0, displayWidth, displayHeight);
			return true;
		}
		return false;
	}

	createPrograms() {
		const gl = this.gl;

		// Create biome program
		const biomeProgram = this.createShaderProgram(
			BIOME_VERTEX_SHADER,
			BIOME_FRAGMENT_SHADER,
		);
		this.biomeProgram = {
			program: biomeProgram,
			attributes: {
				position: gl.getAttribLocation(biomeProgram, "a_position"),
				color: gl.getAttribLocation(biomeProgram, "a_color"),
				isHovered: gl.getAttribLocation(biomeProgram, "a_isHovered"),
			},
			uniforms: {
				resolution: gl.getUniformLocation(biomeProgram, "u_resolution"),
				translation: gl.getUniformLocation(biomeProgram, "u_translation"),
				scale: gl.getUniformLocation(biomeProgram, "u_scale"),
			},
		};

		// Create entity program
		const entityProgram = this.createShaderProgram(
			ENTITY_VERTEX_SHADER,
			ENTITY_FRAGMENT_SHADER,
		);
		this.entityProgram = {
			program: entityProgram,
			attributes: {
				position: gl.getAttribLocation(entityProgram, "a_position"),
				texCoord: gl.getAttribLocation(entityProgram, "a_texCoord"),
				color: gl.getAttribLocation(entityProgram, "a_color"),
				type: gl.getAttribLocation(entityProgram, "a_type"),
			},
			uniforms: {
				resolution: gl.getUniformLocation(entityProgram, "u_resolution"),
				translation: gl.getUniformLocation(entityProgram, "u_translation"),
				scale: gl.getUniformLocation(entityProgram, "u_scale"),
				texture: gl.getUniformLocation(entityProgram, "u_texture"),
			},
		};
	}

	createBuffers() {
		const gl = this.gl;

		// Create and bind vertex buffer
		this.vertexBuffer = gl.createBuffer();
		gl.bindBuffer(gl.ARRAY_BUFFER, this.vertexBuffer);

		// Allocate buffer big enough for visible area plus margin
		const estimatedVertices = 50000; // Adjust based on typical visible chunks
		const bytesPerVertex = 6 * Float32Array.BYTES_PER_ELEMENT;
		gl.bufferData(
			gl.ARRAY_BUFFER,
			estimatedVertices * bytesPerVertex,
			gl.DYNAMIC_DRAW,
		);

		// Pre-allocate vertex array
		this.vertexData = new Float32Array(estimatedVertices * 6);
	}

	updateBiomeData(biomes) {
		this.biomeGrid.clear();

		for (const biome of biomes) {
			const key =
				Math.floor(biome.x / CHUNK_SIZE) +
				"," +
				Math.floor(biome.z / CHUNK_SIZE);
			if (!this.biomeGrid.has(key)) {
				this.biomeGrid.set(key, []);
			}
			this.biomeGrid.get(key).push(biome);
		}
	}

	getVisibleBiomes(transform, viewport) {
		const scale = transform.scale;
		const margin = CHUNK_SIZE * 2; // Add margin to prevent pop-in

		const halfWidth = viewport.width / scale / 2 + margin;
		const halfHeight = viewport.height / scale / 2 + margin;

		const minX = Math.floor((transform.x - halfWidth) / CHUNK_SIZE);
		const maxX = Math.ceil((transform.x + halfWidth) / CHUNK_SIZE);
		const minZ = Math.floor((transform.z - halfHeight) / CHUNK_SIZE);
		const maxZ = Math.ceil((transform.z + halfHeight) / CHUNK_SIZE);

		const visibleBiomes = [];
		const addedKeys = new Set(); // Track added biomes to prevent duplicates

		for (let x = minX; x <= maxX; x++) {
			for (let z = minZ; z <= maxZ; z++) {
				const key = `${x},${z}`;
				if (!addedKeys.has(key)) {
					const chunks = this.biomeGrid.get(key);
					if (chunks) {
						visibleBiomes.push(...chunks);
						addedKeys.add(key);
					}
				}
			}
		}

		return visibleBiomes;
	}

	updateBuffers(visibleBiomes, players, locations, hoveredEntity) {
		const gl = this.gl;

		// Update biome buffer
		const biomeVertices = new Float32Array(
			visibleBiomes.length * 6 * BIOME_VERTEX_SIZE,
		);
		let biomeOffset = 0;

		visibleBiomes.forEach((biome) => {
			const isHovered =
				hoveredEntity?.type === "biome" &&
				hoveredEntity.x === biome.x &&
				hoveredEntity.z === biome.z
					? 1
					: 0;

			// Add two triangles for the biome quad
			const x = biome.x;
			const z = biome.z;
			const size = CHUNK_SIZE;

			// First triangle
			this.addBiomeVertex(biomeVertices, biomeOffset, x, z, biome, isHovered);
			this.addBiomeVertex(
				biomeVertices,
				biomeOffset + BIOME_VERTEX_SIZE,
				x + size,
				z,
				biome,
				isHovered,
			);
			this.addBiomeVertex(
				biomeVertices,
				biomeOffset + BIOME_VERTEX_SIZE * 2,
				x,
				z + size,
				biome,
				isHovered,
			);

			// Second triangle
			this.addBiomeVertex(
				biomeVertices,
				biomeOffset + BIOME_VERTEX_SIZE * 3,
				x + size,
				z,
				biome,
				isHovered,
			);
			this.addBiomeVertex(
				biomeVertices,
				biomeOffset + BIOME_VERTEX_SIZE * 4,
				x + size,
				z + size,
				biome,
				isHovered,
			);
			this.addBiomeVertex(
				biomeVertices,
				biomeOffset + BIOME_VERTEX_SIZE * 5,
				x,
				z + size,
				biome,
				isHovered,
			);

			biomeOffset += BIOME_VERTEX_SIZE * 6;
		});

		gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.biome.vertices);
		gl.bufferData(gl.ARRAY_BUFFER, biomeVertices, gl.DYNAMIC_DRAW);

		// Update entity buffer (players and locations)
		const entityCount = players.length + locations.length;
		const entityVertices = new Float32Array(
			entityCount * 6 * ENTITY_VERTEX_SIZE,
		);
		let entityOffset = 0;

		// Add players
		players.forEach((player) => {
			const isHovered =
				hoveredEntity?.type === "player" && hoveredEntity.name === player.name
					? 1
					: 0;
			this.addEntityQuad(
				entityVertices,
				entityOffset,
				player.x,
				player.z,
				PLAYER_ICON_SIZE,
				PLAYER_ICON_SIZE,
				[1, 1, 1, 1],
				0, // type: player
				isHovered,
			);
			entityOffset += ENTITY_VERTEX_SIZE * 6;
		});

		// Add locations
		locations.forEach((location) => {
			const isHovered =
				hoveredEntity?.type === "location" &&
				hoveredEntity.x === location.x &&
				hoveredEntity.z === location.z
					? 1
					: 0;
			const color =
				location.owner === "server" ? [1, 0.7, 0.03, 1] : [0.23, 0.79, 0.97, 1];
			this.addEntityQuad(
				entityVertices,
				entityOffset,
				location.x,
				location.z,
				LOCATION_ICON_SIZE,
				LOCATION_ICON_SIZE,
				color,
				1, // type: location
				isHovered,
			);
			entityOffset += ENTITY_VERTEX_SIZE * 6;
		});

		gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.entity.vertices);
		gl.bufferData(gl.ARRAY_BUFFER, entityVertices, gl.DYNAMIC_DRAW);
	}

	addBiomeVertex(x, z, r, g, b, isHovered) {
		if (this.currentOffset + VERTEX_SIZES.BIOME > this.vertexData.length) {
			console.warn("Vertex buffer overflow prevented");
			return;
		}

		this.vertexData[this.currentOffset] = x;
		this.vertexData[this.currentOffset + 1] = z;
		this.vertexData[this.currentOffset + 2] = r;
		this.vertexData[this.currentOffset + 3] = g;
		this.vertexData[this.currentOffset + 4] = b;
		this.vertexData[this.currentOffset + 5] = isHovered;
		this.currentOffset += VERTEX_SIZES.BIOME;
	}

	addEntityQuad(vertices, offset, x, z, width, height, color, type, isHovered) {
		const halfWidth = width / 2;
		const halfHeight = height / 2;

		// First triangle
		this.addEntityVertex(
			vertices,
			offset,
			x - halfWidth,
			z - halfHeight,
			0,
			0,
			color,
			type,
			isHovered,
		);
		this.addEntityVertex(
			vertices,
			offset + ENTITY_VERTEX_SIZE,
			x + halfWidth,
			z - halfHeight,
			1,
			0,
			color,
			type,
			isHovered,
		);
		this.addEntityVertex(
			vertices,
			offset + ENTITY_VERTEX_SIZE * 2,
			x - halfWidth,
			z + halfHeight,
			0,
			1,
			color,
			type,
			isHovered,
		);

		// Second triangle
		this.addEntityVertex(
			vertices,
			offset + ENTITY_VERTEX_SIZE * 3,
			x + halfWidth,
			z - halfHeight,
			1,
			0,
			color,
			type,
			isHovered,
		);
		this.addEntityVertex(
			vertices,
			offset + ENTITY_VERTEX_SIZE * 4,
			x + halfWidth,
			z + halfHeight,
			1,
			1,
			color,
			type,
			isHovered,
		);
		this.addEntityVertex(
			vertices,
			offset + ENTITY_VERTEX_SIZE * 5,
			x - halfWidth,
			z + halfHeight,
			0,
			1,
			color,
			type,
			isHovered,
		);
	}

	addEntityVertex(vertices, offset, x, z, tx, ty, color, type, isHovered) {
		vertices[offset] = x;
		vertices[offset + 1] = z;
		vertices[offset + 2] = tx;
		vertices[offset + 3] = ty;
		vertices[offset + 4] = color[0];
		vertices[offset + 5] = color[1];
		vertices[offset + 6] = color[2];
		vertices[offset + 7] = color[3];
		vertices[offset + 8] = type;
		vertices[offset + 9] = isHovered;
	}

	createShaderProgram(vertexSource, fragmentSource) {
		const gl = this.gl;

		const vertexShader = WebGLUtils.createShader(
			gl,
			gl.VERTEX_SHADER,
			vertexSource,
		);
		const fragmentShader = WebGLUtils.createShader(
			gl,
			gl.FRAGMENT_SHADER,
			fragmentSource,
		);

		if (!vertexShader || !fragmentShader) {
			throw new Error("Failed to create shaders");
		}

		const program = WebGLUtils.createProgram(gl, vertexShader, fragmentShader);
		if (!program) {
			throw new Error("Failed to create shader program");
		}

		return program;
	}

	render(
		transform,
		viewport,
		biomes,
		players,
		locations,
		hoveredEntity,
		biomeHoverEnabled,
	) {
		if (this.frameRequest) {
			cancelAnimationFrame(this.frameRequest);
		}

		this.frameRequest = requestAnimationFrame(() => {
			const now = performance.now();
			if (now - this.lastFrameTime < 16) {
				// Cap at ~60fps
				return;
			}

			this.perfMonitor.startFrame();

			// Update viewport if needed
			const viewportChanged = this.updateViewport();

			const gl = this.gl;
			gl.clear(gl.COLOR_BUFFER_BIT);

			// Get visible biomes with wider margin to prevent pop-in
			const visibleBiomes = this.getVisibleBiomes(transform, {
				width: viewport.width * 1.5,
				height: viewport.height * 1.5,
			});

			// Render biomes
			const biomesStart = performance.now();
			this.renderBiomes(
				visibleBiomes,
				transform,
				this.viewport,
				hoveredEntity,
				biomeHoverEnabled,
			);
			this.perfMonitor.measureBiomeRender(performance.now() - biomesStart);

			this.perfMonitor.endFrame();
			this.lastFrameTime = now;
		});
	}

	renderBiomes(biomes, transform, viewport, hoveredEntity, biomeHoverEnabled) {
		const gl = this.gl;

		// Use program
		gl.useProgram(this.biomeProgram.program);

		// Set uniforms
		gl.uniform2f(
			this.biomeProgram.uniforms.resolution,
			viewport.width,
			viewport.height,
		);
		gl.uniform2f(
			this.biomeProgram.uniforms.translation,
			-transform.x,
			-transform.z,
		);
		gl.uniform1f(this.biomeProgram.uniforms.scale, transform.scale);

		// Reset offset
		this.currentOffset = 0;

		// Generate vertex data
		for (const biome of biomes) {
			const isHovered =
				biomeHoverEnabled &&
				hoveredEntity?.type === "biome" &&
				hoveredEntity.x === biome.x &&
				hoveredEntity.z === biome.z
					? 1
					: 0;

			this.addBiomeQuad(
				biome.x,
				biome.z,
				CHUNK_SIZE,
				CHUNK_SIZE,
				biome.r / 255,
				biome.g / 255,
				biome.b / 255,
				isHovered,
			);
		}

		if (this.currentOffset === 0) return;

		// Bind buffer and upload data
		gl.bindBuffer(gl.ARRAY_BUFFER, this.vertexBuffer);
		gl.bufferSubData(
			gl.ARRAY_BUFFER,
			0,
			this.vertexData.subarray(0, this.currentOffset),
		);

		// Set vertex attributes
		const stride = 6 * Float32Array.BYTES_PER_ELEMENT;

		gl.enableVertexAttribArray(this.biomeProgram.attributes.position);
		gl.vertexAttribPointer(
			this.biomeProgram.attributes.position,
			2,
			gl.FLOAT,
			false,
			stride,
			0,
		);

		gl.enableVertexAttribArray(this.biomeProgram.attributes.color);
		gl.vertexAttribPointer(
			this.biomeProgram.attributes.color,
			3,
			gl.FLOAT,
			false,
			stride,
			2 * Float32Array.BYTES_PER_ELEMENT,
		);

		gl.enableVertexAttribArray(this.biomeProgram.attributes.isHovered);
		gl.vertexAttribPointer(
			this.biomeProgram.attributes.isHovered,
			1,
			gl.FLOAT,
			false,
			stride,
			5 * Float32Array.BYTES_PER_ELEMENT,
		);

		// Draw
		gl.drawArrays(gl.TRIANGLES, 0, this.currentOffset / 6);

		// Cleanup
		gl.disableVertexAttribArray(this.biomeProgram.attributes.position);
		gl.disableVertexAttribArray(this.biomeProgram.attributes.color);
		gl.disableVertexAttribArray(this.biomeProgram.attributes.isHovered);
	}

	updateCanvasSize(viewport) {
		const canvas = this.canvas;
		const gl = this.gl;

		if (canvas.width !== viewport.width || canvas.height !== viewport.height) {
			canvas.width = viewport.width;
			canvas.height = viewport.height;
			gl.viewport(0, 0, viewport.width, viewport.height);
		}
	}

	renderEntities(players, locations, transform, viewport, hoveredEntity) {
		const gl = this.gl;

		// Use entity program
		gl.useProgram(this.entityProgram.program);

		// Set uniforms
		gl.uniform2f(
			this.entityProgram.uniforms.resolution,
			viewport.width,
			viewport.height,
		);
		gl.uniform2f(
			this.entityProgram.uniforms.translation,
			-transform.x,
			-transform.z,
		);
		gl.uniform1f(this.entityProgram.uniforms.scale, transform.scale);

		// Reset vertex data offset
		this.currentOffset = 0;

		// Add vertices for locations
		for (const location of locations) {
			const isHovered =
				hoveredEntity?.type === "location" &&
				hoveredEntity.x === location.x &&
				hoveredEntity.z === location.z
					? 1
					: 0;

			const color =
				location.owner === "server"
					? [1.0, 0.7, 0.03, 1.0] // Yellow for server locations
					: [0.23, 0.79, 0.97, 1.0]; // Blue for player locations

			this.addEntityQuad(
				location.x,
				location.z,
				LOCATION_ICON_SIZE,
				LOCATION_ICON_SIZE,
				color,
				1, // type: location
				isHovered,
			);
		}

		// Add vertices for players
		for (const player of players) {
			const isHovered =
				hoveredEntity?.type === "player" && hoveredEntity.name === player.name
					? 1
					: 0;

			this.addEntityQuad(
				player.x,
				player.z,
				PLAYER_ICON_SIZE,
				PLAYER_ICON_SIZE,
				[1, 1, 1, 1], // White for player icons
				0, // type: player
				isHovered,
			);
		}

		if (this.currentOffset === 0) return; // Nothing to render

		// Update buffer with new data
		gl.bindBuffer(gl.ARRAY_BUFFER, this.vertexBuffer);
		gl.bufferSubData(
			gl.ARRAY_BUFFER,
			0,
			this.vertexData.subarray(0, this.currentOffset),
		);

		// Set attributes
		const stride = 9 * 4; // 9 floats * 4 bytes
		gl.enableVertexAttribArray(this.entityProgram.attributes.position);
		gl.vertexAttribPointer(
			this.entityProgram.attributes.position,
			2,
			gl.FLOAT,
			false,
			stride,
			0,
		);

		gl.enableVertexAttribArray(this.entityProgram.attributes.texCoord);
		gl.vertexAttribPointer(
			this.entityProgram.attributes.texCoord,
			2,
			gl.FLOAT,
			false,
			stride,
			8,
		);

		gl.enableVertexAttribArray(this.entityProgram.attributes.color);
		gl.vertexAttribPointer(
			this.entityProgram.attributes.color,
			4,
			gl.FLOAT,
			false,
			stride,
			16,
		);

		gl.enableVertexAttribArray(this.entityProgram.attributes.type);
		gl.vertexAttribPointer(
			this.entityProgram.attributes.type,
			1,
			gl.FLOAT,
			false,
			stride,
			32,
		);

		// Draw entities
		gl.drawArrays(gl.TRIANGLES, 0, this.currentOffset / 9);
	}

	addBiomeQuad(x, z, width, height, r, g, b, isHovered) {
		if (this.currentOffset >= this.vertexData.length - 36) {
			console.warn("Buffer full, skipping biome");
			return;
		}

		// Add vertices for first triangle
		this.addBiomeVertex(x, z, r, g, b, isHovered);
		this.addBiomeVertex(x + width, z, r, g, b, isHovered);
		this.addBiomeVertex(x, z + height, r, g, b, isHovered);

		// Add vertices for second triangle
		this.addBiomeVertex(x + width, z, r, g, b, isHovered);
		this.addBiomeVertex(x + width, z + height, r, g, b, isHovered);
		this.addBiomeVertex(x, z + height, r, g, b, isHovered);
	}

	addEntityQuad(x, z, width, height, color, type, isHovered) {
		const halfWidth = width / 2;
		const halfHeight = height / 2;

		// Add two triangles to form a quad
		this.addEntityVertex(
			x - halfWidth,
			z - halfHeight,
			0,
			0,
			color,
			type,
			isHovered,
		);
		this.addEntityVertex(
			x + halfWidth,
			z - halfHeight,
			1,
			0,
			color,
			type,
			isHovered,
		);
		this.addEntityVertex(
			x - halfWidth,
			z + halfHeight,
			0,
			1,
			color,
			type,
			isHovered,
		);

		this.addEntityVertex(
			x + halfWidth,
			z - halfHeight,
			1,
			0,
			color,
			type,
			isHovered,
		);
		this.addEntityVertex(
			x + halfWidth,
			z + halfHeight,
			1,
			1,
			color,
			type,
			isHovered,
		);
		this.addEntityVertex(
			x - halfWidth,
			z + halfHeight,
			0,
			1,
			color,
			type,
			isHovered,
		);
	}

	addEntityVertex(x, z, tx, ty, color, type, isHovered) {
		if (this.currentOffset + VERTEX_SIZES.ENTITY > this.vertexData.length) {
			console.warn("Vertex buffer overflow prevented");
			return;
		}

		this.vertexData[this.currentOffset] = x;
		this.vertexData[this.currentOffset + 1] = z;
		this.vertexData[this.currentOffset + 2] = tx;
		this.vertexData[this.currentOffset + 3] = ty;
		this.vertexData[this.currentOffset + 4] = color[0];
		this.vertexData[this.currentOffset + 5] = color[1];
		this.vertexData[this.currentOffset + 6] = color[2];
		this.vertexData[this.currentOffset + 7] = color[3];
		this.vertexData[this.currentOffset + 8] = type;
		this.currentOffset += VERTEX_SIZES.ENTITY;
	}

	addBiomeVertex(x, z, r, g, b, isHovered) {
		this.vertexData[this.currentOffset++] = x;
		this.vertexData[this.currentOffset++] = z;
		this.vertexData[this.currentOffset++] = r;
		this.vertexData[this.currentOffset++] = g;
		this.vertexData[this.currentOffset++] = b;
		this.vertexData[this.currentOffset++] = isHovered;
	}

	renderText(text, x, z, color = [1, 1, 1, 1], fontSize = 12) {
		const gl = this.gl;

		gl.useProgram(program.program);

		const texture = this.textureManager.getTextTexture(text, fontSize);
		gl.bindTexture(gl.TEXTURE_2D, texture);

		// Create vertices for text quad
		const width = fontSize * text.length * 0.6; // Approximate width
		const height = fontSize;
		const vertices = new Float32Array([
			x - width / 2,
			z - height / 2,
			0,
			0,
			x + width / 2,
			z - height / 2,
			1,
			0,
			x - width / 2,
			z + height / 2,
			0,
			1,
			x + width / 2,
			z - height / 2,
			1,
			0,
			x + width / 2,
			z + height / 2,
			1,
			1,
			x - width / 2,
			z + height / 2,
			0,
			1,
		]);

		gl.bindBuffer(gl.ARRAY_BUFFER, this.buffers.text.vertices);
		gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.DYNAMIC_DRAW);

		// Set attributes and uniforms
		gl.enableVertexAttribArray(program.attributes.position);
		gl.vertexAttribPointer(
			program.attributes.position,
			2,
			gl.FLOAT,
			false,
			TEXT_VERTEX_SIZE * 4,
			0,
		);

		gl.enableVertexAttribArray(program.attributes.texCoord);
		gl.vertexAttribPointer(
			program.attributes.texCoord,
			2,
			gl.FLOAT,
			false,
			TEXT_VERTEX_SIZE * 4,
			8,
		);

		gl.uniform4fv(program.uniforms.color, color);

		// Draw text
		gl.drawArrays(gl.TRIANGLES, 0, 6);
	}

	// GPU-based picking for hover detection
	pick(screenX, screenY, transform, viewport, biomes, players, locations) {
		const worldPos = WebGLUtils.screenToWorld(
			screenX,
			screenY,
			transform,
			viewport.width,
			viewport.height,
		);

		// Check for entity hover
		const hoverRadius = 10 / transform.scale;

		// Check players first
		const hoveredPlayer = players.find((player) => {
			const dx = player.x - worldPos.x;
			const dz = player.z - worldPos.z;
			return Math.sqrt(dx * dx + dz * dz) < hoverRadius;
		});

		if (hoveredPlayer) {
			return {
				type: "player",
				...hoveredPlayer,
			};
		}

		// Then check locations
		const hoveredLocation = locations.find((location) => {
			const dx = location.x - worldPos.x;
			const dz = location.z - worldPos.z;
			return Math.sqrt(dx * dx + dz * dz) < hoverRadius;
		});

		if (hoveredLocation) {
			return {
				type: "location",
				...hoveredLocation,
			};
		}

		// Finally check biomes
		const gridX = Math.floor(worldPos.x / CHUNK_SIZE);
		const gridZ = Math.floor(worldPos.z / CHUNK_SIZE);
		const key = `${gridX},${gridZ}`;

		const biomesInChunk = this.biomeGrid.get(key);
		if (biomesInChunk && biomesInChunk.length > 0) {
			return {
				type: "biome",
				...biomesInChunk[0],
			};
		}

		return null;
	}

	// Clean up resources
	cleanup() {
		if (this.frameRequest) {
			cancelAnimationFrame(this.frameRequest);
		}

		const gl = this.gl;
		if (this.vertexBuffer) {
			gl.deleteBuffer(this.vertexBuffer);
		}
		if (this.biomeProgram) {
			gl.deleteProgram(this.biomeProgram.program);
		}
	}

	// Helper to create shader program
	createProgram(vertexSource, fragmentSource) {
		const gl = this.gl;

		const vertexShader = WebGLUtils.createShader(
			gl,
			gl.VERTEX_SHADER,
			vertexSource,
		);
		const fragmentShader = WebGLUtils.createShader(
			gl,
			gl.FRAGMENT_SHADER,
			fragmentSource,
		);

		if (!vertexShader || !fragmentShader) {
			throw new Error("Failed to create shaders");
		}

		const program = WebGLUtils.createProgram(gl, vertexShader, fragmentShader);
		if (!program) {
			throw new Error("Failed to create shader program");
		}

		return program;
	}
}
