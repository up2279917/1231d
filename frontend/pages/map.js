import React, {
	useState,
	useEffect,
	useCallback,
	useRef,
	useMemo,
} from "react";
import { motion } from "framer-motion";
import Link from "next/link";
import Icon from "@mdi/react";
import {
	mdiLoading,
	mdiAccount,
	mdiChevronDoubleLeft,
	mdiMapLegend,
	mdiCrosshairs,
} from "@mdi/js";
import Meta from "@/Components/Meta";
import ByteFish from "@/Components/ByteFish";
import { useDispatch, useSelector } from "react-redux";
import { setTheme } from "@/State/slice";
import { FixedSizeList as List } from "react-window";
import DimensionSwitcher from "@/Components/DimensionSwitcher";
import BiomeLegend from "@/Components/BiomeLegend";
import MapCanvas from "@/Components/MapCanvas";
import LoadingScreen from "@/Components/LoadingScreen";

const FETCH_INTERVAL = 15000;
const INITIAL_VIEWPORT = {
	width: 600,
	height: 600,
};
const DEFAULT_TRANSFORM = {
	x: INITIAL_VIEWPORT.width / 2,
	z: INITIAL_VIEWPORT.height / 2,
	scale: 0.7,
};
const CHUNK_SIZE = 100;
const PLAYER_ICON_SIZE = 20;
const BIOME_TILE_SIZE = 16;
const MIN_SCALE = 0.3;
const MAX_SCALE = 5.0;
const MAP_PADDING = 200;
const CACHE_VERSION = "1.0";
const CACHE_DURATION = 30 * 24 * 60 * 60 * 1000; // 30 days in milliseconds
const DB_NAME = "biomeCache";
const STORE_NAME = "biomes";

let dbConnection = null;

const isValidPlayer = (player) => {
	return (
		player &&
		typeof player === "object" &&
		typeof player.name === "string" &&
		typeof player.x === "number" &&
		typeof player.z === "number"
	);
};

const PlayerRow = React.memo(({ index, style, data }) => {
	const { players, onPlayerClick, selectedPlayer } = data;
	const player = players[index];

	return (
		<div
			style={style}
			className={`p-3 flex items-center gap-2 cursor-pointer hover:bg-gray-50 dark:hover:bg-neutral-800 transition-colors ${
				selectedPlayer?.name === player.name
					? "bg-gray-100 dark:bg-neutral-800"
					: ""
			}`}
			onClick={() => onPlayerClick(player)}
		>
			<img
				src={`https://mc-heads.net/avatar/${player.name}`}
				alt={player.name}
				className="w-6 h-6 rounded-sm"
			/>
			<span className="text-sm text-gray-900 dark:text-gray-100">
				{player.name}
			</span>
			<Icon
				path={mdiCrosshairs}
				size={0.7}
				className="ml-auto text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
			/>
		</div>
	);
});

const PlayerList = React.memo(
	({ players = [], onPlayerClick, selectedPlayer }) => {
		const validPlayers = useMemo(
			() => (Array.isArray(players) ? players.filter(isValidPlayer) : []),
			[players],
		);

		const isMobile = window.innerWidth < 768;

		if (validPlayers.length === 0) {
			return (
				<div className="w-full md:w-1/3 h-auto md:h-[600px] bg-white dark:bg-neutral-900 border-t md:border-t-0 md:border-l border-gray-200 dark:border-neutral-700">
					<div className="p-3 border-b border-gray-200 dark:border-neutral-700">
						<h3 className="font-medium text-gray-900 dark:text-gray-100">
							Online Players
						</h3>
					</div>
					<div className="p-4 text-gray-500 dark:text-gray-400 text-center">
						No players in dimension
					</div>
				</div>
			);
		}

		return (
			<div className="w-full md:w-1/3 h-auto md:h-[600px] bg-white dark:bg-neutral-900 border-t md:border-t-0 md:border-l border-gray-200 dark:border-neutral-700 flex flex-col">
				<div className="p-3 border-b border-gray-200 dark:border-neutral-700">
					<h3 className="font-medium text-gray-900 dark:text-gray-100">
						Online Players
					</h3>
				</div>
				<div
					className="flex-1 overflow-hidden"
					style={{
						maxHeight: isMobile ? Math.min(players.length * 50, 300) : 552,
					}}
				>
					<List
						height={Math.min(validPlayers.length * 50, isMobile ? 300 : 552)}
						itemCount={validPlayers.length}
						itemSize={50}
						width="100%"
						itemData={{ players: validPlayers, onPlayerClick, selectedPlayer }}
					>
						{PlayerRow}
					</List>
				</div>
			</div>
		);
	},
);

