package com.bytefish.bytecore.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class MessageCommand implements CommandExecutor {

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
							"/msg <player> <message>",
							NamedTextColor.YELLOW
						)
					)
					.build()
			);
			return true;
		}

		Player target = Bukkit.getPlayer(args[0]);
		if (target == null) {
			sender.sendMessage(
				Component.text()
					.append(Component.text("Player ", NamedTextColor.RED))
					.append(Component.text(args[0], NamedTextColor.YELLOW))
					.append(Component.text(" not found!", NamedTextColor.RED))
					.build()
			);
			return true;
		}

		String message = String.join(" ", args).substring(args[0].length() + 1);
		Component senderMessage = Component.text()
			.append(Component.text("To ", NamedTextColor.GRAY))
			.append(
				Component.text(target.getName(), NamedTextColor.LIGHT_PURPLE)
			)
			.append(Component.text(" » ", NamedTextColor.GRAY))
			.append(Component.text(message, NamedTextColor.WHITE))
			.build();

		Component targetMessage = Component.text()
			.append(Component.text("From ", NamedTextColor.GRAY))
			.append(
				Component.text(sender.getName(), NamedTextColor.LIGHT_PURPLE)
			)
			.append(Component.text(" » ", NamedTextColor.GRAY))
			.append(Component.text(message, NamedTextColor.WHITE))
			.build();

		sender.sendMessage(senderMessage);
		target.sendMessage(targetMessage);

		return true;
	}
}
