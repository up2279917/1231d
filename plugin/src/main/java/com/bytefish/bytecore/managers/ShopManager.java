package com.bytefish.bytecore.managers;

import com.bytefish.bytecore.ByteCore;
import com.bytefish.bytecore.config.ConfigManager;
import com.bytefish.bytecore.models.Shop;
import com.bytefish.bytecore.models.ShopTransaction;
import com.bytefish.bytecore.util.ShopUtils;
import com.bytefish.bytecore.util.StringUtils;
import com.bytefish.bytecore.util.gson.TypeAdapters;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.geysermc.floodgate.api.FloodgateApi;
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
	private static final int VIEW_DISTANCE = 16; // Blocks, not chunks
	private final Object displayLock = new Object();
	private final Set<Location> processingLocations =
		Collections.synchronizedSet(new HashSet<>());

	private final Set<UUID> bedrockPlayers = ConcurrentHashMap.newKeySet();

	private record ChunkPosition(String world, int x, int z) {
		public boolean matches(Location loc) {
			return (
				loc.getWorld().getName().equals(world) &&
				(loc.getBlockX() >> 4) == x &&
				(loc.getBlockZ() >> 4) == z
			);
		}
	}

	private final Map<ChunkPosition, Set<Location>> chunkShopLocations =
		new ConcurrentHashMap<>();

	public ShopManager(ByteCore plugin, ConfigManager config) {
		this.plugin = plugin;
		this.config = config;
		this.shopsFile = new File(plugin.getDataFolder(), "shops.json");
		this.gson = createGsonInstance();
		loadShops();

		if (config.isDisplayItemsEnabled()) {
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
						startDisplayUpdateTask();
					},
					40L
				);
		}
	}

	public void startDisplayUpdateTask() {
		// Update display visibility every tick
		new BukkitRunnable() {
			@Override
			public void run() {
				for (Player player : plugin.getServer().getOnlinePlayers()) {
					updateDisplaysForPlayer(player);
				}
			}
		}
			.runTaskTimer(plugin, 1L, 1L);
	}

	private boolean isPlayerLookingAtDisplay(
		Player player,
		Location displayLoc
	) {
		Location eyeLoc = player.getEyeLocation();
		if (!eyeLoc.getWorld().equals(displayLoc.getWorld())) return false;

		double distanceSquared = eyeLoc.distanceSquared(displayLoc);
		if (distanceSquared > VIEW_DISTANCE * VIEW_DISTANCE) return false;

		Vector toDisplay = displayLoc.clone().subtract(eyeLoc).toVector();
		double angle = eyeLoc.getDirection().angle(toDisplay);
		if (angle > Math.PI / 4) return false; // 45-degree view angle

		// Wall check using ray trace
		RayTraceResult rayTrace = player
			.getWorld()
			.rayTraceBlocks(
				eyeLoc,
				toDisplay.normalize(),
				Math.sqrt(distanceSquared),
				FluidCollisionMode.NEVER,
				true
			);

		return rayTrace == null || rayTrace.getHitBlock() == null;
	}

	private void updateDisplaysForPlayer(Player player) {
		Location playerLoc = player.getLocation();
		final boolean isBedrock = isBedrockPlayer(player);
		final int viewDistance = isBedrock
			? Math.min(config.getDisplayViewDistance(), 32)
			: config.getDisplayViewDistance(); // Reduced for Bedrock

		displayItems.forEach((loc, item) -> {
			if (item != null && item.isValid() && !item.isDead()) {
				if (loc.getWorld().equals(playerLoc.getWorld())) {
					double distanceSquared = loc.distanceSquared(playerLoc);
					if (distanceSquared <= viewDistance * viewDistance) {
						// Only show name for enchanted items
						boolean isEnchanted = hasEnchantments(
							item.getItemStack()
						);
						if (!isEnchanted) {
							item.setCustomNameVisible(false);
							return;
						}

						// Line of sight check with improved performance
						boolean shouldShow = isPlayerLookingAtDisplayImproved(
							player,
							item.getLocation(),
							isBedrock
						);

						// Update visibility state only if needed
						if (item.isCustomNameVisible() != shouldShow) {
							item.setCustomNameVisible(shouldShow);
						}
					} else {
						item.setCustomNameVisible(false);
					}
				}
			}
		});
	}

	private boolean isBedrockPlayer(Player player) {
		try {
			return FloodgateApi.getInstance()
				.isFloodgatePlayer(player.getUniqueId());
		} catch (Exception e) {
			return false;
		}
	}

	private boolean isPlayerLookingAtDisplayImproved(
		Player player,
		Location displayLoc,
		boolean isBedrock
	) {
		Location eyeLoc = player.getEyeLocation();
		if (!eyeLoc.getWorld().equals(displayLoc.getWorld())) return false;

		double distanceSquared = eyeLoc.distanceSquared(displayLoc);

		// Adjusted view angle for Bedrock players
		double maxAngle = isBedrock ? Math.PI / 3 : Math.PI / 4; // 60 degrees for Bedrock, 45 for Java

		Vector toDisplay = displayLoc.clone().subtract(eyeLoc).toVector();
		double angle = eyeLoc.getDirection().angle(toDisplay);
		if (angle > maxAngle) return false;

		// Optimized ray trace with reduced precision for better performance
		RayTraceResult rayTrace = player
			.getWorld()
			.rayTraceBlocks(
				eyeLoc,
				toDisplay.normalize(),
				Math.sqrt(distanceSquared),
				FluidCollisionMode.NEVER,
				true
			);

		return rayTrace == null || rayTrace.getHitBlock() == null;
	}

	private void createDisplayItem(Shop shop, @Nullable Player owner) {
		Location loc = shop.getLocation();
		if (!processingLocations.add(loc)) return;

		try {
			synchronized (displayLock) {
				removeDisplayItem(loc);

				if (
					!loc
						.getWorld()
						.isChunkLoaded(
							loc.getBlockX() >> 4,
							loc.getBlockZ() >> 4
						)
				) {
					return;
				}

				// Track shop location in chunk
				ChunkPosition pos = new ChunkPosition(
					loc.getWorld().getName(),
					loc.getBlockX() >> 4,
					loc.getBlockZ() >> 4
				);
				chunkShopLocations
					.computeIfAbsent(pos, k -> ConcurrentHashMap.newKeySet())
					.add(loc);

				// Rest of the existing createDisplayItem code...
				Location spawnLoc = getDisplayLocation(loc);
				ItemStack displayItem = shop.getSellingItem().clone();
				displayItem.setAmount(1);

				Item item = loc.getWorld().dropItem(spawnLoc, displayItem);
				item.setCustomNameVisible(false);
				item.setGravity(false);
				item.setInvulnerable(true);
				item.setPickupDelay(Integer.MAX_VALUE);
				item.setPersistent(true);
				item.setVelocity(new Vector(0, 0, 0));

				item.setMetadata(
					SHOP_DISPLAY_METADATA,
					new FixedMetadataValue(plugin, shop.getId().toString())
				);

				if (hasEnchantments(displayItem)) {
					List<Component> enchantLore = ShopUtils.getEnchantmentLore(
						displayItem
					);
					if (!enchantLore.isEmpty()) {
						item.customName(
							Component.join(Component.text(" "), enchantLore)
						);
					}
				}

				displayItems.put(loc, item);
			}
		} finally {
			processingLocations.remove(loc);
		}
	}

	private Location getDisplayLocation(Location shopLoc) {
		return shopLoc.clone().add(0.5, config.getDisplayItemHeight(), 0.5);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		Chunk chunk = event.getChunk();
		plugin
			.getServer()
			.getScheduler()
			.runTaskLater(
				plugin,
				() -> {
					if (!chunk.isLoaded()) return;

					shops
						.entrySet()
						.stream()
						.filter(entry -> isInChunk(entry.getKey(), chunk))
						.forEach(entry ->
							createDisplayItem(entry.getValue(), null)
						);
				},
				2L
			);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkUnload(ChunkUnloadEvent event) {
		Chunk chunk = event.getChunk();
		shops
			.entrySet()
			.stream()
			.filter(entry -> isInChunk(entry.getKey(), chunk))
			.forEach(entry -> removeDisplayItem(entry.getKey()));
		ChunkPosition pos = new ChunkPosition(
			chunk.getWorld().getName(),
			chunk.getX(),
			chunk.getZ()
		);

		// Schedule a delayed check for Bedrock players
		plugin
			.getServer()
			.getScheduler()
			.runTaskLater(
				plugin,
				() -> {
					if (!chunk.isLoaded()) {
						Set<Location> shopLocs = chunkShopLocations.get(pos);
						if (shopLocs != null) {
							chunk
								.getWorld()
								.getPlayers()
								.stream()
								.filter(p ->
									bedrockPlayers.contains(p.getUniqueId())
								)
								.forEach(this::scheduleDisplayRefreshForPlayer);
						}
					}
				},
				20L
			); // 1 second delay
	}

	private boolean isInChunk(Location location, Chunk chunk) {
		return (
			location.getWorld().equals(chunk.getWorld()) &&
			(location.getBlockX() >> 4) == chunk.getX() &&
			(location.getBlockZ() >> 4) == chunk.getZ()
		);
	}

	private void removeDisplayItem(Location loc) {
		synchronized (displayLock) {
			// Remove from chunk tracking
			ChunkPosition pos = new ChunkPosition(
				loc.getWorld().getName(),
				loc.getBlockX() >> 4,
				loc.getBlockZ() >> 4
			);
			Set<Location> shopLocs = chunkShopLocations.get(pos);
			if (shopLocs != null) {
				shopLocs.remove(loc);
				if (shopLocs.isEmpty()) {
					chunkShopLocations.remove(pos);
				}
			}

			// Existing removal code
			Item item = displayItems.remove(loc);
			if (item != null && !item.isDead()) {
				item.remove();
			}

			if (loc.getWorld() != null) {
				loc
					.getWorld()
					.getNearbyEntities(
						getDisplayLocation(loc),
						0.5,
						0.5,
						0.5,
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

	public void recreateAllDisplayItems() {
		synchronized (displayLock) {
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
		}
	}

	public void cleanupDisplayItems() {
		synchronized (displayLock) {
			new HashSet<>(displayItems.values()).forEach(item -> {
				if (item != null && !item.isDead()) {
					item.remove();
				}
			});
			displayItems.clear();
			processingLocations.clear();

			// Clean up any stray display items
			plugin
				.getServer()
				.getWorlds()
				.forEach(world ->
					world
						.getEntities()
						.stream()
						.filter(entity -> entity instanceof Item)
						.map(entity -> (Item) entity)
						.filter(
							item ->
								!item.hasGravity() ||
								item.hasMetadata(SHOP_DISPLAY_METADATA)
						)
						.forEach(Entity::remove)
				);
		}
	}

	private boolean hasEnchantments(ItemStack item) {
		if (item == null) return false;

		if (item.getType() == Material.ENCHANTED_BOOK) {
			if (
				!(item.getItemMeta() instanceof EnchantmentStorageMeta meta)
			) return false;
			return !meta.getStoredEnchants().isEmpty();
		}

		return !item.getEnchantments().isEmpty();
	}

	// Shop Management Methods
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

	public void removeShop(Location location) {
		removeDisplayItem(location);
		shops.remove(location);
		shopLocks.remove(location);
		saveAll();
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

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerMove(PlayerMoveEvent event) {
		// Only check if they've moved to a new chunk
		Chunk fromChunk = event.getFrom().getChunk();
		Chunk toChunk = event.getTo().getChunk();

		if (
			fromChunk.getX() != toChunk.getX() ||
			fromChunk.getZ() != toChunk.getZ()
		) {
			Player player = event.getPlayer();
			Location loc = event.getTo();
			int chunkX = loc.getBlockX() >> 4;
			int chunkZ = loc.getBlockZ() >> 4;

			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					Chunk chunk = loc
						.getWorld()
						.getChunkAt(chunkX + dx, chunkZ + dz);
					if (chunk.isLoaded()) {
						// If the chunk is loaded, make sure the displays are created
						handleChunkDisplays(chunk);
					}
				}
			}
		}

		updateDisplaysForPlayer(event.getPlayer());
	}

	private void handleChunkDisplays(Chunk chunk) {
		shops
			.entrySet()
			.stream()
			.filter(entry -> isInChunk(entry.getKey(), chunk))
			.forEach(entry -> {
				if (displayItems.get(entry.getKey()) == null) {
					createDisplayItem(entry.getValue(), null);
				}
			});
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerTeleport(PlayerTeleportEvent event) {
		plugin
			.getServer()
			.getScheduler()
			.runTaskLater(
				plugin,
				() -> updateDisplaysForPlayer(event.getPlayer()),
				1L
			);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (isBedrockPlayer(player)) {
			bedrockPlayers.add(player.getUniqueId());
			scheduleDisplayRefreshForPlayer(player);
		}

		plugin
			.getServer()
			.getScheduler()
			.runTaskLater(
				plugin,
				() -> updateDisplaysForPlayer(event.getPlayer()),
				5L
			);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		bedrockPlayers.remove(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onBlockBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		Location loc = block.getLocation();

		if (shops.containsKey(loc)) {
			Shop shop = shops.get(loc);
			if (!event.getPlayer().getUniqueId().equals(shop.getOwnerUUID())) {
				event.setCancelled(true);
				return;
			}

			removeShop(loc);
			event
				.getPlayer()
				.sendMessage(
					Component.text("Shop removed successfully!").color(
						NamedTextColor.GREEN
					)
				);
		}
	}

	private void scheduleDisplayRefreshForPlayer(Player player) {
		if (!bedrockPlayers.contains(player.getUniqueId())) return;

		// Initial refresh
		refreshDisplaysForPlayer(player);

		// Schedule periodic refreshes while the player is online
		new BukkitRunnable() {
			private int count = 0;

			@Override
			public void run() {
				if (
					!player.isOnline() ||
					!bedrockPlayers.contains(player.getUniqueId())
				) {
					cancel();
					return;
				}

				// Full refresh every 30 seconds
				if (count++ % 30 == 0) {
					refreshDisplaysForPlayer(player);
				} else {
					// Quick visibility update
					updateDisplayVisibilityForPlayer(player);
				}
			}
		}
			.runTaskTimer(plugin, 20L, 20L); // Run every second
	}

	private void updateDisplayVisibilityForPlayer(Player player) {
		Location playerLoc = player.getLocation();
		int viewDistance = Math.min(config.getDisplayViewDistance(), 32);

		displayItems.forEach((loc, item) -> {
			if (item != null && item.isValid() && !item.isDead()) {
				if (loc.getWorld().equals(playerLoc.getWorld())) {
					double distanceSquared = loc.distanceSquared(playerLoc);
					if (distanceSquared <= viewDistance * viewDistance) {
						boolean isEnchanted = hasEnchantments(
							item.getItemStack()
						);
						boolean shouldShow =
							isEnchanted &&
							isPlayerLookingAtDisplayImproved(
								player,
								item.getLocation(),
								true
							);

						if (item.isCustomNameVisible() != shouldShow) {
							item.setCustomNameVisible(shouldShow);
							// Force position update
							item.teleport(item.getLocation());
						}
					} else {
						item.setCustomNameVisible(false);
					}
				}
			}
		});
	}

	private void refreshDisplaysForPlayer(Player player) {
		Location playerLoc = player.getLocation();
		int viewDistance = Math.min(config.getDisplayViewDistance(), 32);
		int chunkRadius = (viewDistance >> 4) + 1;

		int playerChunkX = playerLoc.getBlockX() >> 4;
		int playerChunkZ = playerLoc.getBlockZ() >> 4;

		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				ChunkPosition pos = new ChunkPosition(
					playerLoc.getWorld().getName(),
					playerChunkX + dx,
					playerChunkZ + dz
				);

				Set<Location> shopLocs = chunkShopLocations.get(pos);
				if (shopLocs != null) {
					shopLocs.forEach(loc -> {
						if (
							loc.distanceSquared(playerLoc) <=
							viewDistance * viewDistance
						) {
							recreateDisplayItem(loc);
						}
					});
				}
			}
		}
	}

	private void recreateDisplayItem(Location loc) {
		Shop shop = shops.get(loc);
		if (shop != null) {
			removeDisplayItem(loc);
			createDisplayItem(shop, null);
		}
	}

	// Shop Transaction Methods
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

			if (
				!verifyInventories(buyer, container, transaction) ||
				!executeTransaction(shop, buyer, container, transaction)
			) {
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

			Block attachedBlock = relative.getRelative(
				wallSign.getFacing().getOppositeFace()
			);
			if (
				!attachedBlock.getType().isSolid() ||
				!attachedBlock.equals(container)
			) {
				continue;
			}

			String firstLine = PlainTextComponentSerializer.plainText()
				.serialize(sign.line(0));
			if (firstLine != null && firstLine.equalsIgnoreCase("Selling")) {
				return true;
			}
		}
		return false;
	}

	private boolean verifyInventories(
		Player buyer,
		Container container,
		ShopTransaction transaction
	) {
		if (
			!buyer.isOnline() ||
			container.getInventory() == null ||
			buyer.getInventory() == null
		) {
			transaction.fail("Invalid inventory state");
			return false;
		}

		Shop shop = transaction.getShop();

		if (
			!hasStock(
				container.getInventory(),
				shop.getSellingItem(),
				shop.getSellingAmount()
			)
		) {
			transaction.fail("Shop is out of stock");
			buyer.sendMessage(
				Component.text("This shop is out of stock!").color(
					NamedTextColor.RED
				)
			);
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
			buyer.sendMessage(
				Component.text(
					"This shop is full and cannot accept payment!"
				).color(NamedTextColor.RED)
			);
			return false;
		}

		if (
			!hasItems(
				buyer.getInventory(),
				shop.getPriceItem(),
				shop.getPriceAmount()
			)
		) {
			transaction.fail("Insufficient payment items");
			buyer.sendMessage(
				Component.text("You don't have enough payment items!").color(
					NamedTextColor.RED
				)
			);
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
			buyer.sendMessage(
				Component.text("You don't have enough inventory space!").color(
					NamedTextColor.RED
				)
			);
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
		Inventory shopInv = container.getInventory();
		Inventory buyerInv = buyer.getInventory();

		// Remove items from shop
		if (
			!removeItems(
				shopInv,
				shop.getSellingItem(),
				shop.getSellingAmount()
			)
		) {
			transaction.fail("Failed to remove items from shop");
			return false;
		}

		// Remove payment from buyer
		if (
			!removeItems(buyerInv, shop.getPriceItem(), shop.getPriceAmount())
		) {
			// Revert shop inventory change
			ItemStack revertItem = shop.getSellingItem().clone();
			revertItem.setAmount(shop.getSellingAmount());
			shopInv.addItem(revertItem);
			transaction.fail("Failed to remove payment items");
			return false;
		}

		// Complete the transaction
		ItemStack sellingItems = shop.getSellingItem().clone();
		sellingItems.setAmount(shop.getSellingAmount());
		buyerInv.addItem(sellingItems);

		ItemStack paymentItems = shop.getPriceItem().clone();
		paymentItems.setAmount(shop.getPriceAmount());
		shopInv.addItem(paymentItems);

		return true;
	}

	// Inventory Utility Methods
	public boolean hasItems(Inventory inventory, ItemStack item, int amount) {
		int count = 0;
		for (ItemStack stack : inventory.getStorageContents()) {
			if (stack != null && stack.isSimilar(item)) {
				count += stack.getAmount();
				if (count >= amount) return true;
			}
		}
		return false;
	}

	public boolean hasStock(Inventory inventory, ItemStack item, int amount) {
		int count = 0;
		for (ItemStack stack : inventory.getStorageContents()) {
			if (stack != null && ShopUtils.areItemsEqual(stack, item)) {
				count += stack.getAmount();
				if (count >= amount) return true;
			}
		}
		return false;
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
		for (ItemStack stack : inventory.getStorageContents()) {
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

	public void handleChunkLoad(Chunk chunk) {
		if (!config.isDisplayItemsEnabled()) return;

		plugin
			.getServer()
			.getScheduler()
			.runTaskLater(
				plugin,
				() -> {
					if (!chunk.isLoaded()) return;

					shops
						.entrySet()
						.stream()
						.filter(entry -> isInChunk(entry.getKey(), chunk))
						.forEach(entry ->
							createDisplayItem(entry.getValue(), null)
						);
				},
				2L
			);
	}

	private boolean removeItems(
		Inventory inventory,
		ItemStack item,
		int amount
	) {
		int remaining = amount;
		ItemStack[] contents = inventory.getStorageContents();

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

	// Data Persistence Methods
	public synchronized void saveAll() {
		File tempFile = new File(plugin.getDataFolder(), "shops.json.tmp");
		File backupFile = new File(plugin.getDataFolder(), "shops.json.bak");

		try {
			plugin.getDataFolder().mkdirs();

			// Write to temporary file
			try (Writer writer = new FileWriter(tempFile)) {
				List<Shop> shopList = new ArrayList<>(shops.values());
				gson.toJson(shopList, writer);
			}

			// Create backup if main file exists
			if (shopsFile.exists()) {
				Files.copy(
					shopsFile.toPath(),
					backupFile.toPath(),
					StandardCopyOption.REPLACE_EXISTING
				);
			}

			// Move temp file to main location
			Files.move(
				tempFile.toPath(),
				shopsFile.toPath(),
				StandardCopyOption.REPLACE_EXISTING
			);
			backupFile.delete(); // Clean up backup if successful
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe("Failed to save shops: " + e.getMessage());
			// Attempt to restore from backup if save failed
			if (backupFile.exists() && !shopsFile.exists()) {
				try {
					Files.move(
						backupFile.toPath(),
						shopsFile.toPath(),
						StandardCopyOption.REPLACE_EXISTING
					);
				} catch (IOException restoreError) {
					plugin
						.getLogger()
						.severe(
							"Failed to restore backup: " +
							restoreError.getMessage()
						);
				}
			}
		} finally {
			tempFile.delete(); // Clean up temp file
		}
	}

	private void loadShops() {
		if (!shopsFile.exists()) {
			saveAll();
			return;
		}

		try (Reader reader = new FileReader(shopsFile)) {
			Type type = new TypeToken<List<Shop>>() {}.getType();
			List<Shop> loadedShops = gson.fromJson(reader, type);

			if (loadedShops != null) {
				shops.clear();
				shopLocks.clear();

				for (Shop shop : loadedShops) {
					shops.put(shop.getLocation(), shop);
					shopLocks.put(shop.getLocation(), new ReentrantLock());
				}
			}
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe("Failed to load shops: " + e.getMessage());
			// Try to load from backup
			File backupFile = new File(
				plugin.getDataFolder(),
				"shops.json.bak"
			);
			if (backupFile.exists()) {
				try (Reader reader = new FileReader(backupFile)) {
					Type type = new TypeToken<List<Shop>>() {}.getType();
					List<Shop> loadedShops = gson.fromJson(reader, type);
					if (loadedShops != null) {
						shops.clear();
						shopLocks.clear();
						loadedShops.forEach(shop -> {
							shops.put(shop.getLocation(), shop);
							shopLocks.put(
								shop.getLocation(),
								new ReentrantLock()
							);
						});
						saveAll(); // Save successful backup load as main file
					}
				} catch (IOException backupError) {
					plugin
						.getLogger()
						.severe(
							"Failed to load backup: " + backupError.getMessage()
						);
				}
			}
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

	public void cleanup() {
		synchronized (displayLock) {
			cleanupDisplayItems();
		}
		saveAll();
	}
}
