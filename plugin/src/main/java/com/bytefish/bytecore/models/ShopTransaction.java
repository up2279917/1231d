package com.bytefish.bytecore.models;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ShopTransaction {

	private final UUID id;
	private final Shop shop;
	private final Player buyer;
	private final ItemStack itemsBought;
	private final ItemStack itemsPaid;
	private final long timestamp;
	private TransactionStatus status;
	private String failureReason;

	public enum TransactionStatus {
		PENDING,
		COMPLETED,
		FAILED,
		REVERSED,
	}

	public ShopTransaction(
		Shop shop,
		Player buyer,
		ItemStack itemsBought,
		ItemStack itemsPaid
	) {
		this.id = UUID.randomUUID();
		this.shop = shop;
		this.buyer = buyer;
		this.itemsBought = itemsBought.clone();
		this.itemsPaid = itemsPaid.clone();
		this.timestamp = System.currentTimeMillis();
		this.status = TransactionStatus.PENDING;
	}

	public UUID getId() {
		return id;
	}

	public Shop getShop() {
		return shop;
	}

	public Player getBuyer() {
		return buyer;
	}

	public ItemStack getItemsBought() {
		return itemsBought.clone();
	}

	public ItemStack getItemsPaid() {
		return itemsPaid.clone();
	}

	public long getTimestamp() {
		return timestamp;
	}

	public TransactionStatus getStatus() {
		return status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void complete() {
		this.status = TransactionStatus.COMPLETED;
	}

	public void fail(String reason) {
		this.status = TransactionStatus.FAILED;
		this.failureReason = reason;
	}

	public void reverse() {
		this.status = TransactionStatus.REVERSED;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ShopTransaction that = (ShopTransaction) o;
		return id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
