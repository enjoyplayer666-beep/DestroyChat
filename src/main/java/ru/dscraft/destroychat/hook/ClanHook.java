package ru.dscraft.destroychat.hook;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Тег клана из плагина MediaClans (ru.dscraft.mediaclans.ClanApi.chatTag) - через рефлексию,
 * чтобы плагины собирались независимо. Без MediaClans тега нет.
 */
public final class ClanHook {

    private static Method chatTag;
    private static ClassLoader loadedFrom;

    private ClanHook() {
    }

    /** [Клан] с карточкой при наведении или null. */
    public static Component chatTag(Player player) {
        Method m = resolve();
        if (m == null) return null;
        try {
            return (Component) m.invoke(null, player);
        } catch (Exception e) {
            return null;
        }
    }

    /** После перезагрузки MediaClans (новый загрузчик классов) ищет метод заново. */
    private static synchronized Method resolve() {
        Plugin clans = Bukkit.getPluginManager().getPlugin("MediaClans");
        if (clans == null || !clans.isEnabled()) return null;
        ClassLoader cl = clans.getClass().getClassLoader();
        if (cl == loadedFrom && chatTag != null) return chatTag;
        try {
            chatTag = Class.forName("ru.dscraft.mediaclans.ClanApi", true, cl).getMethod("chatTag", Player.class);
            loadedFrom = cl;
            return chatTag;
        } catch (Exception e) {
            chatTag = null;
            return null;
        }
    }
}
