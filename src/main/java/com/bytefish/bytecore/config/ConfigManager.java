package com.bytefish.bytecore.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {

	private final JavaPlugin plugin;
	private final Set<Material> allowedContainers;
	private Material defaultContainer;
	private int protectionRadius;
	private int maxLocationsPerPlayer;
	private String serverName;
	private String serverWebsite;
	private List<String> serverRules;
	private int apiPort;
	private boolean displayItemsEnabled;
	private boolean displayItemsOpOnly;
	private double displayItemHeight;
	private double displayItemAmplitude;
	private double displayItemFrequency;

	public ConfigManager(JavaPlugin plugin) {
		this.plugin = plugin;
		this.allowedContainers = new HashSet<>();
		loadConfig();
	}

	public void loadConfig() {
		plugin.saveDefaultConfig();
		FileConfiguration config = plugin.getConfig();
		loadContainerSettings(config);
		protectionRadius = config.getInt("shops.protection-radius", 2);

		maxLocationsPerPlayer = config.getInt("locations.max-per-player", 3);

		serverName = config.getString("server.name", "bytefi.sh");
		serverWebsite = config.getString(
			"server.website",
			"https://bytefi.sh/mc"
		);
		apiPort = config.getInt("api.port", 25578);
		serverRules = config.getStringList("rules");
		displayItemsEnabled = config.getBoolean(
			"shops.display-items.enabled",
			true
		);
		displayItemsOpOnly = config.getBoolean(
			"shops.display-items.op-only",
			false
		);
		displayItemHeight = config.getDouble(
			"shops.display-items.float-height",
			1.2
		);
		displayItemAmplitude = config.getDouble(
			"shops.display-items.float-amplitude",
			0.1
		);
		displayItemFrequency = config.getDouble(
			"shops.display-items.float-frequency",
			2.0
		);
	}

	private void loadContainerSettings(FileConfiguration config) {
		allowedContainers.clear();

		List<String> containers = config.getStringList(
			"shops.allowed-containers"
		);
		for (String container : containers) {
			try {
				Material material = Material.valueOf(container.toUpperCase());
				if (
					(material.isBlock() && material.name().contains("CHEST")) ||
					material.name().equals("BARREL")
				) {
					allowedContainers.add(material);
				}
			} catch (IllegalArgumentException ignored) {
				plugin
					.getLogger()
					.warning("Invalid container material: " + container);
			}
		}

		String defaultContainerName = config.getString(
			"shops.enabled-by-default",
			"BARREL"
		);
		try {
			defaultContainer = Material.valueOf(
				defaultContainerName.toUpperCase()
			);
			if (!allowedContainers.contains(defaultContainer)) {
				defaultContainer = Material.BARREL;
				plugin
					.getLogger()
					.warning(
						"Default container not in allowed list, falling back to BARREL"
					);
			}
		} catch (IllegalArgumentException e) {
			defaultContainer = Material.BARREL;
			plugin
				.getLogger()
				.warning(
					"Invalid default container specified, falling back to BARREL"
				);
		}

		allowedContainers.add(Material.BARREL);
	}

	// Getter methods
	public Set<Material> getAllowedContainers() {
		return new HashSet<>(allowedContainers);
	}

	public Material getDefaultContainer() {
		return defaultContainer;
	}

	public int getProtectionRadius() {
		return protectionRadius;
	}

	public int getMaxLocationsPerPlayer() {
		return maxLocationsPerPlayer;
	}

	public String getServerName() {
		return serverName;
	}

	public String getServerWebsite() {
		return serverWebsite;
	}

	public List<String> getServerRules() {
		return serverRules;
	}

	public int getApiPort() {
		return apiPort;
	}

	public boolean isValidShopContainer(Material material) {
		return allowedContainers.contains(material);
	}

	public boolean isDisplayItemsEnabled() {
		return displayItemsEnabled;
	}

	public boolean isDisplayItemsOpOnly() {
		return displayItemsOpOnly;
	}

	public double getDisplayItemHeight() {
		return displayItemHeight;
	}

	public double getDisplayItemAmplitude() {
		return displayItemAmplitude;
	}

	public double getDisplayItemFrequency() {
		return displayItemFrequency;
	}
}
