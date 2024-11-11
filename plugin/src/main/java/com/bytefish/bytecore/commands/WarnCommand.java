package com.bytefish.bytecore.commands;

import com.bytefish.bytecore.managers.WarningManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WarnCommand implements CommandExecutor {

	private final WarningManager warningManager;

	public WarnCommand(WarningManager warningManager) {
		this.warningManager = warningManager;
	}

	@Override
	public boolean onCommand(
		@NotNull CommandSender sender,
		@NotNull Command command,
		@NotNull String label,
		String[] args
	) {
		if (args.length < 2) {
			sender.sendMessage(
				Component.text()
					.append(Component.text("Usage: ", NamedTextColor.WHITE))
					.append(
						Component.text(
							"/warn <player> <reason>",
							NamedTextColor.YELLOW
						)
					)
					.build()
			);
			return true;
		}

		String playerName = args[0];
		Player target = Bukkit.getPlayer(playerName);

		if (target == null) {
			sender.sendMessage(
				Component.text()
					.append(Component.text("Player ", NamedTextColor.RED))
					.append(Component.text(playerName, NamedTextColor.YELLOW))
					.append(Component.text(" not found!", NamedTextColor.RED))
					.build()
			);
			return true;
		}

		String reason = String.join(" ", args).substring(
			playerName.length() + 1
		);

		warningManager.addWarning(sender, target, reason);

		return true;
	}
}
