package com.bytefish.bytecore.api;

import com.bytefish.bytecore.ByteCore;
import com.bytefish.bytecore.managers.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import spark.Spark;

public class ApiServer {

	private final ByteCore plugin;
	private final ShopManager shopManager;
	private final LocationManager locationManager;
	private final WarningManager warningManager;
	private final Gson gson;
	private boolean isRunning;

	public ApiServer(
		ByteCore plugin,
		ShopManager shopManager,
		LocationManager locationManager,
		WarningManager warningManager
	) {
		this.plugin = plugin;
		this.shopManager = shopManager;
		this.locationManager = locationManager;
		this.warningManager = warningManager;
		this.gson = new GsonBuilder()
			.setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
			.setPrettyPrinting()
			.create();
		this.isRunning = false;
	}

	public void start() {
		if (isRunning) {
			return;
		}

		try {
			Spark.port(25578);
			Spark.threadPool(20);
			setupRoutes();

			Spark.awaitInitialization();
			isRunning = true;
			plugin.getLogger().info("API Server started on port 25578");
		} catch (Exception e) {
			plugin
				.getLogger()
				.severe("Failed to start API server: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void setupRoutes() {
		Spark.get("/", (request, response) -> {
			response.redirect("https://mc.bytefi.sh/");
			return null;
		});
		Spark.get("/api/health", (request, response) ->
			gson.toJson(Map.of("status", "up", "timestamp", new Date()))
		);
		Spark.get("/api/stats", (request, response) -> {
			Map<String, Object> stats = new HashMap<>();

			stats.put("playerCount", Bukkit.getOnlinePlayers().size());
			stats.put("maxPlayers", Bukkit.getMaxPlayers());
			stats.put("tps", Math.min(20.0, Bukkit.getTPS()[0]));

			// Online players
			List<Map<String, Object>> players = Bukkit.getOnlinePlayers()
				.stream()
				.map(player -> {
					Map<String, Object> playerInfo = new HashMap<>();
					playerInfo.put("name", player.getName());
					playerInfo.put("isOperator", player.isOp());
					return playerInfo;
				})
				.collect(Collectors.toList());
			stats.put("onlinePlayers", players);

			return gson.toJson(stats);
		});

		Spark.get("/api/warnings", (request, response) -> {
			List<Map<String, Object>> warnings = warningManager
				.getAllWarnings()
				.stream()
				.map(warning -> {
					Map<String, Object> warningData = new HashMap<>();
					warningData.put("player", warning.getPlayerName());
					warningData.put("issuer", warning.getStaffMember());
					warningData.put("reason", warning.getReason());
					warningData.put("timestamp", warning.getTimestamp());
					warningData.put("server", warning.getServerName());
					return warningData;
				})
				.collect(Collectors.toList());
			return gson.toJson(warnings);
		});

		Spark.get("/api/locations", (request, response) -> {
			List<Map<String, Object>> locations = locationManager
				.getAllLocations()
				.stream()
				.map(location -> {
					Map<String, Object> locationData = new HashMap<>();
					locationData.put("name", location.getName());
					locationData.put("owner", location.getOwner());
					locationData.put("x", location.getX());
					locationData.put("z", location.getZ());
					locationData.put("world", location.getWorld());
					locationData.put("description", location.getDescription());
					locationData.put("timestamp", location.getTimestamp());
					return locationData;
				})
				.collect(Collectors.toList());
			return gson.toJson(locations);
		});

		Spark.get("/api/players/locations", (request, response) -> {
			List<Map<String, Object>> playerLocations =
				Bukkit.getOnlinePlayers()
					.stream()
					.map(player -> {
						Map<String, Object> location = new HashMap<>();
						location.put("name", player.getName());
						location.put("x", player.getLocation().getBlockX());
						location.put("z", player.getLocation().getBlockZ());
						location.put("world", player.getWorld().getName());
						return location;
					})
					.collect(Collectors.toList());
			return gson.toJson(playerLocations);
		});

		Spark.exception(Exception.class, (e, request, response) -> {
			plugin.getLogger().warning("API Error: " + e.getMessage());
			response.status(500);
			response.body(
				gson.toJson(
					Map.of(
						"error",
						"Internal server error",
						"message",
						e.getMessage()
					)
				)
			);
		});

		Spark.notFound((request, response) -> {
			response.status(404);
			return gson.toJson(
				Map.of(
					"error",
					"Not found",
					"message",
					"The requested resource was not found"
				)
			);
		});
	}

	public void stop() {
		if (!isRunning) {
			return;
		}

		try {
			Spark.stop();
			Spark.awaitStop();
			isRunning = false;
			plugin.getLogger().info("API Server stopped");
		} catch (Exception e) {
			plugin
				.getLogger()
				.severe("Error stopping API server: " + e.getMessage());
		}
	}
}
