package ru.dscraft.mediaclans;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiConsumer;

/** Клики в меню кланов: предметы из меню не забираются, клик по слоту - действие меню. */
public final class MenuListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ClanMenus.Menu) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClanMenus.Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        // клик по своему инвентарю - выбор иконки (в настройках клана и роли)
        if (event.getClickedInventory() != event.getInventory()) {
            ItemStack item = event.getCurrentItem();
            if (menu.ownItemClick != null && item != null && !item.getType().isAir()) menu.ownItemClick.accept(item);
            return;
        }
        BiConsumer<Player, org.bukkit.event.inventory.ClickType> action = menu.clicks.get(event.getRawSlot());
        if (action != null) action.accept(player, event.getClick());
    }
}
