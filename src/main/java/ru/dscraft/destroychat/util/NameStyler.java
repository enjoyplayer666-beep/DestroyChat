package ru.dscraft.destroychat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.hook.LuckPermsHook;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Цвет ника в чате (в табе так же делает плагин MediaTab).
 * <p>
 * Цвет ника задаётся хвостом префикса: коды после последнего видимого символа.
 * {@code /prefix chat &6&l&oКОРОЛЬ &2&l&o} - префикс "КОРОЛЬ", ник - &amp;2&amp;l&amp;o.
 * Можно и градиент: {@code ... <gradient:#FF5555:#FFFF55>}.
 * Если в префиксе хвоста нет: команда проекта (group-formats) - белый, остальные - серый &amp;7.
 */
public class NameStyler {

    /** Хвост из одних цветовых кодов/тегов (с пробелами между ними) в конце строки. */
    private static final Pattern TRAILING_STYLE = Pattern.compile(
            "((?:\\s*(?:[&§]x(?:[&§][0-9a-fA-F]){6}|[&§]#[0-9a-fA-F]{6}|[&§][0-9a-fk-orA-FK-OR]|<[^/<>][^<>]*>))+)\\s*$");

    /** Префикс без хвоста и стиль ника из хвоста (null - хвоста нет). */
    public record Split(String prefix, String nickStyle) {
    }

    private final ChatConfig config;
    private final LuckPermsHook luckPermsHook;

    public NameStyler(ChatConfig config, LuckPermsHook luckPermsHook) {
        this.config = config;
        this.luckPermsHook = luckPermsHook;
    }

    public ChatConfig.GroupFormat group(Player player) {
        return config.groupFormat(luckPermsHook.getPrimaryGroup(player), g -> player.hasPermission("group." + g));
    }

    /** Отделяет от префикса хвост с цветом ника. Пробел перед ником остаётся в префиксе. */
    public static Split split(String raw) {
        if (raw == null || raw.isEmpty()) return new Split(raw, null);
        Matcher m = TRAILING_STYLE.matcher(raw);
        if (!m.find()) return new Split(raw, null);
        String prefix = raw.substring(0, m.start());
        // без видимого текста это не префикс с цветом ника, а просто цвет
        if (ColorUtil.plain(ColorUtil.rich(prefix)).isBlank()) return new Split(raw, null);
        // &r в хвосте - сброс после префикса, а не цвет ника
        String style = m.group(1).replaceAll("\\s+", "").replaceAll("[&§][rR]", "");
        if (style.isEmpty()) return new Split(raw, null);
        if (!prefix.endsWith(" ")) prefix = prefix + " ";
        return new Split(prefix, style);
    }

    /** Ник в чате: стиль из хвоста префикса -> name-style команды проекта -> format.name-color. */
    public Component chatName(Player player, String nickStyle, ChatConfig.GroupFormat group) {
        if (nickStyle != null) return styled(nickStyle, player.getName());
        if (group != null && group.nameStyle() != null && !group.nameStyle().isBlank()) {
            return styled(group.nameStyle(), player.getName());
        }
        return styled(config.nameColor(), player.getName());
    }

    private static Component styled(String style, String name) {
        Component c = style == null || style.isBlank()
                ? Component.text(name, NamedTextColor.GRAY)
                : ColorUtil.rich(style + name);
        return Component.empty().append(c);
    }
}
