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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Имя в табе: префикс LuckPerms + ник (tab.name-color) + суффикс LuckPerms.
 * Если на сервере стоит плагин TAB, этот модуль не включается - таб делает TAB.
 */
public class TabListManager implements Listener, Runnable {

    private final ChatConfig config;
    private final LuckPermsHook luckPermsHook;
    /** Последнее отправленное имя - чтобы не слать одно и то же каждую секунду. */
    private final Map<UUID, Component> last = new ConcurrentHashMap<>();

    public TabListManager(ChatConfig config, LuckPermsHook luckPermsHook) {
        this.config = config;
        this.luckPermsHook = luckPermsHook;
    }

    /** Раз в секунду: вдруг игроку выдали или сняли группу. */
    @Override
    public void run() {
        if (!config.tabEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Component full = Component.empty()
                    .append(ColorUtil.rich(luckPermsHook.getPrefix(player)))
                    .append(ColorUtil.rich(config.tabNameColor() + player.getName()))
                    .append(ColorUtil.rich(luckPermsHook.getSuffix(player)));
            if (full.equals(last.get(player.getUniqueId()))) continue;
            last.put(player.getUniqueId(), full);
            player.playerListName(full);
        }
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
