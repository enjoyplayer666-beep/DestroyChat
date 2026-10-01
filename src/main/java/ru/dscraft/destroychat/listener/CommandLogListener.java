package ru.dscraft.destroychat.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.dscraft.destroychat.DestroyChatPlugin;
import ru.dscraft.destroychat.util.ColorUtil;

import java.util.List;
import java.util.Locale;

/**
 * [DestroyLog] Игрок ник ввёл команду: /команда - видят только команда проекта (группы из
 * group-formats, право destroychat.logs) и опы. Логируются только команды из command-log.commands.
 */
public class CommandLogListener implements Listener {

    public static final String PERMISSION = "destroychat.logs";

    private final DestroyChatPlugin plugin;

    public CommandLogListener(DestroyChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("command-log.enabled", true)) return;
        String message = event.getMessage();
        Player player = event.getPlayer();
        // пароли - никогда
        if (isAuth(message)) return;
        // опы видят все команды всех игроков, команда проекта - только команды из staff-visible
        boolean forStaff = logged(cfg.getStringList("command-log.staff-visible"), message);

        Component line = ColorUtil.parse(cfg.getString("command-log.format", DEFAULT_FORMAT),
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("command", message));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.isOp() || (forStaff && staff(viewer, cfg))) viewer.sendMessage(line);
        }
        if (cfg.getBoolean("command-log.console", false)) Bukkit.getConsoleSender().sendMessage(line);
    }

    public static final String DEFAULT_FORMAT = "<gray>[</gray><gradient:#FF4B4B:#FFD24B>DestroyLog</gradient><gray>]</gray> "
            + "<#55FFFF>Игрок</#55FFFF> <#00AAAA><player></#00AAAA> <#55FFFF>ввёл команду:</#55FFFF> <gold><command></gold>";

    /**
     * "/minecraft:ban x" -> "ban x", "//set 1" -> "/set 1"; запись из конфига подходит, если команда
     * с неё начинается (можно и с подкомандой: "clan admin"). Без учёта регистра.
     */
    private static boolean logged(List<String> commands, String message) {
        if (message.length() < 2 || message.charAt(0) != '/') return false;
        String body = message.substring(1).toLowerCase(Locale.ROOT).trim();
        int space = body.indexOf(' ');
        String label = space < 0 ? body : body.substring(0, space);
        int colon = label.indexOf(':');
        if (colon >= 0) body = body.substring(colon + 1);
        if (body.isEmpty()) return false;
        for (String c : commands) {
            String name = c.trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("/")) name = name.substring(1); // в конфиге можно писать и "ban", и "/ban"
            if (name.isEmpty()) continue;
            if (body.equals(name) || body.startsWith(name + " ")) return true;
        }
        return false;
    }

    /** Вход/регистрация/смена пароля - в логи никогда (там пароль). */
    private static boolean isAuth(String message) {
        String label = message.length() < 2 ? "" : message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        return List.of("l", "login", "log", "reg", "register", "changepassword", "changepass", "cp").contains(label);
    }

    /** Команда проекта: оп, право destroychat.logs или группа из group-formats. */
    private static boolean staff(Player player, FileConfiguration cfg) {
        if (player.isOp() || player.hasPermission(PERMISSION)) return true;
        ConfigurationSection groups = cfg.getConfigurationSection("group-formats");
        if (groups == null) return false;
        for (String group : groups.getKeys(false)) {
            if (player.hasPermission("group." + group)) return true;
        }
        return false;
    }
}
