package com.bytefish.bytecore.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class LocationHelpCommand implements CommandExecutor {

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
						"═══ Location Creation Guide ═══",
						NamedTextColor.GOLD
					).decorate(TextDecoration.BOLD)
				)
				.build()
		);

		sender.sendMessage(Component.empty());

		sender.sendMessage(
			Component.text()
				.append(Component.text("1. ", NamedTextColor.GOLD))
				.append(
					Component.text(
						"Go to the location you want to mark",
						NamedTextColor.WHITE
					)
				)
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("2. ", NamedTextColor.GOLD))
				.append(Component.text("Place a sign", NamedTextColor.WHITE))
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("3. ", NamedTextColor.GOLD))
				.append(
					Component.text(
						"Format the sign like this:",
						NamedTextColor.WHITE
					)
				)
				.build()
		);

		sender.sendMessage(Component.empty());

		sender.sendMessage(
			Component.text()
				.append(Component.text("Line 1: ", NamedTextColor.GRAY))
				.append(Component.text("Location", NamedTextColor.GOLD))
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("Line 2: ", NamedTextColor.GRAY))
				.append(Component.text("<name>", NamedTextColor.YELLOW))
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("Line 3: ", NamedTextColor.GRAY))
				.append(Component.text("<description>", NamedTextColor.GRAY))
				.append(Component.text(" (optional)", NamedTextColor.DARK_GRAY))
				.build()
		);

		sender.sendMessage(Component.empty());

		sender.sendMessage(
			Component.text()
				.append(Component.text("Notes:", NamedTextColor.YELLOW))
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("• ", NamedTextColor.GRAY))
				.append(
					Component.text(
						"Maximum of 3 locations per player",
						NamedTextColor.WHITE
					)
				)
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("• ", NamedTextColor.GRAY))
				.append(
					Component.text(
						"Anyone can break location signs",
						NamedTextColor.WHITE
					)
				)
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("• ", NamedTextColor.GRAY))
				.append(
					Component.text(
						"Coordinates will be shown on line 4",
						NamedTextColor.WHITE
					)
				)
				.build()
		);

		return true;
	}
}
