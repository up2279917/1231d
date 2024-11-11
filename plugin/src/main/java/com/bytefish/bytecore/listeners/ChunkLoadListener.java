package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.managers.ShopManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class ChunkLoadListener implements Listener {

	private final ShopManager shopManager;

	public ChunkLoadListener(ShopManager shopManager) {
		this.shopManager = shopManager;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent event) {
		shopManager.handleChunkLoad(event.getChunk());
	}
}
