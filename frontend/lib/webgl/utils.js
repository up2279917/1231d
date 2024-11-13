export const CHUNK_SIZE = 16;

export class WebGLUtils {
	static createShader(gl, type, source) {
		const shader = gl.createShader(type);
		gl.shaderSource(shader, source);
		gl.compileShader(shader);

		if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
			console.error("Shader compilation error:", gl.getShaderInfoLog(shader));
			gl.deleteShader(shader);
			return null;
		}

		return shader;
	}

	static createProgram(gl, vertexShader, fragmentShader) {
		const program = gl.createProgram();
		gl.attachShader(program, vertexShader);
		gl.attachShader(program, fragmentShader);
		gl.linkProgram(program);

		if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
			console.error("Program linking error:", gl.getProgramInfoLog(program));
			gl.deleteProgram(program);
			return null;
		}

		return program;
	}

	static createBuffer(gl, data, usage = gl.STATIC_DRAW) {
		const buffer = gl.createBuffer();
		gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
		gl.bufferData(gl.ARRAY_BUFFER, data, usage);
		return buffer;
	}

	static createTexture(gl, image) {
		const texture = gl.createTexture();
		gl.bindTexture(gl.TEXTURE_2D, texture);

		// Set texture parameters
		gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
		gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
		gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
		gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);

		// Upload image to texture
		gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);

		return texture;
	}

	static loadImage(url) {
		return new Promise((resolve, reject) => {
			const image = new Image();
			image.crossOrigin = "anonymous";
			image.onload = () => resolve(image);
			image.onerror = reject;
			image.src = url;
		});
	}

	// Spatial hash for efficient biome lookup
	static getBiomeHash(x, z) {
		const gridX = Math.floor(x / CHUNK_SIZE);
		const gridZ = Math.floor(z / CHUNK_SIZE);
		return `${gridX},${gridZ}`;
	}

	// Check if a point is within the map bounds
	static isInBounds(x, z) {
		return x >= -5000 && x <= 5000 && z >= -5000 && z <= 5000;
	}

	// Convert world coordinates to screen coordinates
	static worldToScreen(x, z, transform, width, height) {
		return {
			x: width / 2 + (x - transform.x) * transform.scale,
			y: height / 2 + (z - transform.z) * transform.scale,
		};
	}

	// Convert screen coordinates to world coordinates
	static screenToWorld(x, y, transform, width, height) {
		return {
			x: (x - width / 2) / transform.scale + transform.x,
			z: (y - height / 2) / transform.scale + transform.z,
		};
	}

	// Create text texture for labels
	static createTextTexture(gl, text, fontSize = 12, fontFamily = "sans-serif") {
		const canvas = document.createElement("canvas");
		const ctx = canvas.getContext("2d");

		// Set canvas size to power of 2
		canvas.width = 256;
		canvas.height = 32;

		// Setup text rendering
		ctx.font = `${fontSize}px ${fontFamily}`;
		ctx.fillStyle = "white";
		ctx.textAlign = "center";
		ctx.textBaseline = "middle";

		// Clear canvas
		ctx.clearRect(0, 0, canvas.width, canvas.height);

		// Draw text
		ctx.fillText(text, canvas.width / 2, canvas.height / 2);

		// Create texture
		return WebGLUtils.createTexture(gl, canvas);
	}
}
