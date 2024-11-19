package com.bytefish.bytecore.util.gson;

import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class TypeAdapters {

	public static class LocationAdapter
		implements JsonSerializer<Location>, JsonDeserializer<Location> {

		@Override
		public JsonElement serialize(
			Location loc,
			Type type,
			JsonSerializationContext context
		) {
			JsonObject obj = new JsonObject();
			obj.addProperty("world", loc.getWorld().getName());
			obj.addProperty("x", loc.getX());
			obj.addProperty("y", loc.getY());
			obj.addProperty("z", loc.getZ());
			return obj;
		}

		@Override
		public Location deserialize(
			JsonElement json,
			Type type,
			JsonDeserializationContext context
		) throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();
			return new Location(
				Bukkit.getWorld(obj.get("world").getAsString()),
				obj.get("x").getAsDouble(),
				obj.get("y").getAsDouble(),
				obj.get("z").getAsDouble()
			);
		}
	}

	public static class ItemStackAdapter
		implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

		@Override
		public JsonElement serialize(
			ItemStack item,
			Type type,
			JsonSerializationContext context
		) {
			JsonObject obj = new JsonObject();
			obj.addProperty("type", item.getType().name());
			obj.addProperty("amount", item.getAmount());

			if (
				item.getEnchantments().size() > 0 ||
				item.getType() == Material.ENCHANTED_BOOK
			) {
				JsonObject enchants = new JsonObject();

				if (item.getType() == Material.ENCHANTED_BOOK) {
					EnchantmentStorageMeta meta =
						(EnchantmentStorageMeta) item.getItemMeta();
					if (meta != null) {
						meta
							.getStoredEnchants()
							.forEach((ench, level) ->
								enchants.addProperty(
									ench.getKey().getKey(),
									level
								)
							);
					}
				} else {
					item
						.getEnchantments()
						.forEach((ench, level) ->
							enchants.addProperty(ench.getKey().getKey(), level)
						);
				}

				obj.add("enchantments", enchants);
			}

			ItemMeta meta = item.getItemMeta();
			if (meta != null && meta.hasDisplayName()) {
				obj.addProperty("displayName", meta.getDisplayName());
			}

			return obj;
		}

		@Override
		public ItemStack deserialize(
			JsonElement json,
			Type type,
			JsonDeserializationContext context
		) throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();
			Material material = Material.valueOf(obj.get("type").getAsString());
			int amount = obj.get("amount").getAsInt();

			ItemStack item = new ItemStack(material, amount);

			if (obj.has("enchantments")) {
				JsonObject enchants = obj.getAsJsonObject("enchantments");

				if (material == Material.ENCHANTED_BOOK) {
					EnchantmentStorageMeta meta =
						(EnchantmentStorageMeta) item.getItemMeta();
					if (meta != null) {
						enchants
							.entrySet()
							.forEach(entry -> {
								Enchantment ench = Enchantment.getByKey(
									org.bukkit.NamespacedKey.minecraft(
										entry.getKey()
									)
								);
								if (ench != null) {
									meta.addStoredEnchant(
										ench,
										entry.getValue().getAsInt(),
										true
									);
								}
							});
						item.setItemMeta(meta);
					}
				} else {
					enchants
						.entrySet()
						.forEach(entry -> {
							Enchantment ench = Enchantment.getByKey(
								org.bukkit.NamespacedKey.minecraft(
									entry.getKey()
								)
							);
							if (ench != null) {
								item.addUnsafeEnchantment(
									ench,
									entry.getValue().getAsInt()
								);
							}
						});
				}
			}

			if (obj.has("displayName")) {
				ItemMeta meta = item.getItemMeta();
				if (meta != null) {
					meta.setDisplayName(obj.get("displayName").getAsString());
					item.setItemMeta(meta);
				}
			}

			return item;
		}
	}
}
