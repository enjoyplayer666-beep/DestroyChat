package ru.dscraft.destroychat.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Ранг игрока для чата - через рефлексию, чтобы плагины собирались независимо:
 * из плагина рангов DsRanks (ru.dscraft.ranks.RanksApi.chatRank), а если его нет -
 * из старого StatPlugin (ru.stat.StatApi.chatRank). Без обоих просто ничего не показывает.
 */
public final class StatHook {

    private static Method chatRank;
    private static ClassLoader loadedFrom;

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

    /** Карточка ранга для наведения (DsRanks RanksApi.rankHover), null - нет. */
    public static String rankHover(Player player) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("DsRanks");
        if (plugin == null || !plugin.isEnabled()) return null;
        try {
            Class<?> api = Class.forName("ru.dscraft.ranks.RanksApi", true, plugin.getClass().getClassLoader());
            return (String) api.getMethod("rankHover", Player.class).invoke(null, player);
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized Method resolve() {
        Method m = find("DsRanks", "ru.dscraft.ranks.RanksApi");
        return m != null ? m : find("StatPlugin", "ru.stat.StatApi");
    }

    /** Метод chatRank(Player) из плагина; после перезагрузки плагина (новый загрузчик классов) ищет заново. */
    private static Method find(String pluginName, String apiClass) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null || !plugin.isEnabled()) return null;
        ClassLoader cl = plugin.getClass().getClassLoader();
        if (cl == loadedFrom && chatRank != null) return chatRank;
        try {
            Class<?> api = Class.forName(apiClass, true, cl);
            chatRank = api.getMethod("chatRank", Player.class);
            loadedFrom = cl;
            return chatRank;
        } catch (Exception e) {
            return null;
        }
    }
}
