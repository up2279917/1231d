/** @type {import('next').NextConfig} */
const nextConfig = {
	reactStrictMode: true,
	async rewrites() {
		return [
			{
				source: "/biome-data/:file",
				destination: "/data/:file", // This will serve from /data directory at build time
			},
		];
	},
	async headers() {
		return [
			{
				source: "/biome-data/:file",
				headers: [
					{
						key: "Cache-Control",
						value: "public, max-age=2592000, stale-while-revalidate=86400", // 30 days + 1 day stale
					},
					{
						key: "Content-Type",
						value: "application/json",
					},
					{
						key: "Content-Encoding",
						value: "gzip",
					},
				],
			},
		];
	},
};

export default nextConfig;
