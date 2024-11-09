package com.bytefish.bytecore.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class ShopDisplayProtectionListener implements Listener {

	private static final String SHOP_DISPLAY_METADATA = "shop_display";

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onItemPickup(EntityPickupItemEvent event) {
		if (event.getItem().hasMetadata(SHOP_DISPLAY_METADATA)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onItemDespawn(ItemDespawnEvent event) {
		if (event.getEntity().hasMetadata(SHOP_DISPLAY_METADATA)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityInteract(PlayerInteractEntityEvent event) {
		Entity entity = event.getRightClicked();
		if (
			entity instanceof Item && entity.hasMetadata(SHOP_DISPLAY_METADATA)
		) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityDamage(EntityDamageEvent event) {
		Entity entity = event.getEntity();
		if (
			entity instanceof Item && entity.hasMetadata(SHOP_DISPLAY_METADATA)
		) {
			event.setCancelled(true);
		}
	}
}
