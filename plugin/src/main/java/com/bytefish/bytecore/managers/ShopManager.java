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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
	private static final double DISPLAY_ITEM_CLEANUP_RADIUS = 0.5;
	private final Set<Location> processingLocations =
		Collections.synchronizedSet(new HashSet<>());
	private final Object displayLock = new Object();
	private final Set<String> activeChunkKeys = Collections.synchronizedSet(
		new HashSet<>()
	);
	private static final int VIEW_DISTANCE = 48;
	private final Map<Player, Set<Location>> playerTracking =
		new ConcurrentHashMap<>();

	public ShopManager(ByteCore plugin, ConfigManager config) {
		this.plugin = plugin;
		this.config = config;
		this.shopsFile = new File(plugin.getDataFolder(), "shops.json");
		this.gson = createGsonInstance();
		loadShops();

		if (config.isDisplayItemsEnabled()) {
			startFloatingAnimation();
			// Wait longer before initial recreation
			plugin
				.getServer()
				.getScheduler()
				.runTaskLater(
					plugin,
					() -> {
						cleanupDisplayItems();
						plugin
							.getServer()
							.getScheduler()
							.runTaskLater(
								plugin,
								this::recreateAllDisplayItems,
								20L
							);
					},
					40L
				);
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

	private boolean isDisplayItem(Item item) {
		return (
			item.hasMetadata(SHOP_DISPLAY_METADATA) ||
			(!item.hasGravity() && item.getPickupDelay() == Integer.MAX_VALUE)
		);
	}

	public void recreateAllDisplayItems() {
		synchronized (displayLock) {
			plugin
				.getLogger()
				.info("Starting recreation of all display items...");

			// First, clean up but don't remove valid shop display items
			cleanupDisplayItems();

			// Recreate display items for all shops
			new HashMap<>(shops).forEach((location, shop) -> {
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

					// Add a small delay between creations to prevent interference
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			});
		}
	}

	public boolean verifyShopSign(Location shopLocation) {
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
			Block attachedBlock = relative.getRelative(
				wallSign.getFacing().getOppositeFace()
			);
			if (
				!attachedBlock.getType().isSolid() ||
				!attachedBlock.equals(container)
			) {
				continue;
			}

			String firstLine = textSerializer.serialize(sign.line(0));
			if (firstLine != null && firstLine.equalsIgnoreCase("Selling")) {
				return true;
			}
		}
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

			if (!shops.containsKey(shop.getLocation())) {
				buyer.sendMessage(
					Component.text("This shop no longer exists!").color(
						NamedTextColor.RED
					)
				);
				return null;
			}

			Block block = shop.getLocation().getBlock();
			if (!(block.getState() instanceof Container container)) {
				buyer.sendMessage(
					Component.text("This shop's container is missing!").color(
						NamedTextColor.RED
					)
				);
				return null;
			}

			ShopTransaction transaction = new ShopTransaction(
				shop,
				buyer,
				shop.getSellingItem(),
				shop.getPriceItem()
			);

			// Log inventory verification
			if (!verifyInventories(buyer, container)) {
				transaction.fail("Invalid inventory state");
				buyer.sendMessage(
					Component.text("Your inventory state is invalid!").color(
						NamedTextColor.RED
					)
				);
				return transaction;
			}

			// Log shop state verification
			if (!verifyShopState(shop, container, transaction)) {
				return transaction;
			}

			// Log buyer inventory verification
			if (!verifyBuyerInventory(buyer, shop, transaction)) {
				return transaction;
			}

			// Execute the transaction
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
		} catch (InterruptedException e) {
			plugin
				.getLogger()
				.warning("Shop transaction interrupted: " + e.getMessage());
			return null;
		} finally {
			lock.unlock();
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

	public boolean verifyBuyerInventory(
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

	public boolean hasItems(Inventory inventory, ItemStack item, int amount) {
		int count = 0;
		for (ItemStack stack : inventory.getContents()) {
			if (stack != null && stack.isSimilar(item)) {
				count += stack.getAmount();
				if (count >= amount) return true;
			}
		}
		return false;
	}

	public boolean hasStock(Inventory inventory, ItemStack item, int amount) {
		return countItems(inventory, item) >= amount;
	}

	public boolean hasSpace(Inventory inventory, ItemStack item, int amount) {
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

	public boolean removeItems(
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
				synchronized (displayLock) {
					displayItems.forEach((loc, item) -> {
						if (item != null && !item.isDead() && item.isValid()) {
							// Only update if chunk is loaded and shop exists
							if (
								loc.getChunk().isLoaded() &&
								shops.containsKey(loc)
							) {
								Location displayLoc = getDisplayLocation(
									loc,
									tick
								);
								// Only teleport if position has changed significantly
								if (
									item
										.getLocation()
										.distanceSquared(displayLoc) >
									0.01
								) {
									item.teleport(displayLoc);
								}
							} else {
								// Remove if chunk unloaded or shop no longer exists
								item.remove();
								displayItems.remove(loc);
							}
						} else {
							displayItems.remove(loc);
						}
					});
				}
			}
		}
			.runTaskTimer(plugin, 1L, 2L);
	}

	private Location getDisplayLocation(Location shopLoc, double tick) {
		// Using block center coordinates for precise positioning
		return new Location(
			shopLoc.getWorld(),
			shopLoc.getBlockX() + 0.5,
			shopLoc.getBlockY() +
			config.getDisplayItemHeight() +
			(Math.sin(tick * config.getDisplayItemFrequency()) *
				config.getDisplayItemAmplitude() *
				0.5),
			shopLoc.getBlockZ() + 0.5
		);
	}

	private void createDisplayItem(Shop shop, @Nullable Player owner) {
		if (!config.isDisplayItemsEnabled()) {
			return;
		}

		Location loc = shop.getLocation();
		if (loc.getWorld() == null || !loc.getChunk().isLoaded()) {
			return;
		}

		if (!processingLocations.add(loc)) {
			return;
		}

		try {
			synchronized (displayLock) {
				if (!shops.containsKey(loc)) {
					return;
				}

				removeDisplayItem(loc);

				Location spawnLoc = getDisplayLocation(loc, 0);
				Item item = loc
					.getWorld()
					.dropItem(spawnLoc, shop.getSellingItem().clone());

				// Configure item with optimized settings
				item.setPickupDelay(Integer.MAX_VALUE);
				item.setPersistent(true);
				item.setVelocity(new Vector(0, 0, 0));
				item.setGravity(false);
				item.setCustomNameVisible(false); // Hide nametag
				item.setGlowing(false); // Ensure glowing is off
				item.setInvulnerable(true); // Prevent damage

				// Add metadata with shop ID
				item.setMetadata(
					SHOP_DISPLAY_METADATA,
					new FixedMetadataValue(plugin, shop.getId().toString())
				);

				// Ensure exact positioning
				item.teleport(spawnLoc);

				displayItems.put(loc, item);
			}
		} finally {
			processingLocations.remove(loc);
		}
	}

	public void cleanupDisplayItems() {
		synchronized (displayLock) {
			// Remove tracked items
			new HashSet<>(displayItems.values()).forEach(item -> {
				if (item != null && !item.isDead()) {
					item.remove();
				}
			});
			displayItems.clear();
			processingLocations.clear();

			// Clean up any orphaned display items
			for (World world : plugin.getServer().getWorlds()) {
				for (Chunk chunk : world.getLoadedChunks()) {
					for (Entity entity : chunk.getEntities()) {
						if (
							entity instanceof Item item && isDisplayItem(item)
						) {
							// Check if this item is at a valid shop location
							Location itemLoc = item.getLocation();
							Location blockLoc = itemLoc
								.getBlock()
								.getLocation();

							// Only remove if it's not at a valid shop location
							if (!shops.containsKey(blockLoc)) {
								entity.remove();
							}
						}
					}
				}
			}
		}
	}

	private void removeDisplayItem(Location loc) {
		synchronized (displayLock) {
			// Remove from our tracking map
			Item trackedItem = displayItems.remove(loc);
			if (trackedItem != null && !trackedItem.isDead()) {
				trackedItem.remove();
			}

			// Only remove items that are EXACTLY at our display position
			if (loc.getWorld() != null) {
				Location exactDisplayLoc = getDisplayLocation(loc, 0);
				loc
					.getWorld()
					.getNearbyEntities(
						exactDisplayLoc,
						0.1,
						0.1,
						0.1,
						entity ->
							entity instanceof Item &&
							(entity.hasMetadata(SHOP_DISPLAY_METADATA) ||
								(!((Item) entity).hasGravity() &&
									((Item) entity).getPickupDelay() ==
									Integer.MAX_VALUE))
					)
					.forEach(Entity::remove);
			}
		}
	}

	private void ensureAllShopsHaveDisplayItems() {
		plugin
			.getLogger()
			.info("Checking all shops for missing display items...");

		shops.forEach((location, shop) -> {
			if (location.getWorld() != null && location.getChunk().isLoaded()) {
				// Check if this shop already has a valid display item
				Item existingItem = displayItems.get(location);
				boolean needsNewItem =
					existingItem == null || existingItem.isDead();

				if (!needsNewItem) {
					// Verify the existing item is still in the correct position
					Location itemLoc = existingItem.getLocation();
					Location expectedLoc = location
						.clone()
						.add(0.5, config.getDisplayItemHeight(), 0.5);
					if (itemLoc.distanceSquared(expectedLoc) > 0.25) { // If item has moved more than 0.5 blocks
						needsNewItem = true;
					}
				}

				if (needsNewItem) {
					plugin
						.getLogger()
						.info(
							"Recreating missing display item for shop at: " +
							location
						);
					removeDisplayItem(location);
					createDisplayItem(shop, null);
				}
			}
		});
	}

	public void startDisplayItemMaintenanceTask() {
		// Main display maintenance task - runs less frequently
		plugin
			.getServer()
			.getScheduler()
			.runTaskTimer(
				plugin,
				() -> {
					synchronized (displayLock) {
						// Update displays only in loaded chunks with players nearby
						for (Player player : plugin
							.getServer()
							.getOnlinePlayers()) {
							updateDisplaysForPlayer(player);
						}
					}
				},
				100L,
				100L
			);

		// Item position update task - more frequent but lighter
		new BukkitRunnable() {
			double tick = 0;

			@Override
			public void run() {
				tick += 0.05;
				synchronized (displayLock) {
					displayItems.forEach((loc, item) -> {
						if (item != null && !item.isDead() && item.isValid()) {
							Location displayLoc = getDisplayLocation(loc, tick);
							if (
								item.getLocation().distanceSquared(displayLoc) >
								0.01
							) {
								item.teleport(displayLoc);
							}
						}
					});
				}
			}
		}
			.runTaskTimer(plugin, 1L, 2L);

		// Register the listener
		plugin
			.getServer()
			.getPluginManager()
			.registerEvents(new ShopDisplayListener(), plugin);
	}

	private void updateDisplaysForPlayer(Player player) {
		Location playerLoc = player.getLocation();
		Set<Location> visibleShops = playerTracking.computeIfAbsent(player, k ->
			new HashSet<>()
		);
		Set<Location> newVisibleShops = new HashSet<>();

		// Check all shops in loaded chunks
		shops.forEach((location, shop) -> {
			if (
				location.getWorld().equals(player.getWorld()) &&
				location
					.getWorld()
					.isChunkLoaded(
						location.getBlockX() >> 4,
						location.getBlockZ() >> 4
					)
			) {
				double distance = playerLoc.distanceSquared(location);
				if (distance <= VIEW_DISTANCE * VIEW_DISTANCE) {
					newVisibleShops.add(location);
					if (!visibleShops.contains(location)) {
						// Shop just became visible
						createDisplayItem(shop, null);
					}
				}
			}
		});

		// Remove displays that are now out of range
		visibleShops.forEach(loc -> {
			if (!newVisibleShops.contains(loc)) {
				removeDisplayItem(loc);
			}
		});

		visibleShops.clear();
		visibleShops.addAll(newVisibleShops);
	}

	public void handleChunkLoad(Chunk chunk) {
		if (!config.isDisplayItemsEnabled()) return;

		plugin
			.getServer()
			.getScheduler()
			.runTaskLater(
				plugin,
				() -> {
					// Only create displays for players within view distance
					for (Player player : chunk.getWorld().getPlayers()) {
						if (
							Math.abs(
									player.getLocation().getChunk().getX() -
									chunk.getX()
								) <=
								3 &&
							Math.abs(
								player.getLocation().getChunk().getZ() -
								chunk.getZ()
							) <=
							3
						) {
							updateDisplaysForPlayer(player);
						}
					}
				},
				20L
			);
	}

	private class ShopDisplayListener implements Listener {

		@EventHandler
		public void onPlayerMove(PlayerMoveEvent event) {
			if (event.hasChangedBlock()) {
				updateDisplaysForPlayer(event.getPlayer());
			}
		}

		@EventHandler
		public void onPlayerJoin(PlayerJoinEvent event) {
			playerTracking.put(event.getPlayer(), new HashSet<>());
		}

		@EventHandler
		public void onPlayerQuit(PlayerQuitEvent event) {
			playerTracking.remove(event.getPlayer());
		}
	}

	private boolean isItemInCorrectPosition(Item item, Location shopLoc) {
		Location expectedLoc = getDisplayLocation(shopLoc, 0);
		return item.getLocation().distanceSquared(expectedLoc) <= 0.01; // Very precise check
	}

	public void cleanup() {
		synchronized (displayLock) {
			cleanupDisplayItems();
			playerTracking.clear();
		}
		saveAll();
	}
}
