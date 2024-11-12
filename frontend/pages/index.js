import Meta from "@/Components/Meta";
import { motion } from "framer-motion";
import axios from "axios";
import { useState, useEffect } from "react";
import Link from "next/link";
import { useDispatch, useSelector } from "react-redux";
import ByteFish from "@/Components/ByteFish";
import { setTheme } from "@/State/slice";
import { mdiDiscord } from "@mdi/js";
import Icon from "@mdi/react";

const ServerButton = ({ ip }) => {
	const [showTooltip, setShowTooltip] = useState(false);
	const [copied, setCopied] = useState(false);

	const handleCopy = () => {
		navigator.clipboard.writeText(ip);
		setCopied(true);
		setTimeout(() => {
			setCopied(false);
		}, 500);
	};

	return (
		<div className="relative inline-block">
			{(showTooltip || copied) && (
				<motion.div
					initial={{ opacity: 0, y: 10 }}
					animate={{ opacity: 1, y: 0 }}
					exit={{ opacity: 0, y: 10 }}
					className="absolute w-full text-center -top-8"
				>
					<span className="bg-black text-white text-sm py-1 px-2 rounded shadow-lg border-[#a0a0a0] border">
						{copied ? "Copied!" : "Copy"}
					</span>
				</motion.div>
			)}

			<button
				className="bg-black font-minecraft px-8 py-1.5 text-white border-[#a0a0a0] border-2 hover:border-white"
				onClick={handleCopy}
				onMouseEnter={() => setShowTooltip(true)}
				onMouseLeave={() => setShowTooltip(false)}
			>
				{ip}
			</button>
		</div>
	);
};

