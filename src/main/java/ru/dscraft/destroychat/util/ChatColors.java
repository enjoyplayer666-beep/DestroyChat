package ru.dscraft.destroychat.util;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Цвет сообщений для /color. Хранится как открывающий MiniMessage-тег цвета
 * (&lt;red&gt;, &lt;#FF55FF&gt;, &lt;gradient:#FF0000:#FFFF00&gt;, &lt;rainbow&gt;),
 * за которым могут идти стили: &lt;bold&gt;, &lt;italic&gt;, &lt;underlined&gt;, &lt;strikethrough&gt;.
 * Перед применением тег всегда проверяется {@link #isValidTag}, поэтому даже если
 * мета в LuckPerms отредактируют руками, в чат не попадёт ничего кроме цвета.
 */
public final class ChatColors {

    private static final String HEX = "#[0-9a-fA-F]{6}";
    private static final String NAMES = "black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|"
            + "dark_gray|blue|green|aqua|red|light_purple|yellow|white";

    private static final String DECORATIONS = "bold|italic|underlined|strikethrough";

    private static final Pattern VALID_TAG = Pattern.compile(
            "^<(" + HEX + "|" + NAMES + "|rainbow|gradient(:" + HEX + "){2,6})>(<(" + DECORATIONS + ")>){0,4}$");

    private static final Pattern CODE = Pattern.compile("^&?([0-9a-fA-F])$");
    private static final Pattern SINGLE_HEX = Pattern.compile("^&?(" + HEX + ")$");
    private static final Pattern GRADIENT = Pattern.compile("^" + HEX + "([:,\\s]+" + HEX + "){1,5}$");

    /** &x&R&R&G&G&B&B / §x§R§R... и &#RRGGBB. */
    private static final Pattern LEGACY_X = Pattern.compile(
            "&x&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])&([0-9a-fA-F])");
    /** Один элемент legacy-записи: hex-цвет, &-код, разделитель. */
    private static final Pattern LEGACY_TOKEN = Pattern.compile("&?(#[0-9a-fA-F]{6})|&([0-9a-fA-Fk-orK-OR])|[\\s:,]+");

    /** Русские названия цветов для удобства игроков. */
    public static final Map<String, String> RU = new LinkedHashMap<>();

    static {
        RU.put("красный", "red");
        RU.put("темно-красный", "dark_red");
        RU.put("оранжевый", "gold");
        RU.put("золотой", "gold");
        RU.put("желтый", "yellow");
        RU.put("зеленый", "green");
        RU.put("темно-зеленый", "dark_green");
        RU.put("голубой", "aqua");
        RU.put("бирюзовый", "dark_aqua");
        RU.put("синий", "blue");
        RU.put("темно-синий", "dark_blue");
        RU.put("розовый", "light_purple");
        RU.put("фиолетовый", "dark_purple");
        RU.put("белый", "white");
        RU.put("серый", "gray");
        RU.put("темно-серый", "dark_gray");
        RU.put("черный", "black");
        RU.put("радуга", "rainbow");
    }

    private ChatColors() {
    }

    /** Ввод игрока -> тег, либо null если цвет не распознан. */
    public static String toTag(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        if (s.indexOf('&') >= 0 || s.indexOf('§') >= 0) {
            return legacyToTag(s);
        }

        var code = CODE.matcher(s);
        if (code.matches()) {
            return "<" + ColorUtil.colorNameByCode(code.group(1).charAt(0)) + ">";
        }
        var hex = SINGLE_HEX.matcher(s);
        if (hex.matches()) {
            return "<" + hex.group(1).toUpperCase(Locale.ROOT) + ">";
        }
        if (GRADIENT.matcher(s).matches()) {
            String[] parts = s.split("[:,\\s]+");
            return "<gradient:" + String.join(":", parts).toUpperCase(Locale.ROOT) + ">";
        }

        String lower = s.toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (RU.containsKey(lower)) lower = RU.get(lower);
        if (lower.equals("rainbow")) return "<rainbow>";
        if (ColorUtil.isColorName(lower)) return "<" + lower + ">";
        return null;
    }

    /**
     * Цвет в формате &-кодов: {@code §x§D§D§D§D§D§D§l}, {@code &x&F&F&0&0&0&0}, {@code &#FF5555&l}, {@code &a&l}.
     * Несколько цветов подряд (через пробел, : или без разделителя) - градиент.
     * &l &o &n &m - жирный, курсив, подчёркнутый, зачёркнутый; &k (мигание) не разрешён.
     */
    private static String legacyToTag(String input) {
        String s = input.replace('§', '&');
        s = LEGACY_X.matcher(s).replaceAll("#$1$2$3$4$5$6");

        java.util.List<String> colors = new java.util.ArrayList<>();
        java.util.Set<String> decorations = new java.util.LinkedHashSet<>();
        var m = LEGACY_TOKEN.matcher(s);
        int pos = 0;
        while (m.find()) {
            if (m.start() != pos) return null; // посторонний текст
            pos = m.end();
            if (m.group(1) != null) {
                colors.add(m.group(1).toUpperCase(Locale.ROOT));
            } else if (m.group(2) != null) {
                char c = Character.toLowerCase(m.group(2).charAt(0));
                switch (c) {
                    case 'l' -> decorations.add("bold");
                    case 'o' -> decorations.add("italic");
                    case 'n' -> decorations.add("underlined");
                    case 'm' -> decorations.add("strikethrough");
                    case 'k' -> {
                        return null;
                    }
                    case 'r' -> {
                    }
                    default -> colors.add(ColorUtil.colorNameByCode(c));
                }
            }
        }
        if (pos != s.length() || colors.isEmpty() || colors.size() > 6) return null;

        StringBuilder tag = new StringBuilder();
        if (colors.size() == 1) {
            tag.append('<').append(colors.get(0)).append('>');
        } else {
            tag.append("<gradient");
            for (String color : colors) tag.append(':').append(toHex(color));
            tag.append('>');
        }
        for (String d : decorations) tag.append('<').append(d).append('>');
        return tag.toString();
    }

    private static String toHex(String color) {
        if (color.startsWith("#")) return color;
        NamedTextColor named = NamedTextColor.NAMES.value(color);
        return named == null ? "#FFFFFF" : named.asHexString().toUpperCase(Locale.ROOT);
    }

    public static boolean isValidTag(String tag) {
        return tag != null && VALID_TAG.matcher(tag).matches();
    }
}
