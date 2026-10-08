package ru.dscraft.mediabans;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Время наказаний: "30m", "1h30m", "1д" -> мс и обратно "1 час 30 минут". */
public final class Durations {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    /** mo раньше m, иначе "1mo" прочитается как минута. */
    private static final Pattern PART = Pattern.compile("(\\d+)(mo|y|w|d|h|m|s|г|н|д|ч|м|с)");

    private Durations() {
    }

    /** Мс или -1, если строка не похожа на время. */
    public static long parse(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1;
        Matcher m = PART.matcher(s);
        int pos = 0;
        long total = 0;
        while (pos < s.length()) {
            if (!m.find(pos) || m.start() != pos) return -1;
            long n;
            try {
                n = Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
            long unit = switch (m.group(2)) {
                case "s", "с" -> SECOND;
                case "m", "м" -> MINUTE;
                case "h", "ч" -> HOUR;
                case "d", "д" -> DAY;
                case "w", "н" -> 7 * DAY;
                case "mo" -> 30 * DAY;
                default -> 365 * DAY; // y, г
            };
            total += n * unit;
            pos = m.end();
        }
        return total > 0 ? total : -1;
    }

    /** "1 секунду", "1 час 30 минут", "2 дня 5 часов" - не больше двух старших частей. */
    public static String format(long millis) {
        long seconds = Math.max(0, (millis + 999) / 1000);
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        long secs = seconds % 60;
        StringBuilder out = new StringBuilder();
        int parts = 0;
        long[] values = {days, hours, minutes, secs};
        String[][] words = {
                {"день", "дня", "дней"},
                {"час", "часа", "часов"},
                {"минуту", "минуты", "минут"},
                {"секунду", "секунды", "секунд"}};
        for (int i = 0; i < values.length && parts < 2; i++) {
            if (values[i] == 0) {
                if (parts > 0) break; // "1 час" без "0 минут", но и не "1 час 5 секунд"
                continue;
            }
            if (out.length() > 0) out.append(' ');
            out.append(values[i]).append(' ').append(plural(values[i], words[i][0], words[i][1], words[i][2]));
            parts++;
        }
        return out.length() == 0 ? "0 секунд" : out.toString();
    }

    public static String plural(long n, String one, String few, String many) {
        long n10 = n % 10;
        long n100 = n % 100;
        if (n10 == 1 && n100 != 11) return one;
        if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) return few;
        return many;
    }
}
