package ru.dscraft.destroychat.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Цвет сообщений для /color. Хранится как один открывающий MiniMessage-тег:
 * &lt;red&gt;, &lt;#FF55FF&gt;, &lt;gradient:#FF0000:#FFFF00&gt; или &lt;rainbow&gt;.
 * Перед применением тег всегда проверяется {@link #isValidTag}, поэтому даже если
 * мета в LuckPerms отредактируют руками, в чат не попадёт ничего кроме цвета.
 */
public final class ChatColors {

    private static final String HEX = "#[0-9a-fA-F]{6}";
    private static final String NAMES = "black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|"
            + "dark_gray|blue|green|aqua|red|light_purple|yellow|white";

    private static final Pattern VALID_TAG = Pattern.compile(
            "^<(" + HEX + "|" + NAMES + "|rainbow|gradient(:" + HEX + "){2,3})>$");

    private static final Pattern CODE = Pattern.compile("^&?([0-9a-fA-F])$");
    private static final Pattern SINGLE_HEX = Pattern.compile("^&?(" + HEX + ")$");
    private static final Pattern GRADIENT = Pattern.compile("^" + HEX + "([:,\\s]+" + HEX + "){1,2}$");

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

    public static boolean isValidTag(String tag) {
        return tag != null && VALID_TAG.matcher(tag).matches();
    }
}
