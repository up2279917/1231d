package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.managers.LocationManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class LocationSignDestroyListener implements Listener {

	private final LocationManager locationManager;
	private final PlainTextComponentSerializer textSerializer =
		PlainTextComponentSerializer.plainText();

	public LocationSignDestroyListener(LocationManager locationManager) {
		this.locationManager = locationManager;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		checkAndRemoveLocationSign(event.getBlock());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		event.blockList().forEach(this::checkAndRemoveLocationSign);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		event.blockList().forEach(this::checkAndRemoveLocationSign);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPhysics(BlockPhysicsEvent event) {
		Block block = event.getBlock();
		if (block.getState() instanceof Sign) {
			if (
				!block.getType().toString().contains("WALL_SIGN") &&
				!block.getRelative(0, -1, 0).getType().isSolid()
			) {
				checkAndRemoveLocationSign(block);
			}
		}
	}

	private void checkAndRemoveLocationSign(Block block) {
		if (!(block.getState() instanceof Sign sign)) {
			return;
		}

		String firstLine = textSerializer.serialize(sign.line(0));
		String secondLine = textSerializer.serialize(sign.line(1));

		if (firstLine.equalsIgnoreCase("Location")) {
			locationManager.removeLocationAtPosition(
				block.getLocation().getX(),
				block.getLocation().getY(),
				block.getLocation().getZ(),
				block.getWorld()
			);
		}
	}
}
