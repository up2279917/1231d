export default function ConnectionStatus({ status, error }) {
	if (status === "connected") {
		return (
			<div className="flex items-center gap-2 text-sm text-green-600 dark:text-green-400">
				<div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
				Connected
			</div>
		);
	}

	if (status === "fallback") {
		return (
			<div className="flex items-center gap-2 text-sm text-yellow-600 dark:text-yellow-400">
				<div className="w-2 h-2 bg-yellow-500 rounded-full" />
				HTTP Fallback
			</div>
		);
	}

	if (status === "connecting" || status === "reconnecting") {
		return (
			<div className="flex items-center gap-2 text-sm text-yellow-600 dark:text-yellow-400">
				<div className="w-2 h-2 bg-yellow-500 rounded-full animate-pulse" />
				{status === "connecting" ? "Connecting..." : "Reconnecting..."}
			</div>
		);
	}

	return (
		<div className="flex items-center gap-2 text-sm text-red-600 dark:text-red-400">
			<div className="w-2 h-2 bg-red-500 rounded-full" />
			{error || "Disconnected"}
		</div>
	);
}
