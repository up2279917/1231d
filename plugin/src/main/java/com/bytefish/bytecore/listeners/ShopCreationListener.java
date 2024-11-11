package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.config.ConfigManager;
import com.bytefish.bytecore.managers.ShopManager;
import com.bytefish.bytecore.models.Shop;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;

public class ShopCreationListener implements Listener {

	private final ShopManager shopManager;
	private final ConfigManager configManager;
	private final PlainTextComponentSerializer textSerializer =
		PlainTextComponentSerializer.plainText();

	private final BlockFace[] SIGN_FACES = {
		BlockFace.NORTH,
		BlockFace.SOUTH,
		BlockFace.EAST,
		BlockFace.WEST,
	};

	public ShopCreationListener(
		ShopManager shopManager,
		ConfigManager configManager
	) {
		this.shopManager = shopManager;
		this.configManager = configManager;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSignChange(SignChangeEvent event) {
		Block attachedBlock = getAttachedBlock(event.getBlock());
		if (
			attachedBlock == null ||
			!configManager.isValidShopContainer(attachedBlock.getType())
		) {
			return;
		}

		if (shopManager.isShop(attachedBlock.getLocation())) {
			event
				.getPlayer()
				.sendMessage(
					Component.text("You cannot edit this shop sign!").color(
						NamedTextColor.RED
					)
				);
			event.setCancelled(true);
			return;
		}

		String[] lines = new String[4];
		for (int i = 0; i < 4; i++) {
			lines[i] = textSerializer.serialize(event.line(i));
		}

		if (!isShopSign(lines)) {
			return;
		}

		// Always allow shop creation, regardless of display item settings
		try {
			processShopCreation(event, attachedBlock);
		} catch (IllegalArgumentException e) {
			event
				.getPlayer()
				.sendMessage(
					Component.text(
						"Error creating shop: " + e.getMessage()
					).color(NamedTextColor.RED)
				);
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();

		if (
			configManager.isValidShopContainer(block.getType()) &&
			shopManager.isShop(block.getLocation())
		) {
			Shop shop = shopManager.getShop(block.getLocation());
			if (shop != null) {
				if (
					!event.getPlayer().getUniqueId().equals(shop.getOwnerUUID())
				) {
					event.setCancelled(true);
					return;
				}
				shopManager.removeShop(block.getLocation());
			}
			return;
		}

		if (block.getBlockData() instanceof WallSign) {
			Block attached = getAttachedBlock(block);
			if (
				attached != null && shopManager.isShop(attached.getLocation())
			) {
				Shop shop = shopManager.getShop(attached.getLocation());
				if (
					shop != null &&
					!event.getPlayer().getUniqueId().equals(shop.getOwnerUUID())
				) {
					event.setCancelled(true);
				}
			}
		}
	}

	private Block getAttachedBlock(Block signBlock) {
		if (!(signBlock.getBlockData() instanceof WallSign wallSign)) {
			return null;
		}
		return signBlock.getRelative(wallSign.getFacing().getOppositeFace());
	}

	private boolean isShopSign(String[] lines) {
		return (
			lines[0] != null &&
			lines[0].equalsIgnoreCase("Selling") &&
			lines[2] != null &&
			lines[2].equalsIgnoreCase("For")
		);
	}

	private ShopParseResult parseShopSign(SignChangeEvent event) {
		String[] lines = new String[4];
		for (int i = 0; i < 4; i++) {
			lines[i] = textSerializer.serialize(event.line(i));
		}

		try {
			String[] sellingParts = lines[1].split("x");
			String[] priceParts = lines[3].split("x");

			if (sellingParts.length != 2 || priceParts.length != 2) {
				throw new IllegalArgumentException(
					"Invalid format. Use: amount x item"
				);
			}

			int sellingAmount = Integer.parseInt(sellingParts[0].trim());
			int priceAmount = Integer.parseInt(priceParts[0].trim());

			if (
				sellingAmount < 1 ||
				sellingAmount > 64 ||
				priceAmount < 1 ||
				priceAmount > 64
			) {
				throw new IllegalArgumentException(
					"Amounts must be between 1 and 64"
				);
			}

			Material sellingMaterial = getMaterialFromInput(
				sellingParts[1].trim()
			);
			Material priceMaterial = getMaterialFromInput(priceParts[1].trim());

			if (sellingMaterial == null || priceMaterial == null) {
				throw new IllegalArgumentException("Invalid item name or ID");
			}

			return new ShopParseResult(
				new ItemStack(sellingMaterial),
				sellingAmount,
				new ItemStack(priceMaterial),
				priceAmount
			);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid number format");
		}
	}

	private Material getMaterialFromInput(String input) {
		try {
			int itemId = Integer.parseInt(input);
			return Material.values()[itemId]; // Get Material by its ordinal value
		} catch (NumberFormatException e) {
			return Material.matchMaterial(input);
		} catch (ArrayIndexOutOfBoundsException e) {
			return null; // or handle it as you see fit
		}
	}

	private String formatItemName(Material material, int amount) {
		// Calculate maximum available space for the name
		// Format: "99x" + name = 15 chars total
		int amountSpace = String.valueOf(amount).length() + 1; // +1 for 'x'
		int maxNameLength = 15 - amountSpace;

		// Get the material name and format it
		String[] words = material
			.name()
			.toLowerCase()
			.replace('_', ' ')
			.split(" ");

		StringBuilder result = new StringBuilder();

		// Try to fit as many words as possible
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			// Capitalize first letter
			word = word.substring(0, 1).toUpperCase() + word.substring(1);

			// If this is not the first word, we need space for the space character
			int spaceNeeded = result.length() > 0
				? word.length() + 1
				: word.length();

			// If we can fit the whole word
			if (result.length() + spaceNeeded <= maxNameLength) {
				if (result.length() > 0) {
					result.append(" ");
				}
				result.append(word);
			} else {
				// If we can't fit the whole word, try to fit part of it
				int remainingSpace = maxNameLength - result.length();
				if (result.length() == 0 && remainingSpace > 0) {
					// If this is the first word and we have space, take what we can
					return word.substring(
						0,
						Math.min(word.length(), remainingSpace)
					);
				} else if (remainingSpace >= 4) { // Only add partial word if we can show at least 4 chars
					if (result.length() > 0) result.append(" ");
					result
						.append(word.substring(0, remainingSpace - 1))
						.append(".");
				}
				break;
			}
		}

		return result.toString();
	}

	private String truncateToSignLimit(String text) {
		final int MAX_LENGTH = 15;
		if (text.length() <= MAX_LENGTH) {
			return text;
		}

		return text.substring(0, MAX_LENGTH);
	}

	private static class ShopParseResult {

		final ItemStack sellingItem;
		final int sellingAmount;
		final ItemStack priceItem;
		final int priceAmount;

		ShopParseResult(
			ItemStack sellingItem,
			int sellingAmount,
			ItemStack priceItem,
			int priceAmount
		) {
			this.sellingItem = sellingItem;
			this.sellingAmount = sellingAmount;
			this.priceItem = priceItem;
			this.priceAmount = priceAmount;
		}
	}

	private void processShopCreation(SignChangeEvent event, Block container) {
		Player player = event.getPlayer();
		ShopParseResult result = parseShopSign(event);

		if (result == null) {
			throw new IllegalArgumentException("Invalid shop format");
		}

		Shop shop = shopManager.addShop(
			container.getLocation(),
			player,
			result.sellingItem,
			result.sellingAmount,
			result.priceItem,
			result.priceAmount
		);

		if (shop != null) {
			// Format the sign with enhanced styling
			event.line(
				0,
				Component.text("Selling")
					.color(NamedTextColor.GOLD)
					.decorate(TextDecoration.BOLD)
			);

			// Selling item line
			String sellingItemName = formatItemName(
				result.sellingItem.getType(),
				result.sellingAmount
			);
			event.line(
				1,
				Component.text()
					.append(
						Component.text(result.sellingAmount).color(
							NamedTextColor.YELLOW
						)
					)
					.append(Component.text("×").color(NamedTextColor.WHITE))
					.append(
						Component.text(sellingItemName).color(
							NamedTextColor.AQUA
						)
					)
					.build()
			);

			// "For" line
			event.line(
				2,
				Component.text("For")
					.color(NamedTextColor.GOLD)
					.decorate(TextDecoration.BOLD)
			);

			// Price item line
			String priceItemName = formatItemName(
				result.priceItem.getType(),
				result.priceAmount
			);
			event.line(
				3,
				Component.text()
					.append(
						Component.text(result.priceAmount).color(
							NamedTextColor.YELLOW
						)
					)
					.append(Component.text("×").color(NamedTextColor.WHITE))
					.append(
						Component.text(priceItemName).color(NamedTextColor.AQUA)
					)
					.build()
			);

			player.sendMessage(
				Component.text("Shop created successfully!").color(
					NamedTextColor.GREEN
				)
			);
		}
	}
}
