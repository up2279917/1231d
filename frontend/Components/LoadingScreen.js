import React from "react"
import Icon from "@mdi/react"
import { mdiEarth, mdiLoading } from "@mdi/js"

export default function LoadingScreen() {
	return (
		<div className="flex flex-col items-center justify-center min-h-[400px] rounded-lg">
			<div className="relative">
				<div className="absolute -inset-4 animate-ping rounded-full bg-ice-500/20 dark:bg-ice-400/20" />
				<div className="relative">
					<Icon
						path={mdiEarth}
						size={4}
						className="animate-spin-slow text-ice-500/30 dark:text-ice-400/30"
					/>
				</div>
			</div>
			<div className="mt-6 space-y-2 text-center animate-fade-in">
				<h3 className="text-lg font-medium text-gray-900 dark:text-gray-100">Loading Map</h3>
				<p className="text-sm text-gray-500 dark:text-gray-400">Fetching world data...</p>
			</div>
		</div>
	)
}
