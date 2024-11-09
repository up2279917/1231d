package com.bytefish.bytecore.listeners;

import com.bytefish.bytecore.config.ConfigManager;
import com.bytefish.bytecore.managers.ShopManager;
import com.bytefish.bytecore.models.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

public class ShopInteractionListener implements Listener {

    private final ShopManager shopManager;
    private final ConfigManager configManager;

    public ShopInteractionListener(ShopManager shopManager, ConfigManager configManager) {
        this.shopManager = shopManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Handle both left and right clicks for Bedrock compatibility
        if ((event.getAction() != Action.RIGHT_CLICK_BLOCK &&
             event.getAction() != Action.LEFT_CLICK_BLOCK) ||
            event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        Block containerBlock = block;
        boolean isSignClick = false;

        // If clicking a sign, get the container it's attached to
        if (block.getState() instanceof Sign sign) {
            if (block.getBlockData() instanceof WallSign wallSign) {
                containerBlock = block.getRelative(wallSign.getFacing().getOppositeFace());
                isSignClick = true;
            } else {
                return; // Not a wall sign, ignore
            }
        }

        // Check if this is a shop container
        if (!configManager.isValidShopContainer(containerBlock.getType())) {
            return;
        }

        // Check if this is a shop
        if (!shopManager.isShop(containerBlock.getLocation())) {
            return;
        }

        Shop shop = shopManager.getShop(containerBlock.getLocation());
        if (shop == null) {
            return;
        }

        Player player = event.getPlayer();

        // Handle the interaction
        handleShopInteraction(event, player, shop, containerBlock, isSignClick);
    }

    private void handleShopInteraction(PlayerInteractEvent event, Player player, Shop shop, Block containerBlock, boolean isSignClick) {
        // Always cancel the interaction first
        event.setCancelled(true);

        // Allow shop owners to access their shops normally
        if (player.getUniqueId().equals(shop.getOwnerUUID())) {
            if (containerBlock.getState() instanceof Container) {
                event.setCancelled(false); // Allow container access for owner
            }
            return;
        }

        // For Bedrock compatibility, left click or right click on sign triggers purchase
        // For containers, only right click triggers purchase
        boolean shouldPurchase = isSignClick || event.getAction() == Action.RIGHT_CLICK_BLOCK;

        if (!shouldPurchase) {
            return;
        }

        // First verify the shop sign is still valid
        if (!shopManager.verifyShopSign(containerBlock.getLocation())) {
            player.sendMessage(Component.text("This shop's sign is broken! Please notify the owner.").color(NamedTextColor.RED));
            return;
        }

        // Get the container
        if (!(containerBlock.getState() instanceof Container container)) {
            player.sendMessage(Component.text("This shop's container is missing!").color(NamedTextColor.RED));
            return;
        }

        // Check shop stock
        if (!shopManager.hasStock(container.getInventory(), shop.getSellingItem(), shop.getSellingAmount())) {
            player.sendMessage(Component.text("This shop is out of stock!").color(NamedTextColor.RED));
            return;
        }

        // Check if container has space for payment
        if (!shopManager.hasSpace(container.getInventory(), shop.getPriceItem(), shop.getPriceAmount())) {
            player.sendMessage(Component.text("This shop is full and cannot accept payment!").color(NamedTextColor.RED));
            return;
        }

        // Check if player has the payment items
        if (!shopManager.hasItems(player.getInventory(), shop.getPriceItem(), shop.getPriceAmount())) {
            String itemName = shop.getPriceItem().getType().toString().toLowerCase().replace('_', ' ');
            player.sendMessage(Component.text()
                .append(Component.text("You need ").color(NamedTextColor.RED))
                .append(Component.text(shop.getPriceAmount() + "x ").color(NamedTextColor.YELLOW))
                .append(Component.text(itemName).color(NamedTextColor.RED))
                .append(Component.text(" to make this purchase!").color(NamedTextColor.RED))
                .build());
            return;
        }

        // Check if player has inventory space
        if (!shopManager.hasSpace(player.getInventory(), shop.getSellingItem(), shop.getSellingAmount())) {
            player.sendMessage(Component.text("You don't have enough inventory space!").color(NamedTextColor.RED));
            return;
        }

        // Process the transaction
        shopManager.processTransaction(shop, player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Check if breaking a container
        if (configManager.isValidShopContainer(block.getType())) {
            if (shopManager.isShop(block.getLocation())) {
                Shop shop = shopManager.getShop(block.getLocation());
                if (shop != null && !event.getPlayer().getUniqueId().equals(shop.getOwnerUUID())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(Component.text("You cannot break another player's shop!").color(NamedTextColor.RED));
                    return;
                }
            }
        }

        // Check if breaking a sign
        if (block.getState() instanceof Sign && block.getBlockData() instanceof WallSign wallSign) {
            Block containerBlock = block.getRelative(wallSign.getFacing().getOppositeFace());
            if (shopManager.isShop(containerBlock.getLocation())) {
                Shop shop = shopManager.getShop(containerBlock.getLocation());
                if (shop != null && !event.getPlayer().getUniqueId().equals(shop.getOwnerUUID())) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(Component.text("You cannot break another player's shop sign!").color(NamedTextColor.RED));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Container container && shopManager.isShop(container.getLocation())) {
            Shop shop = shopManager.getShop(container.getLocation());
            if (shop != null && !player.getUniqueId().equals(shop.getOwnerUUID())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Container container && shopManager.isShop(container.getLocation())) {
            Shop shop = shopManager.getShop(container.getLocation());
            if (shop != null && !player.getUniqueId().equals(shop.getOwnerUUID())) {
                event.setCancelled(true);
            }
        }
    }
}