const getDB = async () => {
	if (dbConnection) {
		return dbConnection;
	}

	return new Promise((resolve, reject) => {
		const request = indexedDB.open(DB_NAME, 1);

		request.onerror = () => {
			console.error("IndexedDB open error:", request.error);
			reject(request.error);
		};

		request.onsuccess = () => {
			dbConnection = request.result;

			// Handle connection closing
			dbConnection.onclose = () => {
				dbConnection = null;
			};

			// Handle version change
			dbConnection.onversionchange = () => {
				dbConnection.close();
				dbConnection = null;
			};

			resolve(dbConnection);
		};

		request.onupgradeneeded = (event) => {
			const db = event.target.result;
			if (!db.objectStoreNames.contains(STORE_NAME)) {
				db.createObjectStore(STORE_NAME);
			}
		};
	});
};

const withRetry = async (operation, maxRetries = 3) => {
	let lastError;

	for (let i = 0; i < maxRetries; i++) {
		try {
			return await operation();
		} catch (error) {
			console.warn(
				`Operation failed, attempt ${i + 1} of ${maxRetries}:`,
				error,
			);
			lastError = error;

			// Reset connection if it was a connection error
			if (
				error.name === "InvalidStateError" ||
				error.name === "TransactionInactiveError"
			) {
				dbConnection = null;
			}

			// Wait before retrying
			await new Promise((resolve) => setTimeout(resolve, 100 * Math.pow(2, i)));
		}
	}

	throw lastError;
};

const initDB = () => {
	return new Promise((resolve, reject) => {
		const request = indexedDB.open(DB_NAME, 1);

		request.onerror = () => reject(request.error);
		request.onsuccess = () => resolve(request.result);

		request.onupgradeneeded = (event) => {
			const db = event.target.result;
			if (!db.objectStoreNames.contains(STORE_NAME)) {
				db.createObjectStore(STORE_NAME);
			}
		};
	});
};

const getFromCache = async (key) => {
	return withRetry(async () => {
		const db = await getDB();
		return new Promise((resolve, reject) => {
			const transaction = db.transaction(STORE_NAME, "readonly");
			const store = transaction.objectStore(STORE_NAME);
			const request = store.get(key);

			transaction.oncomplete = () => {
				resolve(request.result);
			};

			transaction.onerror = () => {
				reject(transaction.error);
			};
		});
	});
};

const saveToCache = async (key, value) => {
	return withRetry(async () => {
		const db = await getDB();
		return new Promise((resolve, reject) => {
			const transaction = db.transaction(STORE_NAME, "readwrite");
			const store = transaction.objectStore(STORE_NAME);
			const request = store.put(value, key);

			transaction.oncomplete = () => {
				resolve(request.result);
			};

			transaction.onerror = () => {
				reject(transaction.error);
			};
		});
	});
};

