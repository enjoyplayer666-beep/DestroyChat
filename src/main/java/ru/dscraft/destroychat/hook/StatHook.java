package ru.dscraft.destroychat.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Ранг игрока из StatPlugin (ru.stat.StatApi.chatRank) - через рефлексию,
 * чтобы плагины собирались независимо. Без StatPlugin просто ничего не показывает.
 */
public final class StatHook {

    private static volatile Method chatRank;
    private static volatile boolean failed;

    private StatHook() {
    }

    /** Ранг для чата (§-цвета) или null. */
    public static String chatRank(Player player) {
        Method m = resolve();
        if (m == null) return null;
        try {
            return (String) m.invoke(null, player);
        } catch (Exception e) {
            return null;
        }
    }

    private static Method resolve() {
        Method m = chatRank;
        if (m != null || failed) return m;
        Plugin stat = Bukkit.getPluginManager().getPlugin("StatPlugin");
        if (stat == null || !stat.isEnabled()) return null;
        try {
            Class<?> api = Class.forName("ru.stat.StatApi", true, stat.getClass().getClassLoader());
            m = api.getMethod("chatRank", Player.class);
            chatRank = m;
            return m;
        } catch (Exception e) {
            failed = true;
            return null;
        }
    }
}
