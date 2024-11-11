package com.bytefish.bytecore.models;

import com.google.gson.annotations.Expose;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Shop {

	@Expose
	private final UUID id;

	@Expose
	private final Location location;

	@Expose
	private final UUID ownerUUID;

	@Expose
	private final String ownerName;

	@Expose
	private final ItemStack sellingItem;

	@Expose
	private final int sellingAmount;

	@Expose
	private final ItemStack priceItem;

	@Expose
	private final int priceAmount;

	@Expose
	private final long creationTime;

	// Transient fields - won't be serialized
	private transient boolean isValid;
	private transient String lastError;

	public Shop(
		Location location,
		UUID ownerUUID,
		String ownerName,
		ItemStack sellingItem,
		int sellingAmount,
		ItemStack priceItem,
		int priceAmount
	) {
		this(
			UUID.randomUUID(),
			location,
			ownerUUID,
			ownerName,
			sellingItem,
			sellingAmount,
			priceItem,
			priceAmount,
			System.currentTimeMillis()
		);
	}

	public Shop(
		UUID id,
		Location location,
		UUID ownerUUID,
		String ownerName,
		ItemStack sellingItem,
		int sellingAmount,
		ItemStack priceItem,
		int priceAmount,
		long creationTime
	) {
		this.id = id;
		this.location = location;
		this.ownerUUID = ownerUUID;
		this.ownerName = ownerName;
		this.sellingItem = sellingItem.clone();
		this.sellingAmount = sellingAmount;
		this.priceItem = priceItem.clone();
		this.priceAmount = priceAmount;
		this.creationTime = creationTime;
		this.isValid = true;
	}

	public UUID getId() {
		return id;
	}

	public Location getLocation() {
		return location.clone();
	}

	public UUID getOwnerUUID() {
		return ownerUUID;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public ItemStack getSellingItem() {
		return sellingItem.clone();
	}

	public int getSellingAmount() {
		return sellingAmount;
	}

	public ItemStack getPriceItem() {
		return priceItem.clone();
	}

	public int getPriceAmount() {
		return priceAmount;
	}

	public long getCreationTime() {
		return creationTime;
	}

	public boolean isValid() {
		return (
			isValid &&
			sellingAmount > 0 &&
			sellingAmount <= sellingItem.getMaxStackSize() &&
			priceAmount > 0 &&
			priceAmount <= priceItem.getMaxStackSize()
		);
	}

	public void setValid(boolean valid) {
		this.isValid = valid;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String error) {
		this.lastError = error;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Shop shop = (Shop) o;
		return id.equals(shop.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
