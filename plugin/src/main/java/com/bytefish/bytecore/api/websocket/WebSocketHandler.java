package com.bytefish.bytecore.api.websocket;

import com.bytefish.bytecore.ByteCore;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

@WebSocket
public class WebSocketHandler {

	private static final Queue<Session> sessions =
		new ConcurrentLinkedQueue<>();
	private final Gson gson;
	private final ByteCore plugin;

	public WebSocketHandler(ByteCore plugin, Gson gson) {
		this.plugin = plugin;
		this.gson = gson;
		plugin.getLogger().info("WebSocketHandler initialized");
	}

	@OnWebSocketConnect
	public void onConnect(Session session) {
		sessions.add(session);
		broadcastData();
	}

	@OnWebSocketClose
	public void onClose(Session session, int statusCode, String reason) {
		sessions.remove(session);
	}

	@OnWebSocketError
	public void onError(Session session, Throwable error) {
		plugin
			.getLogger()
			.warning(
				"WebSocket error from " +
				session.getRemoteAddress() +
				": " +
				error.getMessage()
			);
		error.printStackTrace();
		sessions.remove(session);
	}

	public void broadcastData() {
		if (sessions.isEmpty()) return;

		try {
			Map<String, Object> payload = new HashMap<>();

			// Add player locations
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

			// Add custom locations
			List<Map<String, Object>> customLocations = plugin
				.getLocationManager()
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

			payload.put("players", playerLocations);
			payload.put("locations", customLocations);
			payload.put("timestamp", System.currentTimeMillis());

			String json = gson.toJson(payload);

			sessions.forEach(session -> {
				try {
					if (session.isOpen()) {
						session.getRemote().sendString(json);
					}
				} catch (IOException e) {
					plugin
						.getLogger()
						.warning(
							"Failed to send WebSocket message to " +
							session.getRemoteAddress() +
							": " +
							e.getMessage()
						);
					sessions.remove(session);
				}
			});
		} catch (Exception e) {
			plugin
				.getLogger()
				.severe("Error broadcasting WebSocket data: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
