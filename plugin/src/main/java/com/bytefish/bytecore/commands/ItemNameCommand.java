package com.bytefish.bytecore.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ItemNameCommand implements CommandExecutor {

	@Override
	public boolean onCommand(
		@NotNull CommandSender sender,
		@NotNull Command command,
		@NotNull String label,
		String[] args
	) {
		// Check if the sender is a player
		if (!(sender instanceof Player)) {
			sender.sendMessage(
				Component.text(
					"This command can only be executed by a player."
				).color(NamedTextColor.RED)
			);
			return true;
		}

		Player player = (Player) sender; // Cast sender to Player
		ItemStack itemInHand = player.getInventory().getItemInMainHand(); // Get the item in the player's main hand

		// Check if the player is holding an item
		if (itemInHand == null || itemInHand.getType() == Material.AIR) {
			player.sendMessage(
				Component.text("You are not holding any item.").color(
					NamedTextColor.RED
				)
			);
			return true;
		}

		// Get the material name and ID
		String materialName = itemInHand.getType().name(); // Get the material name
		int materialId = itemInHand.getType().ordinal(); // Get the ordinal value as ID

		// Send the output to the player
		player.sendMessage(
			Component.text(
				"You are holding: " + materialName + " with ID: " + materialId
			).color(NamedTextColor.GREEN)
		);

		return true;
	}

	private Material getMaterialFromInput(String input) {
		// First, try to parse the input as an integer (item ID)
		try {
			int itemId = Integer.parseInt(input);
			// Get Material by its ordinal value
			if (itemId >= 0 && itemId < Material.values().length) {
				return Material.values()[itemId]; // Get Material by its ordinal value
			} else {
				return null; // Handle out of bounds
			}
		} catch (NumberFormatException e) {
			// If parsing fails, try to match the material by name
			Material material = Material.matchMaterial(input);
			if (material != null) {
				return material;
			}
		}
		return null; // Return null if no material is found
	}
}
