import { useSelector } from "react-redux";
import { useEffect } from "react";

const LayoutComponent = ({ children }) => {
	const theme = useSelector((state) => state.state.theme);

	useEffect(() => {
		if (typeof document !== "undefined") {
			document.body.style.backgroundColor =
				theme === "dark" ? "#0A0A0A" : "#f5f5f7";
		}
	}, [theme]);

	return children;
};

export default LayoutComponent;
