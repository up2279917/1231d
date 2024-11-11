import { initializeTheme } from "@/lib/theme";
import { Html, Head, Main, NextScript } from "next/document";

export default function Document() {
	return (
		<Html lang="en">
			<Head />
			<body className="antialiased">
				<script
					id="theme-initializer"
					dangerouslySetInnerHTML={{
						__html: initializeTheme,
					}}
				/>
				<Main />
				<NextScript />
			</body>
		</Html>
	);
}
