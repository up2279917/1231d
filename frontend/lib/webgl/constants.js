export const BUFFER_SIZES = {
	BIOME: 1000000, // 1M vertices for biomes
	ENTITY: 100000, // 100K vertices for entities
};

export const VERTEX_SIZES = {
	BIOME: 6, // x, y, r, g, b, isHovered
	ENTITY: 9, // x, y, tx, ty, r, g, b, a, type
};

// Rendering constants
export const CHUNK_SIZE = 16;
export const PLAYER_ICON_SIZE = 20;
export const LOCATION_ICON_SIZE = 8;

// Performance constants
export const MAX_VISIBLE_CHUNKS = 2000;
export const FRAME_TIME_TARGET = 16; // ~60 FPS
