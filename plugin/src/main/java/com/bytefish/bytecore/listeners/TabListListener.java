package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class TabListListener implements Listener {

	private final Component header;
	private final Component footer;

	public TabListListener(ConfigManager config) {
		this.header = Component.text()
			.append(
				Component.text(
					config.getServerName(),
					NamedTextColor.BLUE
				).decorate(TextDecoration.BOLD)
			)
			.append(Component.newline())
			.append(Component.text("Community Server", NamedTextColor.WHITE))
			.build();

		this.footer = Component.text()
			.append(Component.newline())
			.append(
				Component.text(config.getServerWebsite(), NamedTextColor.GRAY)
			)
			.build();
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		event.getPlayer().sendPlayerListHeader(header);
		event.getPlayer().sendPlayerListFooter(footer);
	}
}
