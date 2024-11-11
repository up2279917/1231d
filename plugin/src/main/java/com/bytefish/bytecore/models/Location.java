package com.bytefish.bytecore.models;

import java.util.UUID;
import org.bukkit.World;

public class Location {

	private final UUID id;
	private final String name;
	private final String owner;
	private final double x;
	private final double y;
	private final double z;
	private final String world;
	private final long timestamp;
	private final String description;

	public Location(
		String name,
		String owner,
		double x,
		double y,
		double z,
		World world,
		String description
	) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.owner = owner;
		this.x = x;
		this.y = y;
		this.z = z;
		this.world = world.getName();
		this.timestamp = System.currentTimeMillis();
		this.description = description == null ? "" : description;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getOwner() {
		return owner;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getZ() {
		return z;
	}

	public String getWorld() {
		return world;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public String getDescription() {
		return description;
	}

	public org.bukkit.Location toBukkitLocation(org.bukkit.Server server) {
		World world = server.getWorld(this.world);
		if (world == null) return null;
		return new org.bukkit.Location(
			world,
			Math.floor(x) + 0.5,
			y,
			Math.floor(z) + 0.5
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Location location = (Location) o;
		return id.equals(location.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	public String getWorldLowerCase() {
		return world.toLowerCase();
	}
}
