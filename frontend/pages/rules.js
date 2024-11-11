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
							<a className="text-xl"> Server Rules </a>
							<div className="grid grid-cols-1 lg:ml-3">
								<a className="text-neutral-900 dark:text-neutral-300">
									1. Do not steal, destroy or attack people/pets unprovoked.{" "}
								</a>
								<a className="text-neutral-900 dark:text-neutral-300">
									2. Do not use any hacks or client modifications that would
									give you an unfair advantage.
								</a>
								<a className="text-neutral-900 dark:text-neutral-300">
									{" "}
									3. No vulgar or obscene language in the chat.{" "}
								</a>
							</div>
						</div>
						<div>
							<a className="text-xl"> Punishments </a>
							<div className="grid grid-cols-1 lg:ml-3">
								<a className="text-neutral-900 dark:text-neutral-300">
									1. First warning.{" "}
								</a>
								<a className="text-neutral-900 dark:text-neutral-300">
									2. Kick from the server.
								</a>
								<a className="text-neutral-900 dark:text-neutral-300">
									3. 10 minute Ban from the server.
								</a>
								<a className="text-neutral-900 dark:text-neutral-300">
									4. 30 minute Ban from the server.
								</a>
								<a className="text-neutral-900 dark:text-neutral-300">
									5. 1 day Ban from the server.
								</a>
								<a className="text-neutral-900 dark:text-neutral-300">
									6. Permanent Ban from the server.
								</a>
							</div>
						</div>
						<WarningsTable />
					</div>
				</div>
			</motion.div>
		</div>
	);
}
