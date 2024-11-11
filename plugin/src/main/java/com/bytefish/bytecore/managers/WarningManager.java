package com.bytefish.bytecore.managers;

import com.bytefish.bytecore.ByteCore;
import com.bytefish.bytecore.models.Warning;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WarningManager {

	private final ByteCore plugin;
	private final Map<UUID, List<Warning>> playerWarnings;
	private final File warningsFile;
	private final Gson gson;
	private final Map<UUID, Long> lastWarningTime = new ConcurrentHashMap<>();
	private static final long WARNING_COOLDOWN = 0;

	public WarningManager(ByteCore plugin) {
		this.plugin = plugin;
		this.playerWarnings = new ConcurrentHashMap<>();
		this.warningsFile = new File(plugin.getDataFolder(), "warnings.json");
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		loadWarnings();
	}

	public Warning addWarning(
		CommandSender issuer,
		Player target,
		String reason
	) {
		if (target == null || !target.isOnline()) {
			return null;
		}

		String sanitizedReason = reason.trim();
		if (sanitizedReason.length() > 255) {
			sanitizedReason = sanitizedReason.substring(0, 255);
		}

		UUID issuerUUID = issuer instanceof Player
			? ((Player) issuer).getUniqueId()
			: null;
		String issuerName = issuer instanceof Player
			? issuer.getName()
			: "Console";

		Warning warning = new Warning(
			target.getName(),
			target.getUniqueId(),
			issuerName,
			issuerUUID,
			sanitizedReason,
			plugin.getConfigManager().getServerName()
		);

		synchronized (playerWarnings) {
			playerWarnings
				.computeIfAbsent(target.getUniqueId(), k ->
					Collections.synchronizedList(new ArrayList<>())
				)
				.add(warning);
		}

		if (!isRateLimited(target.getUniqueId())) {
			Component warningMessage = Component.text()
				.append(Component.text("WARNING: ", NamedTextColor.RED))
				.append(Component.text(target.getName(), NamedTextColor.YELLOW))
				.append(
					Component.text(" has been warned by ", NamedTextColor.WHITE)
				)
				.append(Component.text(issuerName, NamedTextColor.YELLOW))
				.append(Component.text(" for: ", NamedTextColor.WHITE))
				.append(Component.text(sanitizedReason, NamedTextColor.GRAY))
				.build();

			plugin.getServer().broadcast(warningMessage);
		}

		saveWarnings();
		return warning;
	}

	private boolean isRateLimited(UUID playerUUID) {
		long currentTime = System.currentTimeMillis();
		Long lastTime = lastWarningTime.get(playerUUID);

		if (lastTime == null || currentTime - lastTime >= WARNING_COOLDOWN) {
			lastWarningTime.put(playerUUID, currentTime);
			return false;
		}
		return true;
	}

	public List<Warning> getPlayerWarnings(UUID playerUUID) {
		return new ArrayList<>(
			playerWarnings.getOrDefault(playerUUID, new ArrayList<>())
		);
	}

	public List<Warning> getPlayerWarnings(String playerName) {
		return getAllWarnings()
			.stream()
			.filter(w -> w.getPlayerName().equalsIgnoreCase(playerName))
			.collect(Collectors.toList());
	}

	public List<Warning> getRecentWarnings(int days) {
		long cutoff =
			System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
		return getAllWarnings()
			.stream()
			.filter(w -> w.getTimestamp() * 1000L >= cutoff)
			.collect(Collectors.toList());
	}

	public List<Warning> getAllWarnings() {
		return playerWarnings
			.values()
			.stream()
			.flatMap(List::stream)
			.collect(Collectors.toList());
	}

	public int getWarningCount(UUID playerUUID) {
		return playerWarnings
			.getOrDefault(playerUUID, new ArrayList<>())
			.size();
	}

	private void loadWarnings() {
		if (!warningsFile.exists()) {
			saveWarnings();
			return;
		}

		try (Reader reader = new FileReader(warningsFile)) {
			Type type = new TypeToken<Map<UUID, List<Warning>>>() {}.getType();
			Map<UUID, List<Warning>> loaded = gson.fromJson(reader, type);

			if (loaded != null) {
				playerWarnings.clear();
				playerWarnings.putAll(loaded);
			}
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe("Failed to load warnings: " + e.getMessage());
		}
	}

	public void saveAll() {
		try {
			if (!warningsFile.exists()) {
				plugin.getDataFolder().mkdirs();
				warningsFile.createNewFile();
			}

			try (Writer writer = new FileWriter(warningsFile)) {
				gson.toJson(playerWarnings, writer);
			}
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe("Failed to save warnings: " + e.getMessage());
		}
	}

	private void saveWarnings() {
		saveAll();
	}
}
