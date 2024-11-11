package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.managers.ShopManager;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public class ShopProtectionListener implements Listener {

	private final ShopManager shopManager;
	private final Set<EntityType> protectedEntities;

	public ShopProtectionListener(ShopManager shopManager) {
		this.shopManager = shopManager;
		this.protectedEntities = new HashSet<>();
		initProtectedEntities();
	}

	private void initProtectedEntities() {
		protectedEntities.add(EntityType.ENDERMAN);
		protectedEntities.add(EntityType.WITHER);
		protectedEntities.add(EntityType.CREEPER);
		protectedEntities.add(EntityType.TNT);
		protectedEntities.add(EntityType.TNT_MINECART);
		protectedEntities.add(EntityType.WITHER_SKULL);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		event
			.blockList()
			.removeIf(block -> isProtectedBlock(block) || isShopSign(block));
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		event
			.blockList()
			.removeIf(block -> isProtectedBlock(block) || isShopSign(block));
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityChangeBlock(EntityChangeBlockEvent event) {
		if (
			isProtectedBlock(event.getBlock()) || isShopSign(event.getBlock())
		) {
			if (protectedEntities.contains(event.getEntityType())) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent event) {
		if (
			event
				.getBlocks()
				.stream()
				.anyMatch(block -> isProtectedBlock(block) || isShopSign(block))
		) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent event) {
		if (
			event
				.getBlocks()
				.stream()
				.anyMatch(block -> isProtectedBlock(block) || isShopSign(block))
		) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPhysics(BlockPhysicsEvent event) {
		Block block = event.getBlock();
		if (isShopSign(block)) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockFromTo(BlockFromToEvent event) {
		if (isShopSign(event.getToBlock())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInventoryMove(InventoryMoveItemEvent event) {
		if (
			event.getSource().getHolder() instanceof
				Container sourceContainer &&
			shopManager.isShop(sourceContainer.getLocation())
		) {
			event.setCancelled(true);
			return;
		}

		if (
			event.getDestination().getHolder() instanceof
				Container destContainer &&
			shopManager.isShop(destContainer.getLocation())
		) {
			event.setCancelled(true);
		}
	}

	private boolean isProtectedBlock(Block block) {
		return shopManager.isShop(block.getLocation());
	}

	private boolean isShopSign(Block block) {
		if (
			!(block.getState() instanceof Sign) ||
			!(block.getBlockData() instanceof WallSign wallSign)
		) {
			return false;
		}

		Block attachedBlock = block.getRelative(
			wallSign.getFacing().getOppositeFace()
		);
		return shopManager.isShop(attachedBlock.getLocation());
	}
}
