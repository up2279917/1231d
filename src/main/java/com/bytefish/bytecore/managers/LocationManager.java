package com.bytefish.bytecore.managers;

import com.bytefish.bytecore.ByteCore;
import com.bytefish.bytecore.models.Location;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

public class LocationManager {

	private final ByteCore plugin;
	private final Map<UUID, Location> locations;
	private final Map<String, Set<UUID>> playerLocations;
	private final Map<String, UUID> locationNames;
	private final File locationsFile;
	private final Gson gson;
	private final PlainTextComponentSerializer textSerializer =
		PlainTextComponentSerializer.plainText();

	public LocationManager(ByteCore plugin) {
		this.plugin = plugin;
		this.locations = new ConcurrentHashMap<>();
		this.playerLocations = new ConcurrentHashMap<>();
		this.locationNames = new ConcurrentHashMap<>();
		this.locationsFile = new File(plugin.getDataFolder(), "locations.json");
		this.gson = new GsonBuilder().setPrettyPrinting().create();
		loadLocations();

		scheduleValidation();
	}

	private void scheduleValidation() {
		plugin
			.getServer()
			.getScheduler()
			.runTaskTimer(plugin, this::validateAllLocations, 6000L, 6000L);
	}

	public void validateAllLocations() {
		Set<UUID> toRemove = new HashSet<>();
		Map<String, Integer> removalStats = new HashMap<>();

		plugin.getLogger().info("Starting location validation...");
		plugin
			.getLogger()
			.info("Total locations before validation: " + locations.size());

		for (Location location : new ArrayList<>(locations.values())) {
			World world = plugin.getServer().getWorld(location.getWorld());
			if (world == null) {
				plugin
					.getLogger()
					.info("Invalid world for location: " + location.getName());
				trackRemoval(removalStats, "Invalid World");
				toRemove.add(location.getId());
				continue;
			}

			org.bukkit.Location checkLoc = new org.bukkit.Location(
				world,
				location.getX(),
				location.getY(),
				location.getZ()
			);

			Block block = checkLoc.getBlock();
			plugin
				.getLogger()
				.info(
					"Checking block at " +
					checkLoc +
					", type: " +
					block.getType()
				);

			if (block.getState() instanceof Sign sign) {
				String firstLineContent = textSerializer
					.serialize(sign.line(0))
					.trim();
				String secondLineContent = textSerializer
					.serialize(sign.line(1))
					.trim();

				plugin
					.getLogger()
					.info(
						"Sign content - Line 1: '" +
						firstLineContent +
						"', Line 2: '" +
						secondLineContent +
						"'"
					);

				if (
					firstLineContent.equalsIgnoreCase("location") &&
					secondLineContent.equalsIgnoreCase(location.getName())
				) {
					continue;
				}
			}

			plugin
				.getLogger()
				.info(
					"No valid sign found for location: " + location.getName()
				);
			plugin
				.getLogger()
				.info("Block type at location: " + block.getType());
			trackRemoval(removalStats, "Invalid Sign");
			toRemove.add(location.getId());
		}

		removeInvalidLocations(toRemove, removalStats);

		plugin
			.getLogger()
			.info(
				"Location validation complete. Remaining locations: " +
				locations.size()
			);
	}

	private void removeInvalidLocations(
		Set<UUID> toRemove,
		Map<String, Integer> removalStats
	) {
		for (UUID id : toRemove) {
			Location location = locations.remove(id);
			if (location != null) {
				playerLocations
					.getOrDefault(location.getOwner(), new HashSet<>())
					.remove(id);
				locationNames.remove(location.getName().toLowerCase());
			}
		}
		plugin
			.getLogger()
			.info("Removed " + toRemove.size() + " invalid locations:");
		removalStats.forEach((reason, count) ->
			plugin.getLogger().info("- " + reason + ": " + count)
		);
		saveAll();
	}

	private class ChunkLoadHelper implements AutoCloseable {

		private final World world;
		private final int chunkX, chunkZ;
		private final boolean wasLoaded;

