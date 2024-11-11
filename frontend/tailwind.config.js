/** @type {import('tailwindcss').Config} */
module.exports = {
	content: [
		"./pages/**/*.{js,ts,jsx,tsx,mdx}",
		"./components/**/*.{js,ts,jsx,tsx,mdx}",
		"./Components/**/*.{js,ts,jsx,tsx,mdx}",
		"./Slice/**/*.{js,ts,jsx,tsx,mdx}",
		"./app/**/*.{js,ts,jsx,tsx,mdx}",
	],
	theme: {
		extend: {
			fontFamily: {
				grotesk: ["Grotesk", "Segoe UI", "Noto Sans", "sans-serif"],
				minecraft: ["Minecraft"],
			},
			colors: {
				"l-bg": "#f5f5f7",
				"l-fg": "#ffffff",
				"l-h": "#efefef",

				"d-bg": "#0A0A0A",
				"d-fg": "#111111",
				"d-sub": "#5e5e63",
				"d-h": "#1e1e1e",

				"ice-500": "#6083BF",
				"ice-600": "#3064BF",
				"ice-700": "#1352BF",
			},
		},
	},
	plugins: [],
	darkMode: "class",
};
