import React from "react";

const DimensionSwitcher = ({ currentDimension, onDimensionChange }) => {
	const dimensions = [
		{ id: "world", name: "Overworld" },
		{ id: "world_nether", name: "Nether" },
		{ id: "world_the_end", name: "End" },
	];

	return (
		<div className="my-2 border-t border-gray-200 dark:border-neutral-700 pt-2">
			<div className="flex flex-col gap-1">
				{dimensions.map((dimension) => (
					<button
						key={dimension.id}
						onClick={() => onDimensionChange(dimension.id)}
						className={`text-left text-center rounded-sm text-sm transition-colors ${
							currentDimension === dimension.id
								? "bg-gray-200 dark:bg-neutral-700 text-gray-900 dark:text-gray-100"
								: "text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-neutral-800"
						}`}
					>
						{dimension.name}
					</button>
				))}
			</div>
		</div>
	);
};

export default DimensionSwitcher;
