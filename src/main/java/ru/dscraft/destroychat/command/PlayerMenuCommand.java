package ru.dscraft.destroychat.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import ru.dscraft.destroychat.DestroyChatPlugin;
import ru.dscraft.destroychat.util.ColorUtil;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Меню действий над игроком: клик по нику в чате -> /dcplayer ник -> список действий в чате
 * (player-menu в config.yml). Пункт виден, только если игроку доступна его команда (commands.yml),
 * мут и бан - ещё и по правам MediaBans (ultra+ мут, elitesp бан, команда проекта всё).
 */
public class PlayerMenuCommand implements CommandExecutor {

    public static final String NAME = "dcplayer";
    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final DestroyChatPlugin plugin;

    public PlayerMenuCommand(DestroyChatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer) || args.length < 1 || !NICK.matcher(args[0]).matches()) return true;
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("player-menu");
        if (cfg == null || !cfg.getBoolean("enabled", true)) return true;
        String target = args[0];
        Map<String, String> ph = Map.of("name", target);

        // на себя меню не открывается
        if (target.equalsIgnoreCase(viewer.getName())) return true;

        Component out = Component.empty();
        String top = cfg.getString("top", "");
        if (!top.isEmpty()) out = out.append(ColorUtil.parse(top, ph)).append(Component.newline());
        out = out.append(ColorUtil.parse(cfg.getString("title", ""), ph));
        String border = cfg.getString("border", "");
        if (!border.isEmpty()) out = out.append(Component.newline()).append(ColorUtil.parse(border, ph));

        int shown = 0;
        ConfigurationSection items = cfg.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(key);
                if (item == null) continue;
                String cmd = ColorUtil.replacePlaceholders(item.getString("command", ""), ph);
                if (cmd.isBlank() || !allowed(viewer, item.getString("need", ""), cmd)) continue;
                boolean run = item.getString("action", "suggest").equalsIgnoreCase("run");
                Component line = ColorUtil.parse(item.getString("text", key), ph)
                        .hoverEvent(HoverEvent.showText(ColorUtil.parse(item.getString("hover", ""), ph)))
                        .clickEvent(run ? ClickEvent.runCommand(cmd) : ClickEvent.suggestCommand(cmd));
                out = out.append(Component.newline()).append(line);
                shown++;
            }
        }
        if (shown == 0) return true;
        if (!border.isEmpty()) out = out.append(Component.newline()).append(ColorUtil.parse(border, ph));
        viewer.sendMessage(out);
        return true;
    }

    /** Команда пункта доступна игроку, а мут/бан - ещё и по MediaBans. */
    private boolean allowed(Player viewer, String need, String cmd) {
        if (need.equalsIgnoreCase("mute") && !ru.dscraft.mediabans.MediaBansPlugin.canPunish(viewer, false)) return false;
        if (need.equalsIgnoreCase("ban") && !ru.dscraft.mediabans.MediaBansPlugin.canPunish(viewer, true)) return false;
        return plugin.commandAccess() == null || plugin.commandAccess().canUse(viewer, cmd);
    }
}
