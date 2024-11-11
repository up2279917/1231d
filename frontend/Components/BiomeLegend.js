import React, { useState, useCallback, forwardRef, memo } from "react";
import Icon from "@mdi/react";
import { mdiMapLegend, mdiCursorDefaultOutline } from "@mdi/js";
import DimensionSwitcher from "./DimensionSwitcher";

const BiomeLegend = memo(
	forwardRef(
		(
			{
				allBiomes,
				currentDimension,
				onDimensionChange,
				onExpandedChange,
				onBiomeHoverToggle,
			},
			ref,
		) => {
			const [isExpanded, setIsExpanded] = useState(false);
			const [biomeHoverEnabled, setBiomeHoverEnabled] = useState(false);
			const uniqueBiomes = [...new Set(allBiomes.map((b) => b.biome))].sort();

			const toggleExpanded = useCallback(() => {
				const newState = !isExpanded;
				setIsExpanded(newState);
				onExpandedChange?.(newState);
			}, [isExpanded, onExpandedChange]);

			const handleOverlayClick = useCallback(
				(e) => {
					e.preventDefault();
					e.stopPropagation();
					if (e.target === e.currentTarget) {
						toggleExpanded();
					}
				},
				[toggleExpanded],
			);

			const handleLegendScroll = useCallback((e) => {
				e.stopPropagation();
			}, []);

			const handleDimensionChange = useCallback(
				(dimension) => {
					toggleExpanded();
					onDimensionChange(dimension);
				},
				[onDimensionChange, toggleExpanded],
			);

			const toggleBiomeHover = useCallback(() => {
				const newState = !biomeHoverEnabled;
				setBiomeHoverEnabled(newState);
				onBiomeHoverToggle?.(newState);
			}, [biomeHoverEnabled, onBiomeHoverToggle]);

			return (
				<>
					{isExpanded && (
						<div
							className="fixed inset-0 z-40"
							onClick={handleOverlayClick}
							onTouchEnd={handleOverlayClick}
						/>
					)}
					<div
						ref={ref}
						className={`absolute bottom-16 right-2 bg-white dark:bg-neutral-900 bg-opacity-75 rounded text-sm shadow-lg ${
							isExpanded ? "z-50" : "z-30"
						}`}
					>
						<div className="flex items-center justify-between gap-2 px-2 py-1">
							<div
								className="flex items-center gap-1 cursor-pointer"
								onClick={toggleExpanded}
							>
								<Icon
									path={mdiMapLegend}
									size={0.7}
									className="text-gray-600 dark:text-gray-400"
								/>
								<span className="text-gray-600 dark:text-gray-400">Biomes</span>
							</div>
							<button
								onClick={toggleBiomeHover}
								className={`flex items-center gap-1 px-1.5 py-0.5 rounded text-xs transition-colors ${
									biomeHoverEnabled
										? "bg-ice-500/20 text-ice-600 dark:text-ice-400"
										: "text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-neutral-800"
								}`}
							>
								<Icon path={mdiCursorDefaultOutline} size={0.6} />
								<span>Hover</span>
							</button>
						</div>

						{isExpanded && (
							<div
								className="touch-pan-y px-2 pb-2"
								onClick={(e) => e.stopPropagation()}
								onWheel={handleLegendScroll}
							>
								<div className="max-h-64 overflow-y-auto scrollbar-thin scrollbar-thumb-gray-300 dark:scrollbar-thumb-neutral-700">
									<DimensionSwitcher
										currentDimension={currentDimension}
										onDimensionChange={handleDimensionChange}
									/>
									<div className="space-y-1">
										{uniqueBiomes.map((biomeName) => {
											const biome = allBiomes.find(
												(b) => b.biome === biomeName,
											);
											return (
												<div
													key={biomeName}
													className="flex items-center gap-2 py-0.5"
												>
													<div
														className="w-3 h-3 rounded"
														style={{
															backgroundColor: `rgba(${biome.r}, ${biome.g}, ${biome.b}, 0.5)`,
														}}
													/>
													<span className="text-xs text-gray-800 dark:text-gray-200">
														{biomeName
															.toLowerCase()
															.split("_")
															.map(
																(word) =>
																	word.charAt(0).toUpperCase() + word.slice(1),
															)
															.join(" ")}
													</span>
												</div>
											);
										})}
									</div>
								</div>
							</div>
						)}
					</div>
				</>
			);
		},
	),
);

export default BiomeLegend;
