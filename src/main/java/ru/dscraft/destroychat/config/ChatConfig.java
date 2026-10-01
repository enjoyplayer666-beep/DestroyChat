package ru.dscraft.destroychat.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.dscraft.destroychat.DestroyChatPlugin;

import java.util.function.Predicate;

/** Настройки из config.yml плагина DestroyChat. */
public class ChatConfig {

    /** Тег клана по умолчанию: скобки &8&l[ ], название клана без жирного. */

    private final DestroyChatPlugin plugin;
    private FileConfiguration cfg;

    public ChatConfig(DestroyChatPlugin plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    // ---- каналы ----

    /** Радиус локального чата в блоках, 0 = локального чата нет, всё глобальное. */
    public int localRadius() {
        return cfg.getInt("local-radius", 100);
    }

    public String globalSymbol() {
        return cfg.getString("global-symbol", "!");
    }

    public String spyPermission() {
        return cfg.getString("spy-permission", "destroylobby.chat.spy");
    }

    public String nobodyHeardMessage() {
        return cfg.getString("nobody-heard-message", "");
    }

    // ---- формат ----

    public String layout() {
        String layout = cfg.getString("format.layout", "<icon> <rank><clan><prefix><name><stars> <dark_gray>→</dark_gray> ");
        // старые конфиги без <clan>: тег клана встаёт сразу после значка канала
        if (!layout.contains("<clan>")) {
            layout = layout.contains("<icon> ") ? layout.replace("<icon> ", "<icon> <clan>") : "<clan>" + layout;
        }
        // старые конфиги без <rank>: ранг из StatPlugin стоит перед кланом
        if (!layout.contains("<rank>")) layout = layout.replace("<clan>", "<rank><clan>");
        // старые конфиги без <stars>: звёзды сразу после ника
        if (!layout.contains("<stars>")) {
            layout = layout.contains("<name>") ? layout.replace("<name>", "<name><stars>") : layout;
        }
        return layout;
    }

    /** Оформление группы в чате: префикс вместо префикса LuckPerms и стиль ника. */
    public record GroupFormat(String chatPrefix, String nameStyle) {
    }

    /**
     * Формат из group-formats: сначала по основной группе LuckPerms, иначе первая группа по порядку
     * в конфиге, которая есть у игрока. null - у игрока нет такой группы.
     */
    public GroupFormat groupFormat(String primaryGroup, Predicate<String> hasGroup) {
        ConfigurationSection s = cfg.getConfigurationSection("group-formats");
        if (s == null) return null;
        String found = null;
        if (primaryGroup != null && s.isConfigurationSection(primaryGroup)) {
            found = primaryGroup;
        } else {
            for (String group : s.getKeys(false)) {
                if (hasGroup.test(group)) {
                    found = group;
                    break;
                }
            }
        }
        if (found == null) return null;
        return new GroupFormat(s.getString(found + ".chat-prefix", ""), s.getString(found + ".name-style", ""));
    }

    /**
     * Оформление привилегий в чате (donor-formats): первая группа сверху вниз, которая есть у игрока.
     * В отличие от group-formats это не команда проекта. null - нет.
     */
    public GroupFormat donorFormat(Predicate<String> hasGroup) {
        ConfigurationSection s = cfg.getConfigurationSection("donor-formats");
        if (s == null) return null;
        for (String group : s.getKeys(false)) {
            if (hasGroup.test(group)) {
                return new GroupFormat(s.getString(group + ".chat-prefix", ""), s.getString(group + ".name-style", ""));
            }
        }
        return null;
    }

    /** Звёзды персонала: первая подходящая группа из staff-stars, null - нет. */
    public String staffStars(Predicate<String> hasGroup) {
        ConfigurationSection s = cfg.getConfigurationSection("staff-stars");
        if (s == null) return null;
        for (String group : s.getKeys(false)) {
            if (hasGroup.test(group)) return s.getString(group);
        }
        return null;
    }

    public String localIcon() {
        return cfg.getString("format.local-icon", "<white>Ⓛ</white>");
    }

    public String globalIcon() {
        return cfg.getString("format.global-icon", "<white>Ⓖ</white>");
    }

    public String nameColor() {
        return cfg.getString("format.name-color", "&7");
    }

    public String defaultPrefix() {
        return cfg.getString("format.default-prefix", "&f⌜&3Игрок&f⌟ ");
    }

    public String localMessageColor() {
        return cfg.getString("format.local-message-color", "<#AFAABF>");
    }

    public String globalMessageColor() {
        return cfg.getString("format.global-message-color", "<#DBA078>");
    }

    public boolean nameClickMsg() {
        return cfg.getBoolean("format.name-click-msg", true);
    }

    // ---- чат-префикс ----

    public int chatPrefixMaxLength() {
        return cfg.getInt("chat-prefix.max-length", 24);
    }

}
