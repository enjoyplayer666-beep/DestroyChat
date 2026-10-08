package ru.dscraft.destroychat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.plugin.java.JavaPlugin;

/** Сервер переименован в Amaterasu: один раз заменяет старое название в уже существующих конфигах плагина. */
final class Rebrand {

    private static final Pattern OLD = Pattern.compile("DestroyCraft(?!MOTD|Motd)");

    private Rebrand() {
    }

    static void apply(JavaPlugin plugin) {
        apply(plugin, OLD, "Amaterasu", ".rebrand-amaterasu");
        // [DestroyLog] в логах команд -> [AmaterasuLog]
        apply(plugin, Pattern.compile("DestroyLog"), "AmaterasuLog", ".rebrand-amaterasu-log");
    }

    private static void apply(JavaPlugin plugin, Pattern old, String replacement, String marker) {
        File dir = plugin.getDataFolder();
        if (!dir.isDirectory()) return;
        File mark = new File(dir, marker);
        if (mark.exists()) return;
        int changed = 0;
        List<Path> files;
        try (var walk = Files.walk(dir.toPath())) {
            files = walk.filter(p -> p.toString().endsWith(".yml")).toList();
        } catch (IOException e) {
            plugin.getLogger().warning("Переименование в Amaterasu: " + e.getMessage());
            return;
        }
        for (Path f : files) {
            try {
                String s = Files.readString(f, StandardCharsets.UTF_8);
                String r = old.matcher(s).replaceAll(replacement);
                if (!r.equals(s)) {
                    Files.writeString(f, r, StandardCharsets.UTF_8);
                    changed++;
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Переименование в Amaterasu, " + f.getFileName() + ": " + e.getMessage());
            }
        }
        try {
            Files.writeString(mark.toPath(), old.pattern() + " -> " + replacement);
        } catch (IOException ignored) {
        }
        if (changed > 0) plugin.getLogger().info(old.pattern() + " заменено на " + replacement + " в конфигах: " + changed);
    }
}
