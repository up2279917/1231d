package com.bytefish.bytecore;

import com.bytefish.bytecore.api.ApiServer;
import com.bytefish.bytecore.commands.*;
import com.bytefish.bytecore.config.ConfigManager;
import com.bytefish.bytecore.listeners.*;
import com.bytefish.bytecore.managers.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ByteCore extends JavaPlugin {

	private ConfigManager configManager;
	private ShopManager shopManager;
	private LocationManager locationManager;
	private WarningManager warningManager;
	private ApiServer apiServer;

	@Override
	public void onEnable() {
		if (!getDataFolder().exists()) {
			getDataFolder().mkdirs();
		}

		configManager = new ConfigManager(this);
		shopManager = new ShopManager(this, configManager);
		locationManager = new LocationManager(this);
		warningManager = new WarningManager(this);

		registerCommands();

		registerListeners();

		apiServer = new ApiServer(
			this,
			shopManager,
			locationManager,
			warningManager
		);
		apiServer.start();

		getLogger().info("ByteCore enabled successfully!");
	}

	@Override
	public void onDisable() {
		if (apiServer != null) {
			apiServer.stop();
		}

		if (shopManager != null) {
			shopManager.saveAll();
		}
		if (locationManager != null) {
			locationManager.saveAll();
		}
		if (warningManager != null) {
			warningManager.saveAll();
		}

		getLogger().info("ByteCore disabled successfully!");
	}

	private void registerCommands() {
		getCommand("shophelp").setExecutor(new ShopHelpCommand());
		getCommand("locationhelp").setExecutor(new LocationHelpCommand());
		getCommand("warn").setExecutor(new WarnCommand(warningManager));
		getCommand("rules").setExecutor(new RulesCommand(configManager));
		getCommand("msg").setExecutor(new MessageCommand());
		getCommand("checklocations").setExecutor(
			new CheckLocationsCommand(locationManager)
		);
		getCommand("itemname").setExecutor(new ItemNameCommand());
	}

	private void registerListeners() {
		PluginManager pm = getServer().getPluginManager();

		pm.registerEvents(
			new ShopCreationListener(shopManager, configManager),
			this
		);
		pm.registerEvents(
			new ShopInteractionListener(shopManager, configManager),
			this
		);
		pm.registerEvents(new ShopProtectionListener(shopManager), this);

		pm.registerEvents(new LocationSignListener(locationManager), this);
		pm.registerEvents(
			new LocationSignDestroyListener(locationManager),
			this
		);

		pm.registerEvents(new SignListener(this), this);
		pm.registerEvents(new JoinListener(configManager), this);
		pm.registerEvents(new TabListListener(configManager), this);

		pm.registerEvents(new CommandCompletionListener(this), this);

		getServer()
			.getScheduler()
			.runTaskTimer(
				this,
				locationManager::validateAllLocations,
				6000L,
				6000L
			);
	}

	public ConfigManager getConfigManager() {
		return configManager;
	}

	public ShopManager getShopManager() {
		return shopManager;
	}

	public LocationManager getLocationManager() {
		return locationManager;
	}

	public WarningManager getWarningManager() {
		return warningManager;
	}
}
