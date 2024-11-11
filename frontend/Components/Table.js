import React, { useState, useEffect } from "react";
import axios from "axios";
import { format } from "date-fns";
import Icon from "@mdi/react";
import { mdiChevronLeft, mdiChevronRight } from "@mdi/js";

const WarningsTable = () => {
	const [warnings, setWarnings] = useState([]);
	const [currentPage, setCurrentPage] = useState(1);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState(null);

	const itemsPerPage = 5;

	useEffect(() => {
		const fetchWarnings = async () => {
			try {
				const response = await axios.get(
					"https://mcsrv.bytefi.sh/api/warnings",
					{
						headers: {
							"Cache-Control": "max-age=30",
						},
					},
				);

				const sortedWarnings = response.data
					.filter((warning) => warning && warning.timestamp)
					.sort((a, b) => b.timestamp - a.timestamp);

				setWarnings(sortedWarnings);
				setLoading(false);
			} catch (err) {
				console.error("Failed to load warnings:", err);
				setError("Failed to load warnings");
				setLoading(false);
			}
		};

		fetchWarnings();

		// Refresh warnings every 60 seconds
		const interval = setInterval(fetchWarnings, 60000);
		return () => clearInterval(interval);
	}, []);

	const totalPages = Math.ceil(warnings.length / itemsPerPage);
	const startIndex = (currentPage - 1) * itemsPerPage;
	const currentWarnings = warnings.slice(startIndex, startIndex + itemsPerPage);

	const formatDate = (timestamp) => {
		try {
			return format(new Date(timestamp * 1000), "MMM d, yyyy HH:mm");
		} catch (err) {
			console.error("Invalid date:", timestamp);
			return "Invalid date";
		}
	};

	if (loading) {
		return (
			<div className="text-neutral-900 dark:text-neutral-300">
				Loading warnings...
			</div>
		);
	}

	if (error) {
		return <div className="text-red-500">{error}</div>;
	}

	if (!warnings.length) {
		return (
			<div className="mt-6">
				<div className="text-xl mb-3">Recent Warnings</div>
				<div className="text-neutral-900 dark:text-neutral-300">
					No warnings found.
				</div>
			</div>
		);
	}

	return (
		<div className="mt-6">
			<div className="text-xl mb-3">Recent Warnings</div>
			<div className="overflow-x-auto">
				<table className="w-full border-collapse">
					<thead>
						<tr className="bg-l-fg dark:bg-neutral-900">
							<th className="text-left p-3 text-sm font-medium text-neutral-900 dark:text-neutral-300">
								Player
							</th>
							<th className="text-left p-3 text-sm font-medium text-neutral-900 dark:text-neutral-300">
								Staff
							</th>
							<th className="text-left p-3 text-sm font-medium text-neutral-900 dark:text-neutral-300">
								Reason
							</th>
							<th className="text-left p-3 text-sm font-medium text-neutral-900 dark:text-neutral-300">
								Date
							</th>
						</tr>
					</thead>
					<tbody>
						{currentWarnings.map((warning) => (
							<tr
								key={warning.timestamp}
								className="border-b border-gray-200 dark:border-neutral-800 hover:bg-neutral-50 dark:hover:bg-d-fg"
							>
								<td className="p-3 text-sm text-neutral-900 dark:text-neutral-300 flex items-center gap-2">
									<img
										src={`https://mc-heads.net/avatar/${warning?.player}`}
										className="w-5 h-5"
										alt={warning.player}
										loading="lazy"
									/>
									{warning.player}
								</td>
								<td className="p-3 text-sm text-neutral-900 dark:text-neutral-300">
									{warning.issuer}
								</td>
								<td className="p-3 text-sm text-neutral-900 dark:text-neutral-300">
									{warning.reason}
								</td>
								<td className="p-3 text-sm text-neutral-900 dark:text-neutral-300">
									{formatDate(warning.timestamp)}
								</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>

			{totalPages > 1 && (
				<div className="flex items-center justify-between mt-4">
					<div className="text-sm text-neutral-900 dark:text-neutral-300">
						Page {currentPage} of {totalPages}
					</div>
					<div className="flex gap-2">
						<button
							onClick={() => setCurrentPage((prev) => Math.max(prev - 1, 1))}
							disabled={currentPage === 1}
							className="p-1 rounded hover:bg-neutral-100 dark:hover:bg-neutral-800 disabled:opacity-50"
							aria-label="Previous page"
						>
							<Icon
								path={mdiChevronLeft}
								size={1}
								className="text-neutral-900 dark:text-neutral-300"
							/>
						</button>
						<button
							onClick={() =>
								setCurrentPage((prev) => Math.min(prev + 1, totalPages))
							}
							disabled={currentPage === totalPages}
							className="p-1 rounded hover:bg-neutral-100 dark:hover:bg-neutral-800 disabled:opacity-50"
							aria-label="Next page"
						>
							<Icon
								path={mdiChevronRight}
								size={1}
								className="text-neutral-900 dark:text-neutral-300"
							/>
						</button>
					</div>
				</div>
			)}
		</div>
	);
};

export default WarningsTable;
