package ru.dscraft.destroychat.listener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import ru.dscraft.destroychat.DestroyChatPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Для всех, кроме опов (и игроков, и команды проекта):
 * - команды с ":" (destroychat:color, minecraft:tp ...) не видны в подсказках и не выполняются;
 * - /plugins и другие команды из hidden-commands.commands не видны и не выполняются.
 * Вместо них пишется "Нет такой команды :/".
 */
public class CommandHideListener implements Listener {

    private final DestroyChatPlugin plugin;

    public CommandHideListener(DestroyChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (event.getPlayer().isOp()) return;
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("hidden-commands.enabled", true)) return;
        List<String> hidden = hiddenList(cfg);
        event.getCommands().removeIf(c -> c.indexOf(':') >= 0 || hidden.contains(c.toLowerCase(Locale.ROOT)));
    }

    /** Раньше всех: команду не увидит ни другой плагин, ни лог [DestroyLog]. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("hidden-commands.enabled", true)) return;
        String message = event.getMessage();
        if (message.length() < 2) return;
        String label = message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (label.indexOf(':') < 0 && !hiddenList(cfg).contains(label)) return;
        event.setCancelled(true);
        player.sendMessage(plugin.commandAccess().message()); // одно сообщение на все недоступные команды (commands.yml)
    }

    private static List<String> hiddenList(FileConfiguration cfg) {
        return cfg.getStringList("hidden-commands.commands").stream()
                .map(c -> c.trim().toLowerCase(Locale.ROOT))
                .map(c -> c.startsWith("/") ? c.substring(1) : c)
                .toList();
    }
}
