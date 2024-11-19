package com.bytefish.bytecore.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class ShopUtils {

	/**
	 * Gets a display name for an item, handling enchanted items specially
	 */
	public static String getDisplayName(ItemStack item) {
		if (item.getType() == Material.ENCHANTED_BOOK) {
			EnchantmentStorageMeta meta =
				(EnchantmentStorageMeta) item.getItemMeta();
			if (meta != null && !meta.getStoredEnchants().isEmpty()) {
				Map.Entry<Enchantment, Integer> firstEnchant = meta
					.getStoredEnchants()
					.entrySet()
					.iterator()
					.next();
				return formatEnchantmentName(firstEnchant.getKey()) + " Book";
			}
			return "Enchanted Book";
		} else if (!item.getEnchantments().isEmpty()) {
			return (
				"Enchanted " +
				StringUtils.formatItemName(item.getType(), item.getAmount())
			);
		}
		return StringUtils.formatItemName(item.getType(), item.getAmount());
	}

	/**
	 * Formats an enchantment name for display
	 */
	public static String formatEnchantmentName(Enchantment enchantment) {
		return (
			enchantment
				.getKey()
				.getKey()
				.replace('_', ' ')
				.toLowerCase()
				.substring(0, 1)
				.toUpperCase() +
			enchantment
				.getKey()
				.getKey()
				.replace('_', ' ')
				.toLowerCase()
				.substring(1)
		);
	}

	/**
	 * Compares two items for equality, considering enchantments
	 */
	public static boolean areItemsEqual(ItemStack item1, ItemStack item2) {
		if (item1 == null || item2 == null) return false;
		if (item1.getType() != item2.getType()) return false;

		// Handle enchanted books specifically
		if (item1.getType() == Material.ENCHANTED_BOOK) {
			return compareEnchantedBooks(item1, item2);
		}

		// Compare regular items with enchantments
		return compareRegularItems(item1, item2);
	}

	private static boolean compareEnchantedBooks(
		ItemStack book1,
		ItemStack book2
	) {
		EnchantmentStorageMeta meta1 =
			(EnchantmentStorageMeta) book1.getItemMeta();
		EnchantmentStorageMeta meta2 =
			(EnchantmentStorageMeta) book2.getItemMeta();

		if (meta1 == null || meta2 == null) return false;

		Map<Enchantment, Integer> enchants1 = meta1.getStoredEnchants();
		Map<Enchantment, Integer> enchants2 = meta2.getStoredEnchants();

		return enchants1.equals(enchants2);
	}

	private static boolean compareRegularItems(
		ItemStack item1,
		ItemStack item2
	) {
		Map<Enchantment, Integer> enchants1 = item1.getEnchantments();
		Map<Enchantment, Integer> enchants2 = item2.getEnchantments();

		return enchants1.equals(enchants2);
	}

	/**
	 * Creates a list of Components for displaying enchantments
	 */
	public static List<Component> getEnchantmentLore(ItemStack item) {
		List<Component> lore = new ArrayList<>();

		if (item.getType() == Material.ENCHANTED_BOOK) {
			EnchantmentStorageMeta meta =
				(EnchantmentStorageMeta) item.getItemMeta();
			if (meta != null && !meta.getStoredEnchants().isEmpty()) {
				meta
					.getStoredEnchants()
					.forEach((ench, level) -> {
						lore.add(
							Component.text()
								.append(
									Component.text("• ", NamedTextColor.GRAY)
								)
								.append(
									Component.text(
										formatEnchantmentName(ench)
									).color(NamedTextColor.AQUA)
								)
								.append(Component.space())
								.append(
									Component.text(toRomanNumeral(level)).color(
										NamedTextColor.LIGHT_PURPLE
									)
								)
								.build()
						);
					});
			}
		} else if (!item.getEnchantments().isEmpty()) {
			item
				.getEnchantments()
				.forEach((ench, level) -> {
					lore.add(
						Component.text()
							.append(Component.text("• ", NamedTextColor.GRAY))
							.append(
								Component.text(
									formatEnchantmentName(ench)
								).color(NamedTextColor.AQUA)
							)
							.append(Component.space())
							.append(
								Component.text(toRomanNumeral(level)).color(
									NamedTextColor.LIGHT_PURPLE
								)
							)
							.build()
					);
				});
		}

		return lore;
	}

	/**
	 * Converts a number to Roman numerals
	 */
	private static String toRomanNumeral(int number) {
		String[] romanNumerals = {
			"I",
			"II",
			"III",
			"IV",
			"V",
			"VI",
			"VII",
			"VIII",
			"IX",
			"X",
		};
		return number > 0 && number <= romanNumerals.length
			? romanNumerals[number - 1]
			: String.valueOf(number);
	}
}
