package com.bytefish.bytecore.util.gson;

import com.google.gson.*;
import java.lang.reflect.Type;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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
			return obj;
		}

		@Override
		public ItemStack deserialize(
			JsonElement json,
			Type type,
			JsonDeserializationContext context
		) throws JsonParseException {
			JsonObject obj = json.getAsJsonObject();
			return new ItemStack(
				Material.valueOf(obj.get("type").getAsString()),
				obj.get("amount").getAsInt()
			);
		}
	}
}
