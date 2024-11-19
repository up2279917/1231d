package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.config.ConfigManager;
import com.bytefish.bytecore.managers.ShopManager;
import com.bytefish.bytecore.models.Shop;
import com.bytefish.bytecore.util.ShopUtils;
import com.bytefish.bytecore.util.StringUtils;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.geysermc.floodgate.api.FloodgateApi;

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

		processShopCreation(event, attachedBlock);
	}

	private boolean isBedrockPlayer(Player player) {
		try {
			return FloodgateApi.getInstance()
				.isFloodgatePlayer(player.getUniqueId());
		} catch (Exception e) {
			return false;
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

			ItemStack sellingItem = getMaterialFromInput(
				sellingParts[1].trim(),
				event.getBlock()
			);
			if (sellingItem == null) {
				throw new IllegalArgumentException("Invalid selling item");
			}
			sellingItem.setAmount(sellingAmount);

			ItemStack priceItem = getMaterialFromInput(
				priceParts[1].trim(),
				event.getBlock()
			);
			if (priceItem == null) {
				throw new IllegalArgumentException("Invalid price item");
			}
			priceItem.setAmount(priceAmount);

			return new ShopParseResult(
				sellingItem,
				sellingAmount,
				priceItem,
				priceAmount
			);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid number format");
		}
	}

	private ItemStack getMaterialFromInput(String input, Block signBlock) {
		try {
			// Try parsing as an ID first
			int itemId = Integer.parseInt(input);
			Material material = Material.values()[itemId];
			if (material != null) {
				ItemStack item = new ItemStack(material);

				// Check container for enchanted version
				Block container = getAttachedBlock(signBlock);
				if (
					container != null &&
					container.getState() instanceof Container
				) {
					Container chest = (Container) container.getState();
					for (ItemStack containerItem : chest
						.getInventory()
						.getContents()) {
						if (
							containerItem != null &&
							containerItem.getType() == material
						) {
							if (
								!containerItem.getEnchantments().isEmpty() ||
								(containerItem.getType() ==
										Material.ENCHANTED_BOOK &&
									containerItem.getItemMeta() instanceof
									EnchantmentStorageMeta meta &&
									!meta.getStoredEnchants().isEmpty())
							) {
								return containerItem.clone();
							}
						}
					}
				}
				return item;
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			// If not a number, try as material name
			Material material = Material.matchMaterial(input.toUpperCase());
			if (material != null) {
				ItemStack item = new ItemStack(material);

				// Check container for enchanted version
				Block container = getAttachedBlock(signBlock);
				if (
					container != null &&
					container.getState() instanceof Container
				) {
					Container chest = (Container) container.getState();
					for (ItemStack containerItem : chest
						.getInventory()
						.getContents()) {
						if (
							containerItem != null &&
							containerItem.getType() == material
						) {
							if (
								!containerItem.getEnchantments().isEmpty() ||
								(containerItem.getType() ==
										Material.ENCHANTED_BOOK &&
									containerItem.getItemMeta() instanceof
									EnchantmentStorageMeta meta &&
									!meta.getStoredEnchants().isEmpty())
							) {
								return containerItem.clone();
							}
						}
					}
				}
				return item;
			}
		}
		throw new IllegalArgumentException("Invalid item: " + input);
	}

	private void formatShopSign(
		SignChangeEvent event,
		ItemStack sellingItem,
		int sellingAmount,
		ItemStack priceItem,
		int priceAmount
	) {
		Player player = event.getPlayer();
		boolean isBedrock = false;
		try {
			isBedrock = FloodgateApi.getInstance()
				.isFloodgatePlayer(player.getUniqueId());
		} catch (Exception e) {
			// FloodgateApi not available
		}

		// Header
		event.line(
			0,
			Component.text("Selling")
				.color(NamedTextColor.GOLD)
				.decorate(TextDecoration.BOLD)
		);

		String itemName = StringUtils.formatItemName(sellingItem.getType(), 1);
		Component sellingLine;

		if (isBedrock) {
			sellingLine = Component.text()
				.append(
					Component.text(sellingAmount + "×", NamedTextColor.YELLOW)
				)
				.append(Component.text(itemName, NamedTextColor.AQUA))
				.build();
		} else {
			sellingLine = Component.text()
				.append(
					Component.text(sellingAmount + "×", NamedTextColor.YELLOW)
				)
				.append(Component.text(itemName, NamedTextColor.AQUA))
				.build();
		}
		event.line(1, sellingLine);

		event.line(
			2,
			Component.text("For")
				.color(NamedTextColor.GOLD)
				.decorate(TextDecoration.BOLD)
		);

		// Price line
		String priceItemName = StringUtils.formatItemName(
			priceItem.getType(),
			1
		);
		Component priceLine;
		if (isBedrock) {
			priceLine = Component.text()
				.append(
					Component.text(priceAmount + "×", NamedTextColor.YELLOW)
				)
				.append(Component.text(priceItemName, NamedTextColor.AQUA))
				.build();
		} else {
			priceLine = Component.text()
				.append(
					Component.text(priceAmount + "×", NamedTextColor.YELLOW)
				)
				.append(Component.text(priceItemName, NamedTextColor.AQUA))
				.build();
		}
		event.line(3, priceLine);
	}

	private Component formatItemComponent(ItemStack item, int amount) {
		Component itemName;
		if (item.getType() == Material.ENCHANTED_BOOK) {
			EnchantmentStorageMeta meta =
				(EnchantmentStorageMeta) item.getItemMeta();
			if (meta != null && !meta.getStoredEnchants().isEmpty()) {
				Map.Entry<Enchantment, Integer> firstEnchant = meta
					.getStoredEnchants()
					.entrySet()
					.iterator()
					.next();
				String enchantName = ShopUtils.formatEnchantmentName(
					firstEnchant.getKey()
				);
				itemName = Component.text(enchantName + " Book");
			} else {
				itemName = Component.text("Enchanted Book");
			}
		} else {
			itemName = Component.text(
				StringUtils.formatItemName(item.getType(), amount)
			);
		}

		return Component.text()
			.append(Component.text(amount + "×").color(NamedTextColor.YELLOW))
			.append(itemName.color(NamedTextColor.AQUA))
			.build();
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

			// Selling item line with enchantment handling
			Component sellingLine;
			if (result.sellingItem.getType() == Material.ENCHANTED_BOOK) {
				EnchantmentStorageMeta meta =
					(EnchantmentStorageMeta) result.sellingItem.getItemMeta();
				if (meta != null && !meta.getStoredEnchants().isEmpty()) {
					Map.Entry<Enchantment, Integer> firstEnchant = meta
						.getStoredEnchants()
						.entrySet()
						.iterator()
						.next();
					sellingLine = Component.text()
						.append(
							Component.text(result.sellingAmount + "×").color(
								NamedTextColor.YELLOW
							)
						)
						.append(
							Component.text(
								ShopUtils.formatEnchantmentName(
									firstEnchant.getKey()
								) +
								" Book"
							).color(NamedTextColor.LIGHT_PURPLE)
						)
						.build();
				} else {
					sellingLine = Component.text()
						.append(
							Component.text(result.sellingAmount + "×").color(
								NamedTextColor.YELLOW
							)
						)
						.append(
							Component.text("Enchanted Book").color(
								NamedTextColor.AQUA
							)
						)
						.build();
				}
			} else {
				String itemName = StringUtils.formatItemName(
					result.sellingItem.getType(),
					1
				);
				sellingLine = Component.text()
					.append(
						Component.text(result.sellingAmount + "×").color(
							NamedTextColor.YELLOW
						)
					)
					.append(Component.text(itemName).color(NamedTextColor.AQUA))
					.build();
			}
			event.line(1, sellingLine);

			// "For" line
			event.line(
				2,
				Component.text("For")
					.color(NamedTextColor.GOLD)
					.decorate(TextDecoration.BOLD)
			);

			// Price item line
			Component priceLine = Component.text()
				.append(
					Component.text(result.priceAmount + "×").color(
						NamedTextColor.YELLOW
					)
				)
				.append(
					Component.text(
						StringUtils.formatItemName(
							result.priceItem.getType(),
							1
						)
					).color(NamedTextColor.AQUA)
				)
				.build();
			event.line(3, priceLine);

			player.sendMessage(
				Component.text("Shop created successfully!").color(
					NamedTextColor.GREEN
				)
			);
		}
	}
}
