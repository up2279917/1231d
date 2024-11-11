import { useSelector, useDispatch } from "react-redux";
import { useEffect } from "react";
import { setTheme } from "@/State/slice";

export default function ThemeWrapper({ children }) {
	const dispatch = useDispatch();
	const theme = useSelector((state) => state.state.theme);

	useEffect(() => {
		// On mount, check for stored theme and update Redux
		if (typeof window !== "undefined") {
			const storedTheme = window.__INITIAL_THEME__;
			if (storedTheme && storedTheme !== theme) {
				dispatch(setTheme(storedTheme));
			}
		}
	}, []);

	useEffect(() => {
		// Apply theme to body background
		if (typeof window !== "undefined") {
			document.body.style.backgroundColor =
				theme === "dark" ? "#0A0A0A" : "#f5f5f7";
		}
	}, [theme]);

	return (
		<div className={theme}>
			<div className="bg-l-bg dark:bg-d-bg min-h-screen min-w-full">
				{children}
			</div>
		</div>
	);
}