		ChunkLoadHelper(World world, int chunkX, int chunkZ) {
			this.world = world;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.wasLoaded = world.isChunkLoaded(chunkX, chunkZ);
			if (!wasLoaded) {
				world.loadChunk(chunkX, chunkZ);
			}
		}

		@Override
		public void close() {
			if (!wasLoaded) {
				world.unloadChunkRequest(chunkX, chunkZ);
			}
		}
	}

	private static class ChunkCoordinate {

		final String worldName;
		final int x;
		final int z;

		ChunkCoordinate(Chunk chunk) {
			this.worldName = chunk.getWorld().getName();
			this.x = chunk.getX();
			this.z = chunk.getZ();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ChunkCoordinate that = (ChunkCoordinate) o;
			return (
				x == that.x && z == that.z && worldName.equals(that.worldName)
			);
		}

		@Override
		public int hashCode() {
			return Objects.hash(worldName, x, z);
		}
	}

	private void trackRemoval(Map<String, Integer> stats, String reason) {
		stats.merge(reason, 1, Integer::sum);
	}

	public Optional<Location> addLocation(
		String name,
		Player player,
		String description,
		World world,
		double x,
		double y,
		double z
	) {
		Location location = new Location(
			name,
			player.getUniqueId().toString(),
			x,
			y,
			z,
			world,
			description
		);

		locations.put(location.getId(), location);
		playerLocations
			.computeIfAbsent(player.getUniqueId().toString(), k ->
				new HashSet<>()
			)
			.add(location.getId());
		locationNames.put(name.toLowerCase(), location.getId()); // Ensure this line is executed
		saveAll();

		plugin
			.getLogger()
			.info(
				"Adding location: " + name + " at " + x + ", " + y + ", " + z
			);

		return Optional.of(location);
	}

	public boolean removeLocation(UUID locationId) {
		Location location = locations.get(locationId);
		if (location != null) {
			locations.remove(locationId);
			playerLocations
				.getOrDefault(location.getOwner(), new HashSet<>())
				.remove(locationId);
			locationNames.remove(location.getName().toLowerCase());
			return true;
		}
		return false;
	}

	public void validateLocationSigns() {
		Set<UUID> invalidLocations = new HashSet<>();
		Map<String, Integer> removalStats = new HashMap<>();

		for (Location location : locations.values()) {
			World world = plugin.getServer().getWorld(location.getWorld());
			if (world == null) {
				invalidLocations.add(location.getId());
				trackRemoval(removalStats, "Invalid World");
				continue;
			}

			org.bukkit.Location bukkitLoc = location.toBukkitLocation(
				plugin.getServer()
			);
			if (bukkitLoc == null) {
				invalidLocations.add(location.getId());
				trackRemoval(removalStats, "Invalid Location");
				continue;
			}

			if (
				!world.isChunkLoaded(
					bukkitLoc.getBlockX() >> 4,
					bukkitLoc.getBlockZ() >> 4
				)
			) {
				continue;
			}

			Block block = bukkitLoc.getBlock();
			if (block.getState() instanceof Sign sign) {
				String firstLine = textSerializer.serialize(sign.line(0));
				if (!firstLine.equalsIgnoreCase("Location")) {
					plugin
						.getLogger()
						.info(
							"Invalid sign content for location: " +
							location.getName()
						);
					trackRemoval(removalStats, "Invalid Sign Content");
					invalidLocations.add(location.getId());
				}
			} else {
				plugin
					.getLogger()
					.info(
						"No sign found for location: " +
						location.getName() +
						" at " +
						bukkitLoc
					);
				plugin
					.getLogger()
					.info("Block type at location: " + block.getType());
				trackRemoval(removalStats, "No Sign");
				invalidLocations.add(location.getId());
			}
		}

		invalidLocations.forEach(this::removeLocation);
		if (!invalidLocations.isEmpty()) {
			StringBuilder logMessage = new StringBuilder(
				"Removed " +
				invalidLocations.size() +
				" invalid location signs during validation:"
			);
			removalStats.forEach((reason, count) ->
				logMessage
					.append("\n- ")
					.append(reason)
					.append(": ")
					.append(count)
			);
			plugin.getLogger().info(logMessage.toString());
			saveAll();
		}
	}

