import React, { useRef, useEffect, useCallback } from "react";
import { WebGLMapRenderer } from "../lib/webgl/WebGLMapRenderer";
import { PerformanceMonitor } from "../lib/performanceMonitor";

const WebGLMapCanvas = React.memo(
	({
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
	}) => {
		const canvasRef = useRef(null);
		const rendererRef = useRef(null);
		const perfMonitor = useRef(new PerformanceMonitor("WebGLMap"));

		// Initialize renderer
		useEffect(() => {
			if (!canvasRef.current) return;

			try {
				rendererRef.current = new WebGLMapRenderer(
					canvasRef.current,
					perfMonitor.current,
				);

				if (visibleBiomes?.length) {
					rendererRef.current.updateBiomeData(visibleBiomes);
				}

				return () => {
					if (rendererRef.current) {
						rendererRef.current.cleanup();
						rendererRef.current = null;
					}
				};
			} catch (error) {
				console.error("Failed to initialize WebGL renderer:", error);
			}
		}, []);

		// Handle mouse move for hover effects
		const handleMouseMove = useCallback(
			(e) => {
				if (isInteractionDisabled || !rendererRef.current) return;

				const rect = canvasRef.current.getBoundingClientRect();
				const x = (e.clientX - rect.left) * window.devicePixelRatio;
				const y = (e.clientY - rect.top) * window.devicePixelRatio;

				if (biomeHoverEnabled || players.length > 0 || locations.length > 0) {
					const hoveredEntity = rendererRef.current.pick(
						x,
						y,
						transform,
						viewportDimensions,
						visibleBiomes,
						players,
						locations,
					);
					setHoveredEntity(hoveredEntity);
				}
			},
			[
				isInteractionDisabled,
				biomeHoverEnabled,
				transform,
				viewportDimensions,
				visibleBiomes,
				players,
				locations,
				setHoveredEntity,
			],
		);

		// Update and render
		useEffect(() => {
			if (!rendererRef.current) return;

			// Update biome data if dimension changes
			if (visibleBiomes?.length) {
				rendererRef.current.updateBiomeData(visibleBiomes);
			}

			rendererRef.current.render(
				transform,
				viewportDimensions,
				visibleBiomes,
				players.filter((p) => p.world === currentDimension),
				locations.filter((l) => l.world === currentDimension),
				hoveredEntity,
				biomeHoverEnabled,
			);
		}, [
			transform,
			viewportDimensions,
			visibleBiomes,
			players,
			locations,
			currentDimension,
			hoveredEntity,
			biomeHoverEnabled,
		]);

		return (
			<canvas
				ref={canvasRef}
				className="border border-gray-200 dark:border-neutral-700 rounded-t-lg md:rounded-l-lg md:rounded-tr-none w-full h-full"
				onMouseMove={handleMouseMove}
				onMouseLeave={() => setHoveredEntity(null)}
			/>
		);
	},
);

export default WebGLMapCanvas;