export default function Minecraft() {
	const [health, setHealth] = useState({ status: "unknown" });
	const [stats, setStats] = useState({
		onlinePlayers: [],
		playerCount: 0,
		tps: 0,
	});
	const [error, setError] = useState(null);
	const state = useSelector((state) => state.state);
	const dispatch = useDispatch();

	useEffect(() => {
		const fetchHealth = async () => {
			try {
				const response = await axios
					.get("https://mcsrv.bytefi.sh/api/health", {
						headers: {
							"Cache-Control": "max-age=5",
						},
						timeout: 5000,
						validateStatus: function (status) {
							// Consider any status less than 500 as success
							return status < 500;
						},
					})
					.catch((err) => {
						setHealth({ status: "down" });
						setError("Server is currently unavailable");
					});

				if (!response || response?.data?.status === "down") {
					setHealth({ status: "down" });
					setError("Server is currently unavailable");
					return;
				}

				setHealth(response.data || { status: "unknown" });
				setError(null);
			} catch (error) {
				console.error("Health check failed:", error);
				setHealth({ status: "down" });
				// Set a user-friendly error message
				setError(
					error.response?.data?.message ||
						"Unable to connect to the server. Please try again later.",
				);
			}
		};

		const fetchStats = async () => {
			try {
				const response = await axios
					.get("https://mcsrv.bytefi.sh/api/stats", {
						headers: {
							"Cache-Control": "max-age=5",
						},
						timeout: 5000,
						validateStatus: function (status) {
							return status < 500;
						},
					})
					.catch((err) => {
						setHealth({ status: "down" });
						setError("Server is currently unavailable");
						return;
					});

				if (response && response.data) {
					const validatedStats = {
						onlinePlayers: Array.isArray(response.data.onlinePlayers)
							? response.data.onlinePlayers
									.filter((player) => player && player.name)
									.map((player) => ({
										name: player.name,
										isOperator: !!player.isOperator,
									}))
							: [],
						playerCount: Number(response.data.playerCount) || 0,
						tps: Number(response.data.tps) || 0,
					};
					setStats(validatedStats);
					setError(null);
				}
			} catch (error) {
				console.error("Stats fetch failed:", error);
				setStats({ onlinePlayers: [], playerCount: 0, tps: 0 });
				setError(
					error.response?.data?.message ||
						"Unable to fetch server statistics. Please try again later.",
				);
			}
		};

		fetchHealth();
		fetchStats();

		const healthInterval = setInterval(fetchHealth, 60000);
		const statsInterval = setInterval(fetchStats, 30000);

		return () => {
			clearInterval(healthInterval);
			clearInterval(statsInterval);
		};
	}, []);

	return (
		<div>
			<div className="flex w-full justify-center lg:p-5 py-5 px-2 font-grotesk">
				<div className="flex w-full lg:max-w-4xl justify-between items-center">
					<div className="flex justify-start w-full items-center gap-2 text-black dark:text-neutral-100 font-medium">
						<Link href="/" className="cursor-pointer w-10 h-10">
							<ByteFish
								stroke={state.theme == "dark" ? "#ffffff" : "#000000"}
							/>
						</Link>
					</div>
					<div className="flex gap-1 items-center text-black dark:text-white">
						<a
							className={`cursor-pointer px-1.5 text-sm border-b-2 border-black dark:border-0`}
							onClick={() => dispatch(setTheme("light"))}
						>
							Light
						</a>
						<a
							className={`cursor-pointer px-1.5 text-sm bg-transparent dark:border-b-2 border-neutral-200 border-0`}
							onClick={() => dispatch(setTheme("dark"))}
						>
							Dark
						</a>
					</div>
				</div>
			</div>
			<Meta title={"Community Minecraft Server"} />
			<motion.div
				initial={{ opacity: 0 }}
				animate={{ opacity: 1 }}
				exit={{ opacity: 0 }}
				transition={{ delay: 0.2 }}
				key="motion_contact"
				className="flex justify-center w-full font-grotesk px-2 leading-snug "
			>
				<div className="w-full lg:max-w-4xl text-black dark:text-neutral-200 ">
					<a className="text-3xl font-semibold">Minecraft Community Server</a>
					<div className="text-black dark:text-neutral-200 my-1">
						The bytefi.sh community minecraft server is available for anyone to
						freely play. The goal is to mine, craft and survive in the endless
						landscape of Minecraft.
					</div>
					<Link href="/rules" className="text-ice-500 hover:text-ice-600 my-2">
						Server Rules
					</Link>
					<br />
					<Link href="/map" className="text-ice-500 hover:text-ice-600 my-2">
						Live Map
					</Link>
					<br />
					<Link
						href="/plugins"
						className="text-ice-500 hover:text-ice-600 my-2"
					>
						Server plugins
					</Link>
					<br />
					<a
						href="https://discord.gg/Hs6KSuw3jr"
						className="text-ice-500 hover:text-ice-600 my-2"
						target="_blank"
					>
						Community Discord{" "}
					</a>
					<br />
					<a
						className="text-ice-500 hover:text-ice-600 my-2"
						href="https://status.bytefi.sh/status/i"
						target="_blank"
					>
						Server Uptime
					</a>
					<div className="flex flex-col gap-3 mt-4">
						<div className="w-full">
							<div className="flex flex-col lg:flex-row w-full justify-between text-xl gap-1 items-center">
								Getting Connected
								<div className="flex gap-3">
									<div className="flex gap-1 items-baseline text-sm text-red-500">
										<div className="w-2 h-2 rounded-full bg-red-500"></div>{" "}
										Unsupported
									</div>
									<div className="flex gap-1 items-baseline text-sm text-orange-500">
										<div className="w-2 h-2 rounded-full bg-orange-500"></div>{" "}
										Unstable
									</div>
									<div className="flex gap-1 items-baseline text-sm text-green-500">
										<div className="w-2 h-2 rounded-full bg-green-500"></div>{" "}
										Supported
									</div>
								</div>
							</div>
							<div className="text-lg w-full text-center mt-5">
								Java Edition
							</div>
							<div className="flex flex-wrap justify-center gap-1.5 px-2">
								<span className="text-red-500">1.5.x</span>
								<span className="text-red-500">1.6.x</span>
								<span className="text-red-500">1.7.x</span>
								<span className="text-red-500">1.8.x</span>
								<span className="text-orange-500">1.9.x</span>
								<span className="text-orange-500">1.10.x</span>
								<span className="text-orange-500">1.11.x</span>
								<span className="text-green-500">1.12.x</span>
								<span className="text-green-500">1.13.x</span>
								<span className="text-green-500">1.14.x</span>
								<span className="text-green-500">1.15.x</span>
								<span className="text-green-500">1.16.x</span>
								<span className="text-green-500">1.17.x</span>
								<span className="text-green-500">1.18.x</span>
								<span className="text-green-500">1.19.x</span>
								<span className="text-green-500">1.20.x</span>
								<span className="text-green-500">10.21.x</span>
							</div>
							<div className="flex justify-center w-full mt-2">
								<ServerButton ip={"java.bytefi.sh"} />
							</div>
							<div className="text-lg w-full text-center mt-5">
								Bedrock Edition
							</div>
							<div className="w-full text-center text-green-500">
								Latest Supported
							</div>
							<div className="flex justify-center w-full mt-2">
								<ServerButton ip={"bedrock.bytefi.sh"} />
							</div>
						</div>
					</div>
					<div className="flex w-full justify-between gap-1 items-center mt-4">
						<a className="text-lg">Current Playerlist</a>
						{health.status == "up" ? (
							<div className="flex items-center gap-2">
								<div className="w-2.5 h-2.5 bg-green-500 rounded-full"></div>{" "}
								Online
							</div>
						) : (
							<div className="flex items-center gap-2">
								<div className="w-2.5 h-2.5 bg-red-500 rounded-full"></div> Down
							</div>
						)}
					</div>
					<div className="flex w-full justify-center flex-wrap mt-2 gap-1">
						{stats.onlinePlayers.length > 0 ? (
							stats.onlinePlayers.map((player) => {
								return (
									<div
										key={player.name}
										className={`flex items-center gap-1 w-fit pr-1 text-center justify-center border-2 ${player.isOperator ? "border-red-500 border-dashed" : "border-neutral-300 dark:border-d-sub"}`}
									>
										<img
											src={`https://mc-heads.net/avatar/${player.name}`}
											className="w-8 h-8"
										/>
										{player.name}
									</div>
								);
							})
						) : (
							<div className="w-full text-center">No Players Online</div>
						)}
					</div>
				</div>
			</motion.div>
		</div>
	);
}
