package ru.dscraft.destroychat.config;

import org.bukkit.configuration.file.FileConfiguration;
import ru.dscraft.destroychat.DestroyChatPlugin;

/** Настройки из config.yml плагина DestroyChat. */
public class ChatConfig {

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
        return cfg.getString("format.layout", "<icon> <prefix><name> <dark_gray>→</dark_gray> ");
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
