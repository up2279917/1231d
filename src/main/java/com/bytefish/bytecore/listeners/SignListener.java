package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.ByteCore;
import com.bytefish.bytecore.managers.LocationManager;
import com.bytefish.bytecore.managers.ShopManager;
import com.bytefish.bytecore.models.Location;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignListener implements Listener {

	private final ShopManager shopManager;
	private final LocationManager locationManager;
	private final PlainTextComponentSerializer textSerializer =
		PlainTextComponentSerializer.plainText();
	private final ByteCore plugin;

	public SignListener(ByteCore plugin) {
		this.plugin = plugin;
		this.shopManager = plugin.getShopManager();
		this.locationManager = plugin.getLocationManager();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		for (Block block : event.blockList()) {
			if (block.getState() instanceof Sign) {
				handleSignDestruction(block, null);
			} else {
				checkAttachedSigns(block);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		for (Block block : event.blockList()) {
			if (block.getState() instanceof Sign) {
				handleSignDestruction(block, null);
			} else {
				checkAttachedSigns(block);
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBurn(BlockBurnEvent event) {
		Block block = event.getBlock();
		if (block.getState() instanceof Sign) {
			handleSignDestruction(block, null);
		} else {
			checkAttachedSigns(block);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPhysics(BlockPhysicsEvent event) {
		Block block = event.getBlock();
		if (block.getState() instanceof Sign) {
			Block attachedTo = getAttachedBlock(block);
			if (
				attachedTo != null &&
				(attachedTo.getType().isAir() ||
					!attachedTo.getType().isSolid())
			) {
				plugin
					.getServer()
					.getScheduler()
					.runTask(plugin, () -> {
						handleSignDestruction(block, null);
					});
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityChangeBlock(EntityChangeBlockEvent event) {
		Block block = event.getBlock();
		if (block.getState() instanceof Sign) {
			handleSignDestruction(block, null);
		}
		checkAttachedSigns(block);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockFromTo(BlockFromToEvent event) {
		Block toBlock = event.getToBlock();
		if (toBlock.getState() instanceof Sign) {
			handleSignDestruction(toBlock, null);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent event) {
		for (Block block : event.getBlocks()) {
			if (block.getState() instanceof Sign) {
				handleSignDestruction(block, null);
			}
			checkAttachedSigns(block);

			Block destination = block.getRelative(event.getDirection());
			checkAttachedSigns(destination);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent event) {
		for (Block block : event.getBlocks()) {
			if (block.getState() instanceof Sign) {
				handleSignDestruction(block, null);
			}
			checkAttachedSigns(block);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSignEdit(SignChangeEvent event) {
		Block block = event.getBlock();

		// Check if this is an existing shop or location sign
		if (isProtectedSign(block)) {
			event.setCancelled(true);
			return;
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onSignClick(PlayerInteractEvent event) {
		if (
			event.getClickedBlock() == null || !event.getAction().isRightClick()
		) {
			return;
		}

		Block block = event.getClickedBlock();
		if (block.getState() instanceof Sign && isProtectedSign(block)) {
			// Cancel any right-click interaction with protected signs
			event.setCancelled(true);
		}
	}

	private boolean isProtectedSign(Block block) {
		if (!(block.getState() instanceof Sign sign)) {
			return false;
		}

		// Check if it's a shop sign
		if (block.getBlockData() instanceof WallSign wallSign) {
			Block attached = block.getRelative(
				wallSign.getFacing().getOppositeFace()
			);
			if (shopManager.isShop(attached.getLocation())) {
				return true;
			}
		}

		// Check if it's a location sign
		String firstLine = textSerializer.serialize(sign.line(0));
		return firstLine != null && firstLine.equalsIgnoreCase("Location");
	}

	private void checkAttachedSigns(Block block) {
		org.bukkit.block.BlockFace[] faces = {
			org.bukkit.block.BlockFace.NORTH,
			org.bukkit.block.BlockFace.SOUTH,
			org.bukkit.block.BlockFace.EAST,
			org.bukkit.block.BlockFace.WEST,
		};

		for (org.bukkit.block.BlockFace face : faces) {
			Block relative = block.getRelative(face);
			if (relative.getState() instanceof Sign sign) {
				if (isWallSign(sign)) {
					org.bukkit.block.data.type.WallSign wallSign =
						(org.bukkit.block.data.type.WallSign) sign.getBlockData();
					if (wallSign.getFacing().getOppositeFace() == face) {
						plugin
							.getServer()
							.getScheduler()
							.runTask(plugin, () -> {
								handleSignDestruction(relative, null);
							});
					}
				}
			}
		}
	}

	private boolean isWallSign(Sign sign) {
		return sign.getBlock().getType().name().contains("WALL_SIGN");
	}

	private Block getAttachedBlock(Block signBlock) {
		if (!(signBlock.getState() instanceof Sign sign) || !isWallSign(sign)) {
			return null;
		}
		org.bukkit.block.data.type.WallSign wallSign =
			(org.bukkit.block.data.type.WallSign) sign.getBlockData();
		return signBlock.getRelative(wallSign.getFacing().getOppositeFace());
	}

	private void handleSignDestruction(
		Block block,
		org.bukkit.entity.Player player
	) {
		if (!(block.getState() instanceof Sign sign)) {
			return;
		}

		if (isLocationSign(block)) {
			String locationName = textSerializer.serialize(
				sign.getSide(Side.FRONT).line(1)
			);
			if (locationName == null || locationName.isEmpty()) {
				return;
			}

			plugin
				.getLogger()
				.info("Attempting to remove location sign: " + locationName);

			Optional<Location> location = locationManager.getLocationByName(
				locationName
			);
			location.ifPresent(loc -> {
				if (player != null) {
					if (
						!loc.getOwner().equals(player.getName()) &&
						!player.hasPermission("bytecore.location.removeany")
					) {
						return;
					}
					player.sendMessage(
						Component.text("Location marker removed!").color(
							NamedTextColor.GREEN
						)
					);
				}

				plugin
					.getLogger()
					.info(
						"Removing location: " +
						locationName +
						" (triggered by " +
						(player != null ? player.getName() : "environment") +
						")"
					);

				boolean removed = locationManager.removeLocation(loc.getId());
				plugin
					.getLogger()
					.info(
						"Location removal result for " +
						locationName +
						": " +
						removed
					);

				locationManager.removeLocation(loc.getId());

				locationManager.saveAll();
			});
		}
	}

	private boolean isLocationSign(Block block) {
		if (!(block.getState() instanceof Sign sign)) {
			return false;
		}

		// Check the actual sign content
		String firstLine = textSerializer.serialize(
			sign.getSide(Side.FRONT).line(0)
		);
		return firstLine != null && firstLine.equalsIgnoreCase("Location");
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onSignChange(SignChangeEvent event) {
		Block block = event.getBlock();
		String firstLine = textSerializer.serialize(event.line(0));

		// If this is an existing Location sign
		if (isLocationSign(block)) {
			// Always cancel edits to existing Location signs
			event.setCancelled(true);
			return;
		}

		// For new signs that aren't Location signs yet
		if (
			firstLine.equalsIgnoreCase("Selling") ||
			firstLine.equalsIgnoreCase("Location")
		) {
			return;
		}

		if (isShopSign(block)) {
			event.setCancelled(true);
		}
	}

	private boolean isShopSign(Block block) {
		if (!(block.getState() instanceof Sign sign) || !isWallSign(sign)) {
			return false;
		}

		org.bukkit.block.data.type.WallSign wallSign =
			(org.bukkit.block.data.type.WallSign) sign.getBlockData();
		Block attached = block.getRelative(
			wallSign.getFacing().getOppositeFace()
		);
		return shopManager.isShop(attached.getLocation());
	}
}
