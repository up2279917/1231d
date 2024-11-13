import { WebGLUtils } from "./utils";
import { TEXTURE_SIZE } from "./shaders";

export class TextureManager {
	constructor(gl) {
		this.gl = gl;
		this.playerTextures = new Map();
		this.textTextures = new Map();
		this.pendingLoads = new Map();
	}

	async loadPlayerTexture(playerName) {
		// Check cache first
		if (this.playerTextures.has(playerName)) {
			return this.playerTextures.get(playerName);
		}

		// Check if already loading
		if (this.pendingLoads.has(playerName)) {
			return this.pendingLoads.get(playerName);
		}

		// Start new load
		const loadPromise = WebGLUtils.loadImage(
			`https://mc-heads.net/avatar/${playerName}`,
		)
			.then((image) => {
				const texture = WebGLUtils.createTexture(this.gl, image);
				this.playerTextures.set(playerName, texture);
				this.pendingLoads.delete(playerName);
				return texture;
			})
			.catch((error) => {
				console.error(`Failed to load texture for ${playerName}:`, error);
				this.pendingLoads.delete(playerName);
				return null;
			});

		this.pendingLoads.set(playerName, loadPromise);
		return loadPromise;
	}

	getTextTexture(text, fontSize = 12) {
		const key = `${text}_${fontSize}`;
		if (!this.textTextures.has(key)) {
			const texture = WebGLUtils.createTextTexture(this.gl, text, fontSize);
			this.textTextures.set(key, texture);
		}
		return this.textTextures.get(key);
	}

	cleanup() {
		const gl = this.gl;

		// Cleanup player textures
		for (const texture of this.playerTextures.values()) {
			gl.deleteTexture(texture);
		}
		this.playerTextures.clear();

		// Cleanup text textures
		for (const texture of this.textTextures.values()) {
			gl.deleteTexture(texture);
		}
		this.textTextures.clear();
	}
}
