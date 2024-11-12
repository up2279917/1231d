import { useState, useEffect, useCallback, useRef } from "react";

export const useWebSocketData = () => {
	const [data, setData] = useState({ players: [], locations: [] });
	const [status, setStatus] = useState("connecting");
	const [error, setError] = useState(null);
	const wsRef = useRef(null);
	const reconnectTimeoutRef = useRef(null);
	const reconnectAttemptsRef = useRef(0);
	const MAX_RECONNECT_ATTEMPTS = 5;

	const connect = useCallback(() => {
		try {
			if (wsRef.current?.readyState === WebSocket.OPEN) {
				wsRef.current.close();
			}

			if (reconnectAttemptsRef.current >= MAX_RECONNECT_ATTEMPTS) {
				setStatus("fallback");
				fallbackToHTTP();
				return;
			}

			const ws = new WebSocket("wss://mcsrv.bytefi.sh/ws");
			wsRef.current = ws;

			ws.onopen = () => {
				setStatus("connected");
				setError(null);
				reconnectAttemptsRef.current = 0;
				if (reconnectTimeoutRef.current) {
					clearTimeout(reconnectTimeoutRef.current);
					reconnectTimeoutRef.current = null;
				}
			};

			ws.onmessage = (event) => {
				try {
					const parsedData = JSON.parse(event.data);
					setData({
						players: parsedData.players || [],
						locations: parsedData.locations || [],
						timestamp: parsedData.timestamp,
					});
				} catch (err) {
					console.error("Failed to parse WebSocket message:", err);
					setError("Invalid data received");
				}
			};

			ws.onclose = (event) => {
				setStatus("disconnected");
				reconnectAttemptsRef.current++;

				if (reconnectAttemptsRef.current < MAX_RECONNECT_ATTEMPTS) {
					const delay = Math.min(
						1000 * Math.pow(2, reconnectAttemptsRef.current),
						30000,
					);
					reconnectTimeoutRef.current = setTimeout(() => {
						setStatus("reconnecting");
						connect();
					}, delay);
				} else {
					fallbackToHTTP();
				}
			};

			ws.onerror = (event) => {
				setError("Connection error");
				setStatus("error");
			};
		} catch (err) {
			setError("Failed to connect");
			setStatus("error");
			reconnectAttemptsRef.current++;

			if (reconnectAttemptsRef.current >= MAX_RECONNECT_ATTEMPTS) {
				fallbackToHTTP();
			}
		}
	}, []);

	const fallbackToHTTP = async () => {
		console.log("Falling back to HTTP polling");
		setStatus("fallback");

		// Set up HTTP polling
		const pollData = async () => {
			try {
				const [playersRes, locationsRes] = await Promise.all([
					fetch("https://mcsrv.bytefi.sh/api/players/locations"),
					fetch("https://mcsrv.bytefi.sh/api/locations"),
				]);

				if (!playersRes?.ok || !locationsRes?.ok) {
					throw new Error("Failed to fetch data");
					return;
				}

				const players = await playersRes.json();
				const locations = await locationsRes.json();
				setData({
					players,
					locations,
					timestamp: Date.now(),
				});
			} catch (err) {
				console.error("HTTP fallback error:", err);
				setError("Failed to fetch data");
			}
		};

		await pollData();

		const interval = setInterval(pollData, 15000);

		return () => clearInterval(interval);
	};

	useEffect(() => {
		connect();

		return () => {
			if (wsRef.current) {
				wsRef.current.close();
			}
			if (reconnectTimeoutRef.current) {
				clearTimeout(reconnectTimeoutRef.current);
			}
		};
	}, [connect]);

	return {
		data,
		status,
		error,
		isConnected: status === "connected" || status === "fallback",
	};
};
