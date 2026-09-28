package ru.dscraft.destroychat.announce;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.dscraft.destroychat.util.ColorUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Автосообщения в чат (announcements в config.yml): раз в interval-minutes выходит один блок
 * из нескольких строк, блоки идут по очереди (или случайно). Строки - MiniMessage, поэтому
 * можно делать кликабельные команды и ссылки.
 */
public class Announcer {

    private final Plugin plugin;
    private BukkitTask task;
    private List<List<String>> blocks = new ArrayList<>();
    private int index;

    public Announcer(Plugin plugin) {
        this.plugin = plugin;
    }

    /** (Пере)запуск по текущему конфигу - при включении и после /destroychat reload. */
    public void start() {
        stop();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("announcements");
        if (s == null || !s.getBoolean("enabled", true)) return;

        blocks = new ArrayList<>();
        List<?> raw = s.getList("messages");
        if (raw == null) return;
        for (Object o : raw) {
            List<String> lines = new ArrayList<>();
            if (o instanceof List<?> list) {
                for (Object line : list) lines.add(String.valueOf(line));
            } else if (o != null) {
                lines.add(String.valueOf(o));
            }
            if (!lines.isEmpty()) blocks.add(lines);
        }
        if (blocks.isEmpty()) return;
        if (s.getBoolean("random", false)) Collections.shuffle(blocks);
        index = 0;

        long period = Math.max(1, s.getLong("interval-minutes", 15)) * 60L * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::announce, period, period);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void announce() {
        if (blocks.isEmpty() || Bukkit.getOnlinePlayers().isEmpty()) return;
        List<String> block = blocks.get(index);
        index = (index + 1) % blocks.size();

        boolean blank = plugin.getConfig().getBoolean("announcements.blank-lines", true);
        List<Component> lines = new ArrayList<>();
        if (blank) lines.add(Component.empty());
        for (String line : block) lines.add(ColorUtil.parse(line));
        if (blank) lines.add(Component.empty());

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Component line : lines) player.sendMessage(line);
        }
    }
}
