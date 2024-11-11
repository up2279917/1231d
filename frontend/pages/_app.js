import "../styles/globals.css";
import { AnimatePresence } from "framer-motion";
import ThemeWrapper from "@/Components/ThemeWrapper";
import { useRouter } from "next/router";
import { state } from "@/State/slice";
import { configureStore } from "@reduxjs/toolkit";
import { Provider } from "react-redux";

const createStore = () => {
	return configureStore({
		reducer: {
			state: state.reducer,
		},
		middleware: (getDefaultMiddleware) =>
			getDefaultMiddleware({
				serializableCheck: false,
			}),
	});
};

const store = createStore();

function App({ Component, pageProps }) {
	const router = useRouter();

	return (
		<Provider store={store}>
			<ThemeWrapper>
				<AnimatePresence mode="wait">
					<Component {...pageProps} key={router.route} />
				</AnimatePresence>
			</ThemeWrapper>
		</Provider>
	);
}

export default App;
