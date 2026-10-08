package ru.dscraft.mediaclans;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Ответ в чат: на экране крупно "30 сек." и подсказка, в чате - "Напишите отмена в чат чтобы выйти".
 * Сообщение-ответ в общий чат не попадает.
 */
public final class ChatInput implements Listener {

    private record Pending(String prompt, Consumer<String> handler, long expiresAt) {
    }

    private final Plugin plugin;
    private final ClanActions actions;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(Plugin plugin, ClanActions actions) {
        this.plugin = plugin;
        this.actions = actions;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Закрыть меню и ждать ответ в чат; handler вызывается в основном потоке. */
    public void ask(Player player, String prompt, Consumer<String> handler) {
        player.closeInventory();
        long seconds = actions.settings().inputSeconds();
        pending.put(player.getUniqueId(), new Pending(prompt, handler, System.currentTimeMillis() + seconds * 1000L));
        actions.msg(player, "<white>" + prompt + "</white>");
        actions.msg(player, "<white>Напишите <red>отмена</red> в чат чтобы выйти.</white>");
        showTitle(player, prompt, seconds);
    }

    public boolean waiting(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    private void showTitle(Player player, String prompt, long seconds) {
        player.showTitle(Title.title(
                ColorUtil.parse("<#479CFF>" + seconds + "</#479CFF> <white>сек.</white>"),
                ColorUtil.parse("<white>" + prompt + "</white>"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ZERO)));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Pending> e : pending.entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            Pending p = e.getValue();
            if (player == null) {
                pending.remove(e.getKey());
                continue;
            }
            long left = (p.expiresAt() - now + 999) / 1000;
            if (left <= 0) {
                pending.remove(e.getKey());
                player.clearTitle();
                actions.msg(player, "<gray>Время вышло.</gray>");
                continue;
            }
            showTitle(player, p.prompt(), left);
        }
    }

    /** Раньше всех остальных обработчиков чата. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending p = pending.remove(player.getUniqueId());
        if (p == null || p.expiresAt() < System.currentTimeMillis()) return;
        event.setCancelled(true);
        String text = ColorUtil.plain(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.clearTitle();
            if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
                actions.msg(player, "<gray>Отменено.</gray>");
                return;
            }
            p.handler().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
