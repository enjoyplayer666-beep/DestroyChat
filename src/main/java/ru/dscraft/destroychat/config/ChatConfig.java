package ru.dscraft.destroychat.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.dscraft.destroychat.DestroyChatPlugin;

import java.util.function.Predicate;

/** Настройки из config.yml плагина DestroyChat. */
public class ChatConfig {

    /** Тег клана по умолчанию: скобки &8&l[ ], название клана без жирного. */
    private static final String CLAN_TAG = "<dark_gray><bold>[</bold></dark_gray><clan><dark_gray><bold>]</bold></dark_gray> ";

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

    // ---- таб ----

    public boolean tabEnabled() {
        return cfg.getBoolean("tab.enabled", true);
    }

    public String tabNameColor() {
        return cfg.getString("tab.name-color", "&f");
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

    // ---- кланы ----

    public boolean clansEnabled() {
        return cfg.getBoolean("clans.enabled", true);
    }

    /** Тег клана в чате, &lt;clan&gt; - название клана. */
    public String clanChatTag() {
        String tag = cfg.getString("clans.chat-tag", CLAN_TAG);
        // конфиг от прошлой версии со старыми серыми скобками - берём новые &8&l[ ]
        return tag.equals("<gray>[</gray><clan><gray>]</gray> ") ? CLAN_TAG : tag;
    }

    public int clanKillRating() {
        return cfg.getInt("clans.kill-rating", 15);
    }

    public int clanDeathRating() {
        return cfg.getInt("clans.death-rating", 0);
    }

    public int clanKillCooldownSeconds() {
        return cfg.getInt("clans.kill-cooldown-seconds", 300);
    }

    public String clanKillMessage() {
        return cfg.getString("clans.kill-message",
                "<green>Вашему клану добавлено <yellow><amount></yellow> кланового рейтинга!</green>");
    }

    public String clanDeathMessage() {
        return cfg.getString("clans.death-message", "");
    }

    public String clanCreateTitle() {
        return cfg.getString("clans.create-title", "<green>Напишите название клана в чат!</green>");
    }

    public String clanCreateSubtitle() {
        return cfg.getString("clans.create-subtitle", "<gray>Для отмены напишите <white>отмена</white></gray>");
    }

    public int clanNameMinLength() {
        return cfg.getInt("clans.name-min-length", 3);
    }

    public int clanNameMaxLength() {
        return cfg.getInt("clans.name-max-length", 16);
    }

    public int clanMaxMembers() {
        return cfg.getInt("clans.max-members", 50);
    }

    public int clanInviteSeconds() {
        return cfg.getInt("clans.invite-seconds", 60);
    }

    public int clanTopPageSize() {
        return Math.max(1, cfg.getInt("clans.top-page-size", 10));
    }

    public int clanRankMaxLength() {
        return cfg.getInt("clans.rank-max-length", 16);
    }

    public int clanDescriptionMaxLength() {
        return cfg.getInt("clans.description-max-length", 120);
    }

    /** Статус с наибольшим порогом, который не больше рейтинга. */
    public String clanStatus(int rating) {
        ConfigurationSection s = cfg.getConfigurationSection("clans.statuses");
        if (s == null) return "<gray>Новички</gray>";
        int best = Integer.MIN_VALUE;
        String result = "<gray>Новички</gray>";
        for (String key : s.getKeys(false)) {
            int threshold;
            try {
                threshold = Integer.parseInt(key.trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (threshold <= rating && threshold > best) {
                best = threshold;
                result = s.getString(key, result);
            }
        }
        return result;
    }
}
