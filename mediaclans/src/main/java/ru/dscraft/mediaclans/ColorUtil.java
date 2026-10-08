package ru.dscraft.mediaclans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Весь разбор цветного текста в одном месте.
 * <ul>
 *   <li>{@link #parse} - строки из config.yml (полный MiniMessage, их пишет только админ);</li>
 *   <li>{@link #rich} - префиксы из LuckPerms и от игроков: &-коды, &#RRGGBB, &x&R&R.. и
 *       MiniMessage-градиенты ВПЕРЕМЕШКУ. Разрешены только цвета/градиенты/жирный и т.п. -
 *       никаких click/hover/insert, чтобы игрок не мог вставить в префикс команду.</li>
 * </ul>
 */
public final class ColorUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** MiniMessage только с "безопасными" тегами: цвет, градиент, радуга, переход, стили, reset. */
    private static final MiniMessage SAFE_MM = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.decorations())
                    .resolver(StandardTags.gradient())
                    .resolver(StandardTags.rainbow())
                    .resolver(StandardTags.transition())
                    .resolver(StandardTags.reset())
                    .build())
            .build();

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final Pattern X_HEX = Pattern.compile(
            "[&§]x[&§]([0-9a-fA-F])[&§]([0-9a-fA-F])[&§]([0-9a-fA-F])[&§]([0-9a-fA-F])[&§]([0-9a-fA-F])[&§]([0-9a-fA-F])");
    private static final Pattern HASH_HEX = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");

    private static final String[] COLOR_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    };

    private ColorUtil() {
    }

    // ---------------- строки из конфига ----------------

    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        try {
            return MM.deserialize(raw);
        } catch (Exception e) {
            return Component.text(raw);
        }
    }

    public static Component parse(String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        return parse(replacePlaceholders(raw, placeholders));
    }

    /** Строка из конфига с компонент-плейсхолдерами (&lt;prefix&gt;, &lt;name&gt; и т.п.). */
    public static Component parse(String raw, TagResolver... resolvers) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        try {
            return MM.deserialize(raw, resolvers);
        } catch (Exception e) {
            return Component.text(raw);
        }
    }

    public static Component parseLines(List<String> lines, Map<String, String> placeholders) {
        if (lines == null || lines.isEmpty()) return Component.empty();
        return parse(String.join("\n", lines), placeholders);
    }

    public static String replacePlaceholders(String raw, Map<String, String> placeholders) {
        String result = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    // ---------------- префиксы / текст игроков ----------------

    /** Префикс из LuckPerms или от игрока: &-коды, hex и MiniMessage-градиенты вместе. */
    public static Component rich(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        try {
            return SAFE_MM.deserialize(legacyToTags(raw, true));
        } catch (Exception e) {
            return Component.text(raw);
        }
    }

    /** Старое имя метода - оставлено, чтобы не править все вызовы. */
    public static Component legacy(String raw) {
        return rich(raw);
    }

    /** Разбор строки, где уже стоят только "безопасные" теги (цвет сообщений из /color и т.п.). */
    public static Component safe(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        try {
            return SAFE_MM.deserialize(raw);
        } catch (Exception e) {
            return Component.text(raw);
        }
    }

    /** Экранирует теги в тексте игрока, чтобы "&lt;red&gt;" в сообщении оставалось просто текстом. */
    public static String escape(String text) {
        return SAFE_MM.escapeTags(text);
    }

    /**
     * &-коды -> теги MiniMessage. Цветовой код сбрасывает стиль (как в обычном Minecraft),
     * поэтому перед ним ставится &lt;reset&gt;.
     *
     * @param allowObfuscated разрешать ли &k (мигающий текст)
     */
    public static String legacyToTags(String input, boolean allowObfuscated) {
        String s = input;

        Matcher x = X_HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (x.find()) {
            x.appendReplacement(sb, "&#" + x.group(1) + x.group(2) + x.group(3) + x.group(4) + x.group(5) + x.group(6));
        }
        x.appendTail(sb);
        s = sb.toString();

        s = HASH_HEX.matcher(s).replaceAll("<reset><#$1>");

        Matcher m = CODE.matcher(s);
        sb = new StringBuilder();
        while (m.find()) {
            char c = Character.toLowerCase(m.group(1).charAt(0));
            String tag;
            if (c >= '0' && c <= '9') tag = "<reset><" + COLOR_NAMES[c - '0'] + ">";
            else if (c >= 'a' && c <= 'f') tag = "<reset><" + COLOR_NAMES[10 + (c - 'a')] + ">";
            else tag = switch (c) {
                case 'k' -> allowObfuscated ? "<obfuscated>" : "";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                default -> "<reset>"; // r
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(tag));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    /** Есть ли в тексте жирный/курсив/подчёркнутый/зачёркнутый/мигающий. */
    public static boolean hasDecorations(Component component) {
        for (TextDecoration decoration : TextDecoration.values()) {
            if (component.decoration(decoration) == TextDecoration.State.TRUE) return true;
        }
        for (Component child : component.children()) {
            if (hasDecorations(child)) return true;
        }
        return false;
    }

    // ---------------- цвета ----------------

    /**
     * Цвет из строки вида "&7", "&#CDCDFF", "#CDCDFF", "&lt;#CDCDFF&gt;", "&lt;gray&gt;".
     * Возвращает fallback, если цвет распознать не удалось.
     */
    public static TextColor parseColor(String raw, TextColor fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim();
        if (s.startsWith("#") && s.length() == 7) {
            TextColor hex = TextColor.fromHexString(s);
            return hex != null ? hex : fallback;
        }
        TextColor found = firstColor(rich(s + "x"));
        return found != null ? found : fallback;
    }

    /** Ближайший из 16 стандартных цветов - только их понимает цвет команды (ник над головой). */
    public static NamedTextColor toNamed(TextColor color) {
        if (color == null) return NamedTextColor.WHITE;
        if (color instanceof NamedTextColor named) return named;
        return NamedTextColor.nearestTo(color);
    }

    /** Имя стандартного цвета по &-коду (0-9, a-f), либо null. */
    public static String colorNameByCode(char code) {
        char c = Character.toLowerCase(code);
        if (c >= '0' && c <= '9') return COLOR_NAMES[c - '0'];
        if (c >= 'a' && c <= 'f') return COLOR_NAMES[10 + (c - 'a')];
        return null;
    }

    public static boolean isColorName(String name) {
        for (String n : COLOR_NAMES) {
            if (n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static TextColor firstColor(Component component) {
        if (component.color() != null) return component.color();
        for (Component child : component.children()) {
            TextColor c = firstColor(child);
            if (c != null) return c;
        }
        return null;
    }
}
