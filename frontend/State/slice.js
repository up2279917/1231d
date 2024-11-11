import { createSlice } from "@reduxjs/toolkit";

const state = createSlice({
	name: "state",
	initialState: {
		theme: "light", // Default to light theme on server
	},
	reducers: {
		setTheme: (state, action) => {
			state.theme = action.payload;
			if (typeof window !== "undefined") {
				localStorage.setItem("theme", action.payload);
				document.documentElement.classList.remove("dark", "light");
				document.documentElement.classList.add(action.payload);
				document.body.classList.remove("dark", "light");
				document.body.classList.add(action.payload);
			}
		},
	},
});

export const { setTheme } = state.actions;
export { state };
