package com.bytefish.bytecore.commands;

import com.bytefish.bytecore.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class RulesCommand implements CommandExecutor {

	private final ConfigManager config;

	public RulesCommand(ConfigManager config) {
		this.config = config;
	}

	@Override
	public boolean onCommand(
		@NotNull CommandSender sender,
		@NotNull Command command,
		@NotNull String label,
		String[] args
	) {
		sender.sendMessage(
			Component.text()
				.append(
					Component.text(
						"═══ Server Rules ═══",
						NamedTextColor.GOLD
					).decorate(TextDecoration.BOLD)
				)
				.build()
		);

		sender.sendMessage(Component.empty());

		int ruleNumber = 1;
		for (String rule : config.getServerRules()) {
			sender.sendMessage(
				Component.text()
					.append(
						Component.text(ruleNumber + ". ", NamedTextColor.GOLD)
					)
					.append(Component.text(rule, NamedTextColor.WHITE))
					.build()
			);
			ruleNumber++;
		}

		return true;
	}
}
