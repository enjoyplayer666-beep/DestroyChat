package ru.dscraft.mediabans;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Locale;

/** Не пускает забаненных, не даёт писать замученным, прячет команды, которые игроку недоступны. */
public final class PunishListener implements Listener {

    private final MediaBansPlugin plugin;

    public PunishListener(MediaBansPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        Punishment ban = plugin.store().activeBan(event.getUniqueId(), event.getName());
        if (ban != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, plugin.command().banScreen(ban));
        }
    }

    /** Раньше всех: ни общий, ни клановый чат замученный не увидит. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Punishment mute = plugin.store().active(Punishment.Type.MUTE, player.getUniqueId());
        if (mute == null) return;
        event.setCancelled(true);
        player.sendMessage(plugin.command().mutedMessage(mute));
    }

    /** Личные сообщения и /me в муте тоже нельзя (список - muted-commands в config.yml). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() < 2) return;
        Player player = event.getPlayer();
        Punishment mute = plugin.store().active(Punishment.Type.MUTE, player.getUniqueId());
        if (mute == null) return;
        String label = message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (!plugin.mutedCommands().contains(label)) return;
        event.setCancelled(true);
        player.sendMessage(plugin.command().mutedMessage(mute));
    }

    /** Чего игроку нельзя - того нет и в подсказках. */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        event.getCommands().removeIf(c -> {
            String label = c.toLowerCase(Locale.ROOT);
            int colon = label.indexOf(':');
            if (colon >= 0) label = label.substring(colon + 1);
            String main = plugin.mainCommand(label);
            if (main == null) return false;
            if (main.equals("mediabans")) return !player.isOp();
            Access.Action action = PunishCommand.action(main);
            return action != null && !plugin.access().can(player, action);
        });
    }
}
