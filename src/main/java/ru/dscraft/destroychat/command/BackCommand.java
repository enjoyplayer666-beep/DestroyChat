package ru.dscraft.destroychat.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.dscraft.destroychat.DestroyChatPlugin;
import ru.dscraft.destroychat.util.ColorUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /back и /dback - на место последней смерти, у всех групп.
 * Перехватывает ввод раньше Essentials, поэтому права essentials.back не нужны.
 */
public class BackCommand implements Listener {

    private final DestroyChatPlugin plugin;
    private final Map<UUID, Location> deaths = new HashMap<>();

    public BackCommand(DestroyChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        deaths.put(event.getEntity().getUniqueId(), event.getEntity().getLocation().clone());
    }

    /** После CommandAccess (LOWEST): команда уже разрешена игроку. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg.length() < 2) return;
        String label = msg.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!label.equals("back") && !label.equals("dback")) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Location death = deaths.get(player.getUniqueId());
        if (death == null || death.getWorld() == null) {
            player.sendMessage(ColorUtil.parse(plugin.getConfig().getString("back.no-death",
                    "<#E53232>◆</#E53232> <#C7C4B7>Вы ещё не умирали.</#C7C4B7>")));
            return;
        }
        Location target = safe(death);
        if (target == null) {
            player.sendMessage(ColorUtil.parse(plugin.getConfig().getString("back.unsafe",
                    "<#E53232>◆</#E53232> <#C7C4B7>На место смерти не переместиться - там пустота.</#C7C4B7>")));
            return;
        }
        player.teleport(target);
        player.sendMessage(ColorUtil.parse(plugin.getConfig().getString("back.message",
                "<#8971AF>Телепорт</#8971AF> <dark_gray>»</dark_gray> <#C8C5B8>Вы перемещены на место смерти.</#C8C5B8>")));
    }

    /** Умер в пустоте - на самый верхний блок над этим местом; нет блока - null. */
    private static Location safe(Location death) {
        World world = death.getWorld();
        if (death.getY() >= world.getMinHeight()) return death;
        Block top = world.getHighestBlockAt(death.getBlockX(), death.getBlockZ());
        if (top.getType().isAir()) return null;
        return top.getLocation().add(0.5, 1, 0.5);
    }
}
