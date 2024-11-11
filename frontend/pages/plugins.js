import Meta from "@/Components/Meta";
import { motion } from "framer-motion";
import axios from "axios";
import { useState } from "react";
import Link from "next/link";
import { useDispatch, useSelector } from "react-redux";
import ByteFish from "@/Components/ByteFish";
import { setTheme } from "@/State/slice";
import Icon from "@mdi/react";
import { mdiChevronDoubleLeft } from "@mdi/js";
import WarningsTable from "@/Components/Table";

const plugins = [
	{
		id: 12,
		name: (
			<a
				href="https://geysermc.org/download/"
				className="text-ice-500 hover:text-ice-600"
				target="_blank"
			>
				Geyser
			</a>
		),
		description: "Bedrock & Java compatibility",
		requirements: "None",
		status: "server",
	},
	{
		id: 13,
		name: (
			<a
				href="https://hangar.papermc.io/ViaVersion/ViaBackwards"
				className="text-ice-500 hover:text-ice-600"
				target="_blank"
			>
				viabackwards
			</a>
		),
		description: "Older client compatibility",
		requirements: "None",
		status: "server",
	},
	{
		id: 4,
		name: (
			<a
				href="https://mc.bytefi.sh/"
				className="text-ice-500 hover:text-ice-600"
			>
				Chest Shops
			</a>
		),
		description: "Custom barrel shops, do /shophelp in-game.",
		requirements: "None",
		status: "enabled",
	},
	{
		id: 123,
		name: (
			<a
				href="https://mc.bytefi.sh"
				className="text-ice-500 hover:text-ice-600"
			>
				Locations
			</a>
		),
		description:
			"Custom locations displayed on the online map, do /locationhelp in-game.",
		requirements: "None",
		status: "enabled",
	},
	{
		id: 5,
		name: (
			<a
				href="https://mc.bytefi.sh/rules"
				className="text-ice-500 hover:text-ice-600"
			>
				Warnings
			</a>
		),
		description: "Custom warning management, do /warn in-game.",
		requirements: "Operator Permissons",
		status: "enabled",
	},
	{
		id: 41,
		name: (
			<a
				href="https://mc.bytefi.sh/"
				className="text-ice-500 hover:text-ice-600"
			>
				MultiSleep
			</a>
		),
		description: "Only 50% of people online have to sleep to skip the night.",
		requirements: "None",
		status: "enabled",
	},
	{
		id: 1,
		name: <a className="text-ice-500 hover:text-ice-600">Voice Chat</a>,
		description: "An optional proximity voice chat mod.",
		requirements: (
			<div>
				Download and install via any mod launcher{" "}
				<a
					href="https://modrinth.com/plugin/simple-voice-chat/versions?l=forge"
					className="text-ice-500 hover:text-ice-600"
					target="_blank"
				>
					here
				</a>
				.
			</div>
		),
		status: "disabled",
	},
];

export default function Minecraft() {
	const state = useSelector((state) => state.state);
	const dispatch = useDispatch();
	return (
		<div>
			<div className="flex w-full justify-center lg:p-5 py-5 px-2 font-grotesk">
				<div className="flex w-full lg:max-w-4xl justify-between items-center">
					<div className="flex justify-start w-full items-center gap-2 text-black dark:text-neutral-100 font-medium">
						<Link href="/mc">
							<div className="cursor-pointer w-10 h-10">
								<ByteFish
									stroke={state.theme == "dark" ? "#ffffff" : "#000000"}
								/>
							</div>
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
					<Link href="/">
						<div className="text-ice-500 hover:text-ice-600 flex items-center cursor-pointer">
							<Icon path={mdiChevronDoubleLeft} size={0.8} />
							Go Back
						</div>
					</Link>
					<div className="flex flex-col gap-3 mt-2">
						<div>
							<a className="text-xl"> Current Plugins </a>
							<p>
								Currently, the server uses and supports the below plugins. Most
								plugins work automatically however each entry has detailed
								intstructions on how to set it up if necessary.
							</p>
							<div className="overflow-x-auto mt-3">
								<table className="w-full border-collapse">
									<thead>
										<tr className="bg-l-fg dark:bg-neutral-900">
											<th className="text-left p-3 text-sm font-medium text-neutral-900 dark:text-neutral-300">
												Plugin Name
											</th>
											<th className="text-left p-3 text-sm font-medium text-neutral-900 dark:text-neutral-300">
												Description
											</th>
											<th className="text-left p-3 text-sm font-medium text-neutral-900 dark:text-neutral-300  hidden lg:table-cell">
												Requirements
											</th>
											<th className="text-left p-3 text-sm font-medium text-neutral-900 dark:text-neutral-300">
												Status
											</th>
										</tr>
									</thead>
									<tbody>
										{plugins.map((plugin) => (
											<tr
												key={plugin.id}
												className="border-b border-gray-200 dark:border-neutral-800 hover:bg-neutral-50 dark:hover:bg-d-fg"
											>
												<td className="p-3 text-sm text-neutral-900 dark:text-neutral-300 flex items-center gap-2">
													{plugin.name}
												</td>
												<td className="p-3 text-sm text-neutral-900 dark:text-neutral-300">
													{plugin.description}
												</td>
												<td className="p-3 text-sm text-neutral-900 dark:text-neutral-300 hidden lg:table-cell">
													{plugin.requirements}
												</td>
												<td className="p-3 text-sm text-neutral-900 dark:text-neutral-300">
													{plugin.status === "enabled" ? (
														<div className="flex gap-1 items-center">
															<div className="w-2 h-2 bg-green-500 rounded-full" />
															Enabled
														</div>
													) : plugin.status === "optional" ? (
														<div className="flex gap-1 items-center">
															<div className="w-2 h-2 bg-purple-300 rounded-full" />
															Optional
														</div>
													) : plugin.status === "disabled" ? (
														<div className="flex gap-1 items-center">
															<div className="w-2 h-2 bg-red-500 rounded-full" />
															Disabled
														</div>
													) : plugin.status === "server" ? (
														<div className="flex gap-1 items-center">
															<div className="w-2 h-2 bg-neutral-500 rounded-full" />
															Server
														</div>
													) : (
														<div className="flex gap-1 items-center">
															<div className="w-2 h-2 bg-orange-400 rounded-full" />
															Developing
														</div>
													)}
												</td>
											</tr>
										))}
									</tbody>
								</table>
							</div>
						</div>
					</div>
				</div>
			</motion.div>
		</div>
	);
}