const MinecraftMap = () => {
	const [players, setPlayers] = useState([]);
	const [loading, setLoading] = useState(true); // Start with loading true
	const [loadingBiomes, setLoadingBiomes] = useState(true); // Add biomes loading state
	const [locations, setLocations] = useState([]);
	const [error, setError] = useState(null);
	const [transform, setTransform] = useState({
		x: INITIAL_VIEWPORT.width / 2,
		z: INITIAL_VIEWPORT.height / 2,
		scale: 1,
	});
	const [viewportDimensions, setViewportDimensions] =
		useState(INITIAL_VIEWPORT);
	const [hoveredEntity, setHoveredEntity] = useState(null);
	const [selectedPlayer, setSelectedPlayer] = useState(null);
	const [mounted, setMounted] = useState(false);
	const [visibleBiomes, setVisibleBiomes] = useState([]);
	const [allBiomes, setAllBiomes] = useState([]);
	const [isInitialized, setIsInitialized] = useState(false);
	const [isMobile, setIsMobile] = useState(false);
	const [biomeHoverEnabled, setBiomeHoverEnabled] = useState(false);
	const [currentDimension, setCurrentDimension] = useState("world");
	const [isLegendExpanded, setIsLegendExpanded] = useState(false);
	const [dimensionStates, setDimensionStates] = useState({
		world: {
			transform: { ...DEFAULT_TRANSFORM },
			biomes: [],
		},
		world_nether: {
			transform: { ...DEFAULT_TRANSFORM },
			biomes: [],
		},
		world_the_end: {
			transform: { ...DEFAULT_TRANSFORM },
			biomes: [],
		},
	});

	const initializeTimeout = useRef(null);
	const mapRef = useRef(null);
	const isDragging = useRef(false);
	const lastTouch = useRef(null);
	const isPinching = useRef(false);
	const initialDistance = useRef(null);
	const hasInitialized = useRef(false);
	const hasZoomed = useRef(false);
	const biomeLegendRef = useRef(null);

	const state = useSelector((state) => state.state);
	const dispatch = useDispatch();

	useEffect(() => {
		setMounted(true);
	}, []);

	const fetchBiomes = useCallback(async () => {
		try {
			setLoadingBiomes(true);

			const fetchAndCacheData = async (url, cacheKey) => {
				try {
					// Check cache first
					const cachedData = await getFromCache(cacheKey);
					if (cachedData) {
						const { data, timestamp, version } = cachedData;
						const isExpired = Date.now() - timestamp > CACHE_DURATION;

						// Return cached data if it's not expired and version matches
						if (!isExpired && version === CACHE_VERSION) {
							return data;
						}
					}
				} catch (cacheError) {
					console.warn("Cache read failed:", cacheError);
					// Continue to fetch if cache read fails
				}

				// Fetch fresh data
				const response = await fetch(`/biome-data/biome_data_${url}.json.gz`);
				if (!response.ok)
					throw new Error(`Failed to fetch biome data from ${url}`);
				const data = await response.json();

				// Try to cache the fresh data
				try {
					const cacheObject = {
						data,
						timestamp: Date.now(),
						version: CACHE_VERSION,
					};
					await saveToCache(cacheKey, cacheObject);
				} catch (cacheError) {
					console.warn("Cache write failed:", cacheError);
					// Continue even if caching fails
				}

				return data;
			};

			// Fetch all biome data
			const [owData, ntData, endData] = await Promise.all([
				fetchAndCacheData("ow", "biome_cache_ow"),
				fetchAndCacheData("nt", "biome_cache_nt"),
				fetchAndCacheData("end", "biome_cache_end"),
			]);

			setDimensionStates((prev) => ({
				...prev,
				world: { ...prev.world, biomes: owData },
				world_nether: { ...prev.world_nether, biomes: ntData },
				world_the_end: { ...prev.world_the_end, biomes: endData },
			}));
		} catch (err) {
			console.error("Error fetching biomes:", err);
			setError(err.message);

			// Try to load from cache on error
			try {
				const [owCache, ntCache, endCache] = await Promise.all([
					getFromCache("biome_cache_ow").then((data) => data?.data ?? []),
					getFromCache("biome_cache_nt").then((data) => data?.data ?? []),
					getFromCache("biome_cache_end").then((data) => data?.data ?? []),
				]);

				setDimensionStates((prev) => ({
					...prev,
					world: { ...prev.world, biomes: owCache },
					world_nether: { ...prev.world_nether, biomes: ntCache },
					world_the_end: { ...prev.world_the_end, biomes: endCache },
				}));
			} catch (cacheErr) {
				console.error("Error loading from cache:", cacheErr);
			}
		} finally {
			setLoadingBiomes(false);
		}
	}, []);

	const dimensionPlayers = useMemo(
		() => players.filter((player) => player.world === currentDimension),
		[players, currentDimension],
	);

	const dimensionLocations = useMemo(
		() => locations.filter((location) => location.world === currentDimension),
		[locations, currentDimension],
	);

	const handleDimensionChange = useCallback(
		(newDimension) => {
			// Save current transform state
			setDimensionStates((prev) => ({
				...prev,
				[currentDimension]: {
					...prev[currentDimension],
					transform: transform,
				},
			}));

			// Set new dimension and its transform
			setCurrentDimension(newDimension);
			setTransform(dimensionStates[newDimension].transform);
		},
		[currentDimension, transform, dimensionStates],
	);

	const updateVisibleBiomes = useCallback(() => {
		const currentBiomes = dimensionStates[currentDimension].biomes;
		const margin = 100;
		const viewportBounds = {
			minX:
				transform.x - viewportDimensions.width / transform.scale / 2 - margin,
			maxX:
				transform.x + viewportDimensions.width / transform.scale / 2 + margin,
			minZ:
				transform.z - viewportDimensions.height / transform.scale / 2 - margin,
			maxZ:
				transform.z + viewportDimensions.height / transform.scale / 2 + margin,
		};

		setVisibleBiomes(
			currentBiomes.filter(
				(biome) =>
					biome.x >= viewportBounds.minX &&
					biome.x <= viewportBounds.maxX &&
					biome.z >= viewportBounds.minZ &&
					biome.z <= viewportBounds.maxZ,
			),
		);
	}, [currentDimension, dimensionStates, transform, viewportDimensions]);

	useEffect(() => {
		if (!mounted) return;
		fetchBiomes();
	}, [mounted]);

	useEffect(() => {
		updateVisibleBiomes();
	}, [transform, updateVisibleBiomes]);

	const handleMouseDown = useCallback(
		(e) => {
			e.preventDefault();
			if (e.button === 0) {
				isDragging.current = true;
				lastTouch.current = { x: e.clientX, y: e.clientY };
			}
		},
		[viewportDimensions],
	);

	const handleMouseMove = useCallback(
		(e) => {
			if (!isDragging.current) return;

			const dx = (e.clientX - lastTouch.current.x) / transform.scale;
			const dy = (e.clientY - lastTouch.current.y) / transform.scale;

			setTransform((prev) => ({
				...prev,
				x: prev.x - dx,
				z: prev.z - dy,
			}));

			lastTouch.current = { x: e.clientX, y: e.clientY };
		},
		[transform],
	);
	const handleMouseUp = useCallback(() => {
		isDragging.current = false;
		lastTouch.current = null;
	}, []);

	const handleTouchStart = useCallback(
		(e) => {
			// Don't handle touch events if the legend is expanded
			if (isLegendExpanded) return;

			if (e.touches.length === 2) {
				e.preventDefault();
				isDragging.current = false;
				isPinching.current = true;
				const dx = e.touches[0].clientX - e.touches[1].clientX;
				const dy = e.touches[0].clientY - e.touches[1].clientY;
				initialDistance.current = Math.sqrt(dx * dx + dy * dy);
				lastTouch.current = {
					x: (e.touches[0].clientX + e.touches[1].clientX) / 2,
					y: (e.touches[0].clientY + e.touches[1].clientY) / 2,
				};
			} else if (e.touches.length === 1) {
				isDragging.current = true;
				isPinching.current = false;
				lastTouch.current = {
					x: e.touches[0].clientX,
					y: e.touches[0].clientY,
				};
			}
		},
		[isLegendExpanded],
	);

	const handleTouchMove = useCallback(
		(e) => {
			if (isLegendExpanded) return;
			e.preventDefault();

			if (isDragging.current && e.touches.length === 1) {
				const dx =
					(e.touches[0].clientX - lastTouch.current.x) / transform.scale;
				const dy =
					(e.touches[0].clientY - lastTouch.current.y) / transform.scale;

				setTransform((prev) => ({
					...prev,
					x: prev.x - dx,
					z: prev.z - dy,
				}));

				lastTouch.current = {
					x: e.touches[0].clientX,
					y: e.touches[0].clientY,
				};
			} else if (isPinching.current && e.touches.length === 2) {
				const dx = e.touches[0].clientX - e.touches[1].clientX;
				const dy = e.touches[0].clientY - e.touches[1].clientY;
				const distance = Math.sqrt(dx * dx + dy * dy);
				const scale = distance / initialDistance.current;

				const centerX = (e.touches[0].clientX + e.touches[1].clientX) / 2;
				const centerY = (e.touches[0].clientY + e.touches[1].clientY) / 2;

				setTransform((prev) => {
					const newScale = prev.scale * scale;

					if (newScale < MIN_SCALE || newScale > MAX_SCALE) {
						return prev;
					}

					return {
						scale: newScale,
						x: prev.x + (centerX - lastTouch.current.x) / prev.scale,
						z: prev.z + (centerY - lastTouch.current.y) / prev.scale,
					};
				});

				lastTouch.current = { x: centerX, y: centerY };
				initialDistance.current = distance;
			}
		},
		[transform.scale, isLegendExpanded],
	);

	const handleTouchEnd = useCallback(() => {
		// Don't handle touch events if the legend is expanded
		if (isLegendExpanded) return;

		isDragging.current = false;
		isPinching.current = false;
	}, [isLegendExpanded]);

	useEffect(() => {
		const mapElement = mapRef.current;
		if (!mapElement) return;

		const mouseDownHandler = (e) => handleMouseDown(e);
		const mouseMoveHandler = (e) => handleMouseMove(e);
		const mouseUpHandler = (e) => handleMouseUp(e);
		const touchStartHandler = (e) => handleTouchStart(e);
		const touchMoveHandler = (e) => handleTouchMove(e);
		const touchEndHandler = (e) => handleTouchEnd(e);

		mapElement.addEventListener("mousedown", mouseDownHandler);
		window.addEventListener("mousemove", mouseMoveHandler);
		window.addEventListener("mouseup", mouseUpHandler);
		mapElement.addEventListener("touchstart", touchStartHandler);
		mapElement.addEventListener("touchmove", touchMoveHandler);
		mapElement.addEventListener("touchend", touchEndHandler);

		return () => {
			mapElement.removeEventListener("mousedown", mouseDownHandler);
			window.removeEventListener("mousemove", mouseMoveHandler);
			window.removeEventListener("mouseup", mouseUpHandler);
			mapElement.removeEventListener("touchstart", touchStartHandler);
			mapElement.removeEventListener("touchmove", touchMoveHandler);
			mapElement.removeEventListener("touchend", touchEndHandler);
		};
	}, [
		handleMouseDown,
		handleMouseMove,
		handleMouseUp,
		handleTouchStart,
		handleTouchMove,
		handleTouchEnd,
	]);

	const worldToCanvas = useCallback(
		(worldX, worldZ) => ({
			x:
				viewportDimensions.width / 2 + (worldX - transform.x) * transform.scale,
			y:
				viewportDimensions.height / 2 +
				(worldZ - transform.z) * transform.scale,
		}),
		[transform, viewportDimensions],
	);

	const fitPlayersInView = useCallback((playerData, dimensions) => {
		if (!playerData?.length) return { x: 0, z: 0, scale: DEFAULT_SCALE };

		const validPlayers = Array.isArray(playerData)
			? playerData.filter(isValidPlayer)
			: [];
		if (!validPlayers.length) return { x: 0, z: 0, scale: DEFAULT_SCALE };

		const minX = Math.min(...validPlayers.map((p) => p.x));
		const maxX = Math.max(...validPlayers.map((p) => p.x));
		const minZ = Math.min(...validPlayers.map((p) => p.z));
		const maxZ = Math.max(...validPlayers.map((p) => p.z));

		const centerX = (minX + maxX) / 2;
		const centerZ = (minZ + maxZ) / 2;

		const contentWidth = Math.max(maxX - minX + MAP_PADDING * 2, 1000);
		const contentHeight = Math.max(maxZ - minZ + MAP_PADDING * 2, 1000);
		const scaleX = dimensions.width / contentWidth;
		const scaleZ = dimensions.height / contentHeight;

		const scale = Math.min(
			Math.max(Math.min(scaleX, scaleZ) * 0.8, MIN_SCALE),
			MAX_SCALE,
		);

		return { x: centerX, z: centerZ, scale };
	}, []);

	const focusOnPlayer = useCallback(
		(player) => {
			if (!player || !isValidPlayer(player)) return;

			setSelectedPlayer(player);
			setTransform((prev) => ({
				...prev,
				x: player.x,
				z: player.z,
				scale: Math.min(2, MAX_SCALE), // Apply desired scale without interfering with initial setup
			}));
		},
		[setTransform],
	);

	const updateDimensions = useCallback(() => {
		const container = document.getElementById("map-container");
		if (!container) return;

		// Get container dimensions first
		const containerRect = container.getBoundingClientRect();
		const isMobile = window.innerWidth < 768;

		// Calculate dimensions
		const width = isMobile
			? Math.min(containerRect.width, window.innerWidth - 32) // Account for mobile padding
			: Math.floor((containerRect.width * 2) / 3); // 2/3 width on desktop

		// Calculate height considering player list
		const headerHeight = 150;
		const mobilePlayerListHeight = isMobile
			? Math.min(300, (players?.length || 0) * 50)
			: 0;
		const height = 600; // Keep height consistent

		setViewportDimensions((prev) => {
			if (prev.width !== width || prev.height !== height) {
				return { width, height };
			}
			return prev;
		});
	}, [players]);

	// Modify the initialization effect to ensure proper initial sizing
	useEffect(() => {
		if (!mounted) return;

		const initializeSize = () => {
			const container = document.getElementById("map-container");
			if (!container) return;

			// Force immediate calculation
			requestAnimationFrame(() => {
				updateDimensions();
				// Double check after a brief delay to catch any layout shifts
				setTimeout(updateDimensions, 50);
			});
		};

		// Run initial setup
		initializeSize();

		// Handle resize
		const handleResize = () => requestAnimationFrame(updateDimensions);
		window.addEventListener("resize", handleResize);

		// Use ResizeObserver for container changes
		const resizeObserver = new ResizeObserver(() => {
			requestAnimationFrame(updateDimensions);
		});

		const container = document.getElementById("map-container");
		if (container) {
			resizeObserver.observe(container);
		}

		return () => {
			window.removeEventListener("resize", handleResize);
			resizeObserver.disconnect();
		};
	}, [mounted, updateDimensions]);

	// Add this effect to update isMobile state
	useEffect(() => {
		const checkMobile = () => {
			setIsMobile(window.innerWidth < 768);
		};

		checkMobile();
		window.addEventListener("resize", checkMobile);
		return () => window.removeEventListener("resize", checkMobile);
	}, []);

	useEffect(() => {
		if (typeof window === "undefined") return;

		const initializeSize = () => {
			// Force immediate calculation
			const container = document.getElementById("map-container");
			if (!container) return;

			const containerRect = container.getBoundingClientRect();
			// Calculate initial dimensions
			const mobilePadding = isMobile ? 16 : 0;
			const containerPadding = 32;
			const width = isMobile
				? Math.min(
						containerRect.width - mobilePadding - containerPadding,
						window.innerWidth - mobilePadding - containerPadding,
					)
				: containerRect.width - 350;

			const headerHeight = 150;
			const mobilePlayerListHeight = isMobile ? 300 : 0;
			const availableHeight =
				window.innerHeight - headerHeight - mobilePlayerListHeight;
			const height = isMobile
				? Math.min(availableHeight, 464)
				: Math.max(availableHeight, 400);

			// Set initial dimensions immediately
			setViewportDimensions({ width, height });

			// Update again after a short delay to catch any layout shifts
			setTimeout(updateDimensions, 100);
		};

		// Set up ResizeObserver
		const container = document.getElementById("map-container");
		if (!container) return;

		const resizeObserver = new ResizeObserver(() => {
			requestAnimationFrame(updateDimensions);
		});

		// Start observing and initialize
		resizeObserver.observe(container);
		initializeSize();

		// Handle orientation changes explicitly
		const handleOrientationChange = () => {
			setTimeout(updateDimensions, 100);
		};
		window.addEventListener("orientationchange", handleOrientationChange);

		return () => {
			resizeObserver.disconnect();
			window.removeEventListener("orientationchange", handleOrientationChange);
		};
	}, [updateDimensions]);

	useEffect(() => {
		const container = document.getElementById("map-container");
		if (!container) return;

		// Create ResizeObserver
		const resizeObserver = new ResizeObserver(() => {
			requestAnimationFrame(updateDimensions);
		});

		// Start observing
		resizeObserver.observe(container);

		// Initial dimension update
		updateDimensions();

		return () => {
			resizeObserver.disconnect();
		};
	}, [updateDimensions]);

	useEffect(() => {
		if (hasInitialized.current) return;

		const initializeMap = async () => {
			setMounted(true);
			updateDimensions();

			try {
				// Fetch initial data
				await Promise.all([fetchBiomes(), fetchPlayers(), fetchLocations()]);

				// Set initial state after data is loaded
				setTransform((prev) => ({
					...prev,
					x: INITIAL_VIEWPORT.width / 2,
					z: INITIAL_VIEWPORT.height / 2,
					scale: 1, // Ensure scale is set to a reasonable value
				}));

				setIsInitialized(true);
				setLoading(false);
				hasInitialized.current = true;
			} catch (err) {
				console.error("Initialization error:", err);
				setError(err.message);
				setLoading(false);
			}
		};

		initializeMap();

		// Setup resize handler
		const handleResize = () => {
			requestAnimationFrame(updateDimensions);
		};
		window.addEventListener("resize", handleResize);

		return () => {
			window.removeEventListener("resize", handleResize);
		};
	}, []);

	const canvasRef = useRef(null);

	const fetchLocations = async () => {
		try {
			const response = await fetch("https://mcsrv.bytefi.sh/api/locations");
			if (!response.ok) throw new Error("Failed to fetch locations");
			const data = await response.json();
			setLocations(data);
		} catch (err) {
			console.error("Error fetching locations:", err);
		}
	};

	const duplicatePlayers = useCallback((originalPlayers) => {
		// Early return if no players
		if (!originalPlayers.length) return [];

		// Take the first player as template
		const templatePlayer = originalPlayers[0];

		// Create array of 100 duplicated players with unique names
		return Array.from({ length: 100 }, (_, index) => ({
			...templatePlayer,
			// Use original name for first player, then add numbers for others
			name:
				index === 0 ? templatePlayer.name : `${templatePlayer.name}_${index}`,
			// Slightly vary positions to spread players out (optional)
			x: templatePlayer.x + (Math.random() - 0.5) * 100,
			z: templatePlayer.z + (Math.random() - 0.5) * 100,
		}));
	}, []);

	const fetchPlayers = useCallback(async () => {
		try {
			const response = await fetch(
				"https://mcsrv.bytefi.sh/api/players/locations",
			);
			if (!response.ok) throw new Error("Failed to fetch player data");
			const data = await response.json();
			if (!Array.isArray(data)) {
				throw new Error("Invalid data format received");
			}

			const validData = data.filter(isValidPlayer);
			setPlayers(validData);
		} catch (err) {
			console.error("Error fetching players:", err);
			setError(err.message);
			setPlayers([]);
		} finally {
			setLoading(false); // Always set loading to false when done
		}
	}, []);

	useEffect(() => {
		if (!isInitialized) return;

		const playerInterval = setInterval(fetchPlayers, FETCH_INTERVAL);
		const locationInterval = setInterval(fetchLocations, FETCH_INTERVAL);

		return () => {
			clearInterval(playerInterval);
			clearInterval(locationInterval);
		};
	}, [isInitialized]);

	useEffect(() => {
		if (!mounted) return;
		fetchLocations();
		const interval = setInterval(fetchLocations, FETCH_INTERVAL);
		return () => clearInterval(interval);
	}, [mounted]);

	useEffect(() => {
		if (!isInitialized && viewportDimensions.width > 0) {
			const scale = Math.min(
				viewportDimensions.width / 1000,
				viewportDimensions.height / 1000,
			);

			setTransform((prev) => ({
				...prev,
				x: viewportDimensions.width / 2,
				z: viewportDimensions.height / 2,
				scale: Math.max(scale, 0.5), // Ensure minimum scale
			}));

			setIsInitialized(true);
		}
	}, [viewportDimensions, isInitialized]);

	const calculateScale = useCallback(() => {
		const gridSize = 100 * transform.scale;
		return `${Math.round(gridSize)} blocks`;
	}, [transform.scale]);

	const handleWheel = useCallback(
		(e) => {
			const rect = mapRef.current?.getBoundingClientRect();
			const legendRect = biomeLegendRef.current?.getBoundingClientRect();

			if (!rect) return;

			// Allow scrolling if the mouse is not over the BiomeLegend
			if (
				legendRect &&
				e.clientY >= legendRect.top &&
				e.clientY <= legendRect.bottom
			) {
				// If the mouse is over the BiomeLegend, allow scrolling and prevent zoom
				return; // Do nothing, let the scroll happen
			}

			const mouseX = e.clientX - rect.left;
			const mouseY = e.clientY - rect.top;
			const scaleFactor = e.deltaY > 0 ? 0.9 : 1.1;

			setTransform((prev) => {
				const newScale = prev.scale * scaleFactor;

				if (newScale < MIN_SCALE || newScale > MAX_SCALE) return prev;

				return {
					scale: newScale,
					x:
						prev.x +
						(mouseX - viewportDimensions.width / 2) *
							(1 / prev.scale - 1 / (prev.scale * scaleFactor)),
					z:
						prev.z +
						(mouseY - viewportDimensions.height / 2) *
							(1 / prev.scale - 1 / (prev.scale * scaleFactor)),
				};
			});
		},
		[viewportDimensions],
	);

	useEffect(() => {
		const mapElement = mapRef.current;
		if (!mapElement) return;
		mapElement.addEventListener("wheel", handleWheel, { passive: false });
		return () => mapElement.removeEventListener("wheel", handleWheel);
	}, [handleWheel]);

	useEffect(() => {
		const initialDimensions = {
			width: window.innerWidth >= 768 ? 600 : window.innerWidth, // Change 600 to desired width for mobile
			height: window.innerWidth >= 768 ? 600 : window.innerHeight - 150, // Adjust height accordingly
		};
		setViewportDimensions(initialDimensions);
	}, []);

	if (!mounted || loading || loadingBiomes) {
		return <LoadingScreen />;
	}

	if (error) {
		return (
			<div className="text-red-500 dark:text-red-400 p-4 bg-red-100 dark:bg-red-900/30 rounded">
				Error loading map: {error}
			</div>
		);
	}

	return (
		<div>
			<Meta title="Live Map" />
			<div className="flex w-full justify-center lg:p-5 py-5 px-2 font-grotesk">
				<div className="flex w-full lg:max-w-4xl justify-between items-center">
					<div className="flex justify-start w-full items-center gap-2 text-black dark:text-neutral-100 font-medium">
						<Link href="/mc">
							<div className="cursor-pointer w-10 h-10">
								<ByteFish
									stroke={state.theme === "dark" ? "#ffffff" : "#000000"}
								/>
							</div>
						</Link>
					</div>
					<div className="flex gap-1 items-center text-black dark:text-white">
						<button
							className="cursor-pointer px-1.5 text-sm border-b-2 border-black dark:border-0"
							onClick={() => dispatch(setTheme("light"))}
						>
							Light
						</button>
						<button
							className="cursor-pointer px-1.5 text-sm bg-transparent dark:border-b-2 border-neutral-200 border-0"
							onClick={() => dispatch(setTheme("dark"))}
						>
							Dark
						</button>
					</div>
				</div>
			</div>

			<motion.div
				initial={{ opacity: 0 }}
				animate={{ opacity: 1 }}
				exit={{ opacity: 0 }}
				transition={{ delay: 0.2 }}
				className="p-4 flex w-full justify-center h-full"
			>
				<div className="flex flex-col justify-center w-full max-w-4xl">
					<div className="flex items-center w-full justify-between mb-4">
						<Link href="/">
							<div className="text-ice-500 hover:text-ice-600 flex items-center gap-1 cursor-pointer">
								<Icon path={mdiChevronDoubleLeft} size={0.8} />
								<span>Go Back</span>
							</div>
						</Link>
						<div className="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-300">
							<Icon path={mdiAccount} size={0.8} />
							<span>{players.length} online</span>
						</div>
					</div>

					<div
						className="bg-white dark:bg-neutral-900 rounded-lg shadow-lg w-full"
						id="map-container"
					>
						<div className="flex flex-col md:flex-row w-full h-full">
							<div
								ref={mapRef}
								className="relative bg-gray-50 dark:bg-neutral-800 rounded-t-lg md:rounded-l-lg md:rounded-tr-none w-full md:w-2/3 h-full overflow-hidden"
								onWheel={!isLegendExpanded ? handleWheel : undefined}
								onMouseDown={!isLegendExpanded ? handleMouseDown : undefined}
								onTouchStart={!isLegendExpanded ? handleTouchStart : undefined}
							>
								{!loading && !loadingBiomes && (
									<MapCanvas
										ref={canvasRef}
										viewportDimensions={viewportDimensions}
										transform={transform}
										visibleBiomes={visibleBiomes}
										players={players}
										locations={locations}
										hoveredEntity={hoveredEntity}
										setHoveredEntity={setHoveredEntity}
										currentDimension={currentDimension}
										isInteractionDisabled={isLegendExpanded}
										biomeHoverEnabled={biomeHoverEnabled}
									/>
								)}
								{hoveredEntity && (
									<div
										style={{
											position: "absolute",
											left: worldToCanvas(hoveredEntity.x, hoveredEntity.z).x,
											top:
												worldToCanvas(hoveredEntity.x, hoveredEntity.z).y - 55,
											transform: "translate(-50%, -50%)",
											pointerEvents: "none",
										}}
										className="bg-white dark:bg-neutral-900 bg-opacity-90 dark:bg-opacity-90 px-4 py-2 rounded shadow-lg"
									>
										<div className="text-sm text-gray-900 dark:text-gray-100 text-center">
											{Math.round(hoveredEntity.x)},{" "}
											{Math.round(hoveredEntity.z)}
										</div>
										{hoveredEntity.description && (
											<div className="text-xs text-gray-500 dark:text-gray-400 text-center mt-1">
												{hoveredEntity.description}
											</div>
										)}
									</div>
								)}
								<BiomeLegend
									ref={biomeLegendRef}
									allBiomes={dimensionStates[currentDimension].biomes}
									currentDimension={currentDimension}
									onDimensionChange={handleDimensionChange}
									onExpandedChange={setIsLegendExpanded}
									onBiomeHoverToggle={setBiomeHoverEnabled}
								/>

								<div className="absolute bottom-2 right-2 bg-white dark:bg-neutral-900 bg-opacity-75 dark:bg-opacity-75 px-2 py-1 rounded text-sm flex flex-col gap-1">
									<div className="flex items-center gap-1">
										<Icon
											path={mdiMapLegend}
											size={0.7}
											className="text-gray-600 dark:text-gray-400"
										/>
										<span className="text-gray-600 dark:text-gray-400">
											Scale: {calculateScale()}
										</span>
									</div>
									<span className="text-black dark:text-white">
										Center: {Math.round(transform.x)}, {Math.round(transform.z)}
									</span>
								</div>
							</div>

							<PlayerList
								players={players.filter(
									(player) => player.world === currentDimension,
								)}
								onPlayerClick={focusOnPlayer}
								selectedPlayer={selectedPlayer}
							/>
						</div>
					</div>
				</div>
			</motion.div>
		</div>
	);
};
MinecraftMap.getInitialProps = async () => {
	return { initialData: [] };
};

export default MinecraftMap;
