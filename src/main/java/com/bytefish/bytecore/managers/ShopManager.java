package com.bytefish.bytecore.managers;

import com.bytefish.bytecore.ByteCore;
import com.bytefish.bytecore.config.ConfigManager;
import com.bytefish.bytecore.models.Shop;
import com.bytefish.bytecore.models.ShopTransaction;
import com.bytefish.bytecore.util.gson.TypeAdapters;
import com.google.gson.*;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public class ShopManager {

	private final Map<Location, Shop> shops = new ConcurrentHashMap<>();
	private final Map<Location, ReentrantLock> shopLocks =
		new ConcurrentHashMap<>();
	private final Map<Location, Item> displayItems = new ConcurrentHashMap<>();
	private final ByteCore plugin;
	private final ConfigManager config;
	private final File shopsFile;
	private final Gson gson;
	private static final String SHOP_DISPLAY_METADATA = "shop_display";
	private final PlainTextComponentSerializer textSerializer =
		PlainTextComponentSerializer.plainText();

	public ShopManager(ByteCore plugin, ConfigManager config) {
		this.plugin = plugin;
		this.config = config;
		this.shopsFile = new File(plugin.getDataFolder(), "shops.json");
		this.gson = createGsonInstance();
		loadShops();

		if (config.isDisplayItemsEnabled()) {
			startFloatingAnimation();
			// Schedule recreation of display items after server is fully started
			plugin
				.getServer()
				.getScheduler()
				.runTaskLater(plugin, () -> recreateAllDisplayItems(), 20L);
		}
	}

	private Gson createGsonInstance() {
		return new GsonBuilder()
			.excludeFieldsWithoutExposeAnnotation()
			.registerTypeAdapter(
				Location.class,
				new TypeAdapters.LocationAdapter()
			)
			.registerTypeAdapter(
				ItemStack.class,
				new TypeAdapters.ItemStackAdapter()
			)
			.setPrettyPrinting()
			.create();
	}

	private static class OptionalTypeAdapter<T>
		implements JsonSerializer<Optional<T>>, JsonDeserializer<Optional<T>> {

		@Override
		public JsonElement serialize(
			Optional<T> src,
			Type typeOfSrc,
			JsonSerializationContext context
		) {
			return src.map(context::serialize).orElse(JsonNull.INSTANCE);
		}

		@Override
		public Optional<T> deserialize(
			JsonElement json,
			Type typeOfT,
			JsonDeserializationContext context
		) throws JsonParseException {
			return Optional.ofNullable(
				json.isJsonNull() ? null : context.deserialize(json, typeOfT)
			);
		}
	}

	public void recreateAllDisplayItems() {
		cleanupDisplayItems();

		shops.forEach((location, shop) -> {
			if (
				location.getWorld() != null &&
				location
					.getWorld()
					.isChunkLoaded(
						location.getBlockX() >> 4,
						location.getBlockZ() >> 4
					)
			) {
				createDisplayItem(shop, null);
			}
		});

		plugin
			.getLogger()
			.info(
				"Recreated display items for " + displayItems.size() + " shops"
			);
	}

	private boolean verifyShopSign(Location shopLocation) {
		Block container = shopLocation.getBlock();
		for (BlockFace face : new BlockFace[] {
			BlockFace.NORTH,
			BlockFace.SOUTH,
			BlockFace.EAST,
			BlockFace.WEST,
		}) {
			Block relative = container.getRelative(face);
			if (!(relative.getState() instanceof Sign sign)) {
				continue;
			}

			if (!(relative.getBlockData() instanceof WallSign wallSign)) {
				continue;
			}

			// Check if the sign is actually attached to our container
			if (wallSign.getFacing().getOppositeFace() != face) {
				continue;
			}

			// Get the first line text
			String firstLine = textSerializer.serialize(sign.line(0));
			plugin
				.getLogger()
				.info(
					"Checking shop sign at " +
					relative.getLocation() +
					", first line: " +
					firstLine
				); // Debug log

			// If we find a valid shop sign, return true
			if (firstLine != null && firstLine.equalsIgnoreCase("Selling")) {
				return true;
			}
		}

		// If we get here, no valid sign was found
		plugin
			.getLogger()
			.warning("No valid shop sign found at " + shopLocation);
		return false;
	}

	private void verifyShopIntegrity(Shop shop) {
		if (!verifyShopSign(shop.getLocation())) {
			plugin
				.getLogger()
				.warning(
					"Shop at " + shop.getLocation() + " is missing its sign!"
				);
		}
	}

	public Shop addShop(
		Location location,
		Player owner,
		ItemStack sellingItem,
		int sellingAmount,
		ItemStack priceItem,
		int priceAmount
	) {
		if (!isValidShopLocation(location)) {
			owner.sendMessage(
				Component.text("Invalid shop location!").color(
					NamedTextColor.RED
				)
			);
			return null;
		}

		Shop shop = new Shop(
			location,
			owner.getUniqueId(),
			owner.getName(),
			sellingItem,
			sellingAmount,
			priceItem,
			priceAmount
		);

		if (!shop.isValid()) {
			owner.sendMessage(
				Component.text("Invalid item amounts!").color(
					NamedTextColor.RED
				)
			);
			return null;
		}

		shops.put(location, shop);
		shopLocks.put(location, new ReentrantLock());
		createDisplayItem(shop, owner);
		saveAll();
		return shop;
	}

	public ShopTransaction processTransaction(Shop shop, Player buyer) {
		if (
			!buyer.isOnline() ||
			!shop
				.getLocation()
				.getWorld()
				.isChunkLoaded(
					shop.getLocation().getBlockX() >> 4,
					shop.getLocation().getBlockZ() >> 4
				)
		) {
			return null;
		}

		if (!verifyShopSign(shop.getLocation())) {
			buyer.sendMessage(
				Component.text("This shop's sign is missing!").color(
					NamedTextColor.RED
				)
			);
			return null;
		}

		ReentrantLock lock = shopLocks.computeIfAbsent(shop.getLocation(), k ->
			new ReentrantLock()
		);

		try {
			if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {
				return null;
			}

			try {
				if (!shops.containsKey(shop.getLocation())) {
					return null;
				}

				Block block = shop.getLocation().getBlock();
				if (!(block.getState() instanceof Container container)) {
					return null;
				}

				ShopTransaction transaction = new ShopTransaction(
					shop,
					buyer,
					shop.getSellingItem(),
					shop.getPriceItem()
				);

				if (!verifyInventories(buyer, container)) {
					transaction.fail("Invalid inventory state");
					return transaction;
				}

				if (!executeTransaction(shop, buyer, container, transaction)) {
					return transaction;
				}

				transaction.complete();
				buyer.sendMessage(
					Component.text("Purchase successful!").color(
						NamedTextColor.GREEN
					)
				);
				return transaction;
			} finally {
				lock.unlock();
			}
		} catch (InterruptedException e) {
			plugin
				.getLogger()
				.warning("Shop transaction interrupted: " + e.getMessage());
			return null;
		}
	}

	private boolean verifyInventories(Player buyer, Container container) {
		return (
			buyer.isOnline() &&
			container.getInventory() != null &&
			buyer.getInventory() != null
		);
	}

	private boolean verifyShopState(
		Shop shop,
		Container container,
		ShopTransaction transaction
	) {
		if (!shops.containsKey(shop.getLocation())) {
			transaction.fail("Shop no longer exists");
			return false;
		}

		if (
			!hasStock(
				container.getInventory(),
				shop.getSellingItem(),
				shop.getSellingAmount()
			)
		) {
			transaction.fail("Shop is out of stock");
			return false;
		}

		if (
			!hasSpace(
				container.getInventory(),
				shop.getPriceItem(),
				shop.getPriceAmount()
			)
		) {
			transaction.fail("Shop is full and cannot accept payment");
			return false;
		}

		return true;
	}

	private boolean verifyBuyerInventory(
		Player buyer,
		Shop shop,
		ShopTransaction transaction
	) {
		if (
			!hasItems(
				buyer.getInventory(),
				shop.getPriceItem(),
				shop.getPriceAmount()
			)
		) {
			transaction.fail("Insufficient payment items");
			return false;
		}

		if (
			!hasSpace(
				buyer.getInventory(),
				shop.getSellingItem(),
				shop.getSellingAmount()
			)
		) {
			transaction.fail("Insufficient inventory space");
			return false;
		}

		return true;
	}

	private boolean executeTransaction(
		Shop shop,
		Player buyer,
		Container container,
		ShopTransaction transaction
	) {
		if (
			!removeItems(
				container.getInventory(),
				shop.getSellingItem(),
				shop.getSellingAmount()
			)
		) {
			transaction.fail("Shop is out of stock");
			return false;
		}

		if (
			!removeItems(
				buyer.getInventory(),
				shop.getPriceItem(),
				shop.getPriceAmount()
			)
		) {
			// Revert the shop inventory change
			ItemStack revertItem = shop.getSellingItem().clone();
			revertItem.setAmount(shop.getSellingAmount());
			container.getInventory().addItem(revertItem);
			transaction.fail("Insufficient payment");
			return false;
		}

		// Complete the transaction
		ItemStack sellingItems = shop.getSellingItem().clone();
		sellingItems.setAmount(shop.getSellingAmount());
		buyer.getInventory().addItem(sellingItems);

		ItemStack paymentItems = shop.getPriceItem().clone();
		paymentItems.setAmount(shop.getPriceAmount());
		container.getInventory().addItem(paymentItems);

		return true;
	}

	private boolean hasItems(Inventory inventory, ItemStack item, int amount) {
		int count = 0;
		for (ItemStack stack : inventory.getContents()) {
			if (stack != null && stack.isSimilar(item)) {
				count += stack.getAmount();
				if (count >= amount) return true;
			}
		}
		return false;
	}

	private boolean hasStock(Inventory inventory, ItemStack item, int amount) {
		return countItems(inventory, item) >= amount;
	}

	private boolean hasSpace(Inventory inventory, ItemStack item, int amount) {
		return (
			inventory.firstEmpty() != -1 ||
			canStackWith(inventory, item, amount)
		);
	}

	private boolean canStackWith(
		Inventory inventory,
		ItemStack item,
		int amount
	) {
		for (ItemStack stack : inventory.getContents()) {
			if (
				stack != null &&
				stack.isSimilar(item) &&
				stack.getAmount() + amount <= stack.getMaxStackSize()
			) {
				return true;
			}
		}
		return false;
	}

	private int countItems(Inventory inventory, ItemStack item) {
		int count = 0;
		for (ItemStack stack : inventory.getContents()) {
			if (stack != null && stack.isSimilar(item)) {
				count += stack.getAmount();
			}
		}
		return count;
	}

	private boolean removeItems(
		Inventory inventory,
		ItemStack item,
		int amount
	) {
		int remaining = amount;
		ItemStack[] contents = inventory.getContents();

		for (int i = 0; i < contents.length && remaining > 0; i++) {
			ItemStack stack = contents[i];
			if (stack != null && stack.isSimilar(item)) {
				if (stack.getAmount() <= remaining) {
					remaining -= stack.getAmount();
					inventory.setItem(i, null);
				} else {
					stack.setAmount(stack.getAmount() - remaining);
					remaining = 0;
				}
			}
		}

		return remaining == 0;
	}

	public boolean isValidShopLocation(Location location) {
		return config.isValidShopContainer(location.getBlock().getType());
	}

	public Shop getShop(Location location) {
		return shops.get(location);
	}

	public boolean isShop(Location location) {
		return shops.containsKey(location);
	}

	public void removeShop(Location location) {
		removeDisplayItem(location);
		shops.remove(location);
		shopLocks.remove(location);
		saveAll();
	}

	private void loadShops() {
		File backupFile = new File(plugin.getDataFolder(), "shops.json.bak");

		// Try loading main file first
		if (shopsFile.exists()) {
			if (loadShopsFromFile(shopsFile)) {
				return;
			}
		}

		// If main file failed or doesn't exist, try backup
		if (backupFile.exists()) {
			if (loadShopsFromFile(backupFile)) {
				// If backup loaded successfully, save it as main file
				try {
					Files.copy(
						backupFile.toPath(),
						shopsFile.toPath(),
						StandardCopyOption.REPLACE_EXISTING
					);
				} catch (IOException e) {
					plugin
						.getLogger()
						.warning(
							"Failed to restore backup file: " + e.getMessage()
						);
				}
			}
		}
	}

	public synchronized void saveAll() {
		File tempFile = new File(plugin.getDataFolder(), "shops.json.tmp");
		File backupFile = new File(plugin.getDataFolder(), "shops.json.bak");
		File targetFile = shopsFile;

		try {
			// Ensure plugin directory exists
			plugin.getDataFolder().mkdirs();

			// Write to temporary file first
			try (Writer writer = new FileWriter(tempFile)) {
				List<Shop> shopList = new ArrayList<>(shops.values());
				gson.toJson(shopList, writer);
				writer.flush();
			}

			// If we have an existing file, create a backup
			if (targetFile.exists()) {
				Files.copy(
					targetFile.toPath(),
					backupFile.toPath(),
					StandardCopyOption.REPLACE_EXISTING
				);
			}

			// Move temporary file to target location
			Files.move(
				tempFile.toPath(),
				targetFile.toPath(),
				StandardCopyOption.REPLACE_EXISTING
			);

			// Delete backup file if everything succeeded
			if (backupFile.exists()) {
				backupFile.delete();
			}
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe("Failed to save shops: " + e.getMessage());
			e.printStackTrace();

			// If we failed and have a backup, try to restore it
			if (backupFile.exists() && !targetFile.exists()) {
				try {
					Files.move(
						backupFile.toPath(),
						targetFile.toPath(),
						StandardCopyOption.REPLACE_EXISTING
					);
				} catch (IOException restoreError) {
					plugin
						.getLogger()
						.severe(
							"Failed to restore shops backup: " +
							restoreError.getMessage()
						);
				}
			}
		} finally {
			// Clean up temporary file if it still exists
			if (tempFile.exists()) {
				tempFile.delete();
			}
		}
	}

	private boolean loadShopsFromFile(File file) {
		try (Reader reader = new FileReader(file)) {
			Type type = new TypeToken<List<Shop>>() {}.getType();
			List<Shop> loadedShops = gson.fromJson(reader, type);

			if (loadedShops != null) {
				shops.clear();
				shopLocks.clear();

				for (Shop shop : loadedShops) {
					// Verify shop integrity but still load it
					verifyShopIntegrity(shop);
					shops.put(shop.getLocation(), shop);
					shopLocks.put(shop.getLocation(), new ReentrantLock());
				}
				return true;
			}
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe(
					"Failed to load shops from " +
					file.getName() +
					": " +
					e.getMessage()
				);
		}
		return false;
	}

	private static class LocationAdapter
		implements JsonSerializer<Location>, JsonDeserializer<Location> {

		@Override
		public JsonElement serialize(
			Location location,
			Type type,
			JsonSerializationContext context
		) {
			JsonObject object = new JsonObject();
			object.addProperty("world", location.getWorld().getName());
			object.addProperty("x", location.getX());
			object.addProperty("y", location.getY());
			object.addProperty("z", location.getZ());
			return object;
		}

		@Override
		public Location deserialize(
			JsonElement element,
			Type type,
			JsonDeserializationContext context
		) throws JsonParseException {
			JsonObject object = element.getAsJsonObject();
			String worldName = object.get("world").getAsString();
			double x = object.get("x").getAsDouble();
			double y = object.get("y").getAsDouble();
			double z = object.get("z").getAsDouble();

			return new Location(org.bukkit.Bukkit.getWorld(worldName), x, y, z);
		}
	}

	private static class ItemStackAdapter
		implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {

		@Override
		public JsonElement serialize(
			ItemStack item,
			Type type,
			JsonSerializationContext context
		) {
			JsonObject object = new JsonObject();
			object.addProperty("type", item.getType().name());
			object.addProperty("amount", item.getAmount());

			return object;
		}

		@Override
		public ItemStack deserialize(
			JsonElement element,
			Type type,
			JsonDeserializationContext context
		) throws JsonParseException {
			JsonObject object = element.getAsJsonObject();
			String materialName = object.get("type").getAsString();
			int amount = object.get("amount").getAsInt();

			return new ItemStack(
				org.bukkit.Material.valueOf(materialName),
				amount
			);
		}
	}

	private static class ShopData {

		private final UUID id;
		private final LocationData location;
		private final UUID ownerUUID;
		private final String ownerName;
		private final ItemStackData sellingItem;
		private final int sellingAmount;
		private final ItemStackData priceItem;
		private final int priceAmount;
		private final long creationTime;

		public ShopData(Shop shop) {
			this.id = shop.getId();
			this.location = new LocationData(shop.getLocation());
			this.ownerUUID = shop.getOwnerUUID();
			this.ownerName = shop.getOwnerName();
			this.sellingItem = new ItemStackData(shop.getSellingItem());
			this.sellingAmount = shop.getSellingAmount();
			this.priceItem = new ItemStackData(shop.getPriceItem());
			this.priceAmount = shop.getPriceAmount();
			this.creationTime = shop.getCreationTime();
		}

		public Shop toShop() {
			Location loc = location.toLocation();
			ItemStack selling = sellingItem.toItemStack();
			ItemStack price = priceItem.toItemStack();
			return new Shop(
				id,
				loc,
				ownerUUID,
				ownerName,
				selling,
				sellingAmount,
				price,
				priceAmount,
				creationTime
			);
		}
	}

	private static class LocationData {

		private final String world;
		private final double x;
		private final double y;
		private final double z;

		public LocationData(Location loc) {
			this.world = loc.getWorld().getName();
			this.x = loc.getX();
			this.y = loc.getY();
			this.z = loc.getZ();
		}

		public Location toLocation() {
			return new Location(org.bukkit.Bukkit.getWorld(world), x, y, z);
		}
	}

	private static class ItemStackData {

		private final String type;
		private final int amount;

		public ItemStackData(ItemStack item) {
			this.type = item.getType().name();
			this.amount = item.getAmount();
		}

		public ItemStack toItemStack() {
			return new ItemStack(org.bukkit.Material.valueOf(type), amount);
		}
	}

	private void startFloatingAnimation() {
		new BukkitRunnable() {
			double tick = 0;

			@Override
			public void run() {
				tick += 0.05;
				displayItems.forEach((loc, item) -> {
					if (item != null && !item.isDead()) {
						double newY =
							loc.getY() +
							config.getDisplayItemHeight() +
							Math.sin(tick * config.getDisplayItemFrequency()) *
							config.getDisplayItemAmplitude();
						item.teleport(
							new Location(
								loc.getWorld(),
								loc.getX() + 0.5,
								newY,
								loc.getZ() + 0.5,
								item.getLocation().getYaw(),
								item.getLocation().getPitch()
							)
						);
					}
				});
			}
		}
			.runTaskTimer(plugin, 1L, 1L);
	}

	private void createDisplayItem(Shop shop, @Nullable Player owner) {
		if (!config.isDisplayItemsEnabled()) {
			return;
		}

		// If display items are enabled but op-only, and owner is specified and not op, skip display item
		if (owner != null && config.isDisplayItemsOpOnly() && !owner.isOp()) {
			return;
		}

		Location loc = shop.getLocation();
		if (loc.getWorld() == null) {
			plugin
				.getLogger()
				.warning(
					"Attempted to create display item in null world for shop: " +
					shop.getId()
				);
			return;
		}

		removeDisplayItem(loc);

		Item item = loc
			.getWorld()
			.dropItem(
				loc.clone().add(0.5, config.getDisplayItemHeight(), 0.5),
				shop.getSellingItem().clone()
			);

		item.setPickupDelay(Integer.MAX_VALUE);
		item.setPersistent(true);
		item.setVelocity(new Vector(0, 0, 0));
		item.setGravity(false);
		item.setGlowing(true);
		item.setMetadata(
			SHOP_DISPLAY_METADATA,
			new FixedMetadataValue(plugin, shop.getId().toString())
		);

		displayItems.put(loc, item);
	}

	private void cleanupDisplayItems() {
		displayItems
			.values()
			.forEach(item -> {
				if (item != null && !item.isDead()) {
					item.remove();
				}
			});
		displayItems.clear();

		for (World world : plugin.getServer().getWorlds()) {
			world
				.getEntitiesByClass(Item.class)
				.stream()
				.filter(item -> item.hasMetadata(SHOP_DISPLAY_METADATA))
				.forEach(Entity::remove);
		}
	}

	private void removeDisplayItem(Location loc) {
		Item item = displayItems.remove(loc);
		if (item != null && !item.isDead()) {
			item.remove();
		}
	}

	public void handleChunkLoad(Chunk chunk) {
		if (!config.isDisplayItemsEnabled()) return;

		// Get all shops in this chunk
		shops.forEach((location, shop) -> {
			if (
				location.getWorld().equals(chunk.getWorld()) &&
				(location.getBlockX() >> 4) == chunk.getX() &&
				(location.getBlockZ() >> 4) == chunk.getZ()
			) {
				// Remove any existing display item first
				removeDisplayItem(location);

				// Create new display item
				createDisplayItem(shop, null);
			}
		});
	}

	public void cleanup() {
		cleanupDisplayItems();
		saveAll();
	}
}