	public Optional<Location> getLocationByName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return Optional.empty();
		}
		UUID locationId = locationNames.get(name.toLowerCase());

		if (locationId == null) {
			return Optional.empty();
		}

		return Optional.ofNullable(locations.get(locationId));
	}

	public Optional<Location> getLocation(UUID id) {
		return Optional.ofNullable(locations.get(id));
	}

	public List<Location> getPlayerLocations(String playerName) {
		return playerLocations
			.getOrDefault(playerName, new HashSet<>())
			.stream()
			.map(locations::get)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
	}

	public int getPlayerLocationCount(String playerUUID) {
		return playerLocations.getOrDefault(playerUUID, new HashSet<>()).size();
	}

	public List<Location> getAllLocations() {
		return new ArrayList<>(locations.values());
	}

	private boolean isChunkLoaded(World world, int x, int z) {
		return world.isChunkLoaded(x >> 4, z >> 4);
	}

	public void removeLocationAtPosition(
		double x,
		double y,
		double z,
		World world
	) {
		locations
			.values()
			.stream()
			.filter(
				loc ->
					loc.getWorld().equals(world.getName()) &&
					loc.getX() == x &&
					loc.getY() == y &&
					loc.getZ() == z
			)
			.findFirst()
			.ifPresent(location -> {
				locations.remove(location.getId());
				playerLocations
					.getOrDefault(location.getOwner(), new HashSet<>())
					.remove(location.getId());
				locationNames.remove(location.getName().toLowerCase());
				saveAll();
			});
	}

	private void loadLocations() {
		if (!locationsFile.exists()) {
			saveAll();
			return;
		}

		try (Reader reader = new FileReader(locationsFile)) {
			Type type = new TypeToken<List<Location>>() {}.getType();
			List<Location> loadedLocations = gson.fromJson(reader, type);

			if (loadedLocations != null) {
				locations.clear();
				playerLocations.clear();
				locationNames.clear();

				for (Location location : loadedLocations) {
					locations.put(location.getId(), location);
					playerLocations
						.computeIfAbsent(location.getOwner(), k ->
							new HashSet<>()
						)
						.add(location.getId());
					locationNames.put(
						location.getName().toLowerCase(),
						location.getId()
					);
				}
			}
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe("Failed to load locations: " + e.getMessage());
		}
	}

	public void validateLocations() {
		Set<UUID> invalidLocations = new HashSet<>();

		for (Location location : locations.values()) {
			World world = plugin
				.getServer()
				.getWorlds()
				.stream()
				.filter(w -> w.getName().equalsIgnoreCase(location.getWorld()))
				.findFirst()
				.orElse(null);
			if (world == null) {
				invalidLocations.add(location.getId());
				continue;
			}

			org.bukkit.Location bukkitLoc = location.toBukkitLocation(
				plugin.getServer()
			);
			if (
				bukkitLoc == null ||
				!world.isChunkLoaded(
					bukkitLoc.getBlockX() >> 4,
					bukkitLoc.getBlockZ() >> 4
				)
			) {
				continue;
			}
		}

		invalidLocations.forEach(this::removeLocation);
	}

	public void saveAll() {
		try {
			if (!locationsFile.exists()) {
				plugin.getDataFolder().mkdirs();
				locationsFile.createNewFile();
			}

			try (Writer writer = new FileWriter(locationsFile)) {
				gson.toJson(new ArrayList<>(locations.values()), writer);
			}
		} catch (IOException e) {
			plugin
				.getLogger()
				.severe("Failed to save locations: " + e.getMessage());
		}
	}

	public int getMaxLocationsPerPlayer() {
		return plugin.getConfigManager().getMaxLocationsPerPlayer();
	}
}
