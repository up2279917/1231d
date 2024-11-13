// File: /frontend/lib/performanceMonitor.js

export class PerformanceMonitor {
	static instance = null;

	constructor(name) {
		if (PerformanceMonitor.instance) {
			return PerformanceMonitor.instance;
		}

		this.name = name;
		this.enabled = false;
		this.frameStartTime = 0;
		this.metrics = this.resetMetrics();
		this.debug = false; // Set to false by default
		this.lastMetricsOutput = 0;

		PerformanceMonitor.instance = this;
	}

	startFrame() {
		if (!this.enabled) return;
		this.frameStartTime = performance.now();
		this.metrics.renderCalls++;
	}

	measureBiomeRender(duration) {
		if (!this.enabled) return;
		this.metrics.biomeRenderTime += duration;
		this.metrics.totalBiomes++; // Add counter for biomes rendered
	}

	measureEntityRender(duration) {
		if (!this.enabled) return;
		this.metrics.entityRenderTime += duration;
	}

	endFrame() {
		if (!this.enabled || !this.frameStartTime) return;

		const frameTime = performance.now() - this.frameStartTime;
		this.metrics.maxFrameTime = Math.max(this.metrics.maxFrameTime, frameTime);
		this.metrics.totalFrameTime += frameTime;
		this.metrics.frameCount++;

		const currentTime = performance.now();
		const timeSinceLastLog = currentTime - this.lastMetricsOutput;

		// Output metrics every second
		if (timeSinceLastLog >= 1000) {
			const fps = Math.round(
				(this.metrics.frameCount * 1000) / timeSinceLastLog,
			);
			const avgFrameTime =
				this.metrics.totalFrameTime / this.metrics.frameCount;
			const avgBiomeTime =
				this.metrics.biomeRenderTime / this.metrics.renderCalls;
			const avgEntityTime =
				this.metrics.entityRenderTime / this.metrics.renderCalls;
			const biomesPerFrame = this.metrics.totalBiomes / this.metrics.frameCount;

			console.table({
				"Map Performance": {
					FPS: fps,
					"Frame Time": `${avgFrameTime.toFixed(1)}ms`,
					"Peak Frame": `${this.metrics.maxFrameTime.toFixed(1)}ms`,
					"Biome Render": `${avgBiomeTime.toFixed(1)}ms (${Math.round(biomesPerFrame)} biomes/frame)`,
					"Entity Render": `${avgEntityTime.toFixed(1)}ms`,
					"Active Frames": this.metrics.frameCount - this.metrics.skippedFrames,
					"Skipped Frames": this.metrics.skippedFrames,
				},
			});

			this.metrics = this.resetMetrics();
			this.lastMetricsOutput = currentTime;
		}

		this.frameStartTime = 0;
	}

	skipFrame() {
		if (!this.enabled) return;
		this.metrics.skippedFrames++;
	}

	resetMetrics() {
		return {
			frameCount: 0,
			skippedFrames: 0,
			lastTime: performance.now(),
			maxFrameTime: 0,
			totalFrameTime: 0,
			renderCalls: 0,
			biomeRenderTime: 0,
			entityRenderTime: 0,
			totalBiomes: 0,
		};
	}

	enable() {
		this.enabled = true;
		this.metrics = this.resetMetrics();
	}

	disable() {
		this.enabled = false;
	}
}
