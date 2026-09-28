package ru.dscraft.destroychat.tab;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.hook.LuckPermsHook;
import ru.dscraft.destroychat.util.ColorUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Имя в табе: префикс LuckPerms + ник (tab.name-color) + суффикс LuckPerms.
 * Для групп из tab.animated-groups (например media) ник переливается градиентом.
 * Если на сервере стоит плагин TAB, этот модуль не включается - таб делает TAB.
 */
public class TabListManager implements Listener, Runnable {

    private final ChatConfig config;
    private final LuckPermsHook luckPermsHook;
    /** Последнее отправленное имя - чтобы не слать одно и то же каждые 2 тика. */
    private final Map<UUID, Component> last = new ConcurrentHashMap<>();
    private long ticks;

    public TabListManager(ChatConfig config, LuckPermsHook luckPermsHook) {
        this.config = config;
        this.luckPermsHook = luckPermsHook;
    }

    @Override
    public void run() {
        ticks++;
        if (!config.tabEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        String animatedGroup = animatedGroup(player);
        Component name;
        if (animatedGroup != null) {
            name = animatedName(player, animatedGroup);
        } else if (ticks % 20 == 0 || !last.containsKey(player.getUniqueId())) {
            // статичные имена проверяем раз в секунду (вдруг выдали группу)
            name = ColorUtil.rich(config.tabNameColor() + player.getName());
        } else {
            return;
        }

        Component full = Component.empty()
                .append(ColorUtil.rich(luckPermsHook.getPrefix(player)))
                .append(name)
                .append(ColorUtil.rich(luckPermsHook.getSuffix(player)));
        if (full.equals(last.get(player.getUniqueId()))) return;
        last.put(player.getUniqueId(), full);
        player.playerListName(full);
    }

    private String animatedGroup(Player player) {
        String primary = luckPermsHook.getPrimaryGroup(player);
        if (primary != null && config.tabAnimation(primary) != null) return primary;
        for (String group : config.tabAnimatedGroups()) {
            if (player.hasPermission("group." + group) && config.tabAnimation(group) != null) return group;
        }
        return null;
    }

    /** Ник с бегущим градиентом: цвета из конфига по кругу, фаза сдвигается каждый тик обновления. */
    private Component animatedName(Player player, String group) {
        List<String> colors = config.tabAnimation(group);
        StringBuilder tag = new StringBuilder("<gradient");
        for (String c : colors) tag.append(':').append(c);
        tag.append(':').append(colors.get(0)); // замыкаем по кругу, чтобы переливание было плавным
        double speed = config.tabAnimationSpeed(group);
        double phase = ((ticks * speed) % 2.0) - 1.0;
        tag.append(':').append(String.format(Locale.ROOT, "%.3f", phase)).append('>');
        return ColorUtil.parse(tag + player.getName() + "</gradient>");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        last.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        last.remove(event.getPlayer().getUniqueId());
    }
}
