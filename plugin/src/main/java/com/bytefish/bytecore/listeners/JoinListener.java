package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

	private final ConfigManager config;
	private final Component welcomeMessage;

	public JoinListener(ConfigManager config) {
		this.config = config;
		this.welcomeMessage = buildWelcomeMessage();
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		event.joinMessage(null); // Remove default join message

		event
			.getPlayer()
			.getServer()
			.getScheduler()
			.runTaskLater(
				event
					.getPlayer()
					.getServer()
					.getPluginManager()
					.getPlugin("ByteCore"),
				() -> event.getPlayer().sendMessage(welcomeMessage),
				10L
			);
	}

	private Component buildWelcomeMessage() {
		return Component.text()
			.append(Component.text("Welcome to ", NamedTextColor.WHITE))
			.append(Component.text(config.getServerName(), NamedTextColor.BLUE))
			.append(Component.newline())
			.append(Component.text("→ ", NamedTextColor.GRAY))
			.append(
				Component.text(config.getServerWebsite(), NamedTextColor.BLUE)
					.clickEvent(ClickEvent.openUrl(config.getServerWebsite()))
					.decorate(TextDecoration.UNDERLINED)
			)
			.build();
	}
}
