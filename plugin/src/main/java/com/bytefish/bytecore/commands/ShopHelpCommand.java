package com.bytefish.bytecore.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ShopHelpCommand implements CommandExecutor {

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
						"═══ Shop Creation Guide ═══",
						NamedTextColor.GOLD
					).decorate(TextDecoration.BOLD)
				)
				.build()
		);

		sender.sendMessage(Component.empty());

		sender.sendMessage(
			Component.text()
				.append(Component.text("1. ", NamedTextColor.GOLD))
				.append(Component.text("Place a barrel", NamedTextColor.WHITE))
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("2. ", NamedTextColor.GOLD))
				.append(
					Component.text(
						"Place a sign on any side",
						NamedTextColor.WHITE
					)
				)
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("3. ", NamedTextColor.GOLD))
				.append(
					Component.text(
						"Format the sign exactly like this:",
						NamedTextColor.WHITE
					)
				)
				.build()
		);

		sender.sendMessage(Component.empty());

		sender.sendMessage(
			Component.text()
				.append(Component.text("Line 1: ", NamedTextColor.GRAY))
				.append(Component.text("Selling", NamedTextColor.GOLD))
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("Line 2: ", NamedTextColor.GRAY))
				.append(Component.text("10", NamedTextColor.YELLOW))
				.append(Component.text("x"))
				.append(Component.text("diamond", NamedTextColor.AQUA))
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("Line 3: ", NamedTextColor.GRAY))
				.append(Component.text("For", NamedTextColor.GOLD))
				.build()
		);

		sender.sendMessage(
			Component.text()
				.append(Component.text("Line 4: ", NamedTextColor.GRAY))
				.append(Component.text("64", NamedTextColor.YELLOW))
				.append(Component.text("x"))
				.append(Component.text("dirt", NamedTextColor.AQUA))
				.build()
		);

		sender.sendMessage(Component.empty());

		sender.sendMessage(
			Component.text()
				.append(Component.text("Note: ", NamedTextColor.YELLOW))
				.append(
					Component.text(
						"Only shop owners can break their shops",
						NamedTextColor.GRAY
					)
				)
				.build()
		);

		return true;
	}
}
