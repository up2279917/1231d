package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.config.ConfigManager;
import com.bytefish.bytecore.managers.ShopManager;
import com.bytefish.bytecore.models.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

	private String formatItemName(String name) {
		String formattedName = name.toLowerCase().replace('_', ' ').trim();
		if (formattedName.length() > 15) {
			return formattedName.substring(0, 15);
		}
		return formattedName;
	}

	private String truncateToSignLimit(String itemName) {
		final int MAX_LENGTH = 15;

		if (itemName.length() > MAX_LENGTH) {
			return itemName.substring(0, MAX_LENGTH);
		}
		return itemName;
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
			event.line(0, Component.text("Selling").color(NamedTextColor.GOLD));
			event.line(
				1,
				Component.text()
					.append(
						Component.text(
							String.valueOf(result.sellingAmount)
						).color(NamedTextColor.YELLOW)
					)
					.append(Component.text("×").color(NamedTextColor.WHITE))
					.append(
						Component.text(
							formatItemName(
								result.sellingItem.getType().name() +
								" " +
								result.sellingItem
									.getType()
									.toString()
									.toLowerCase()
							)
						).color(NamedTextColor.AQUA)
					)
					.build()
			);
			event.line(2, Component.text("For").color(NamedTextColor.GOLD));
			event.line(
				3,
				Component.text()
					.append(
						Component.text(
							String.valueOf(result.priceAmount)
						).color(NamedTextColor.YELLOW)
					)
					.append(Component.text("×").color(NamedTextColor.WHITE))
					.append(
						Component.text(
							formatItemName(result.priceItem.getType().name())
						).color(NamedTextColor.AQUA)
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
