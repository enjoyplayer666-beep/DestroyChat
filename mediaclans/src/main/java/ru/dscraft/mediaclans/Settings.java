package ru.dscraft.mediaclans;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.TreeMap;

/** Настройки из config.yml. */
public final class Settings {

    private final JavaPlugin plugin;

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public String chatTag() {
        return cfg().getString("chat-tag", "<dark_gray><bold>[</bold></dark_gray><clan><dark_gray><bold>]</bold></dark_gray> ");
    }

    public String prefix() {
        return cfg().getString("clan-prefix", "<#F6993C>Кланы</#F6993C> <#666666>•</#666666> ");
    }

    public String chatSymbol() {
        return cfg().getString("clan-chat.symbol", "%");
    }

    public String chatFormat() {
        return cfg().getString("clan-chat.style", "<#F6993C>Клан</#F6993C> <#666666>»</#666666> <#666666>[</#666666><role><#666666>]</#666666> <#00FFE3><name></#00FFE3><#666666>:</#666666> <white><message></white>");
    }

    public String createPermission() {
        // новый ключ: старый create-permission: "" во всех конфигах пускал всех
        return cfg().getString("create-clan-permission", "mediaclans.create");
    }

    public int killRating() {
        return cfg().getInt("kill-rating", 15);
    }

    public String boosterPermission() {
        return cfg().getString("booster-permission", "");
    }

    public int deathRating() {
        return cfg().getInt("death-rating", 0);
    }

    public int killCooldownSeconds() {
        return cfg().getInt("kill-cooldown-seconds", 300);
    }

    /** Меньше стольких ударов по жертве - "Бой был слишком легким" (1 рейтинга). */
    public int easyFightHits() {
        return cfg().getInt("easy-fight-hits", 3);
    }

    /** На сколько меньше рейтинга за каждое повторное убийство того же игрока. */
    public int repeatKillPenalty() {
        return cfg().getInt("repeat-kill-penalty", 2);
    }

    public String killMessage() {
        return cfg().getString("kill-message", "");
    }

    public String deathMessage() {
        return cfg().getString("death-message", "");
    }

    public int nameMin() {
        return cfg().getInt("name-min-length", 3);
    }

    public int nameMax() {
        return cfg().getInt("name-max-length", 16);
    }

    public int maxMembers() {
        return Math.max(1, cfg().getInt("max-members", 50));
    }

    /** Сколько слотов участников открыто у нового клана. */
    public int freeSlots() {
        return Math.max(1, cfg().getInt("free-slots", 14));
    }

    /** Всего слотов (открытые + купленные), по 21 на страницу. */
    public int maxSlots() {
        return Math.max(freeSlots(), cfg().getInt("max-slots", 63));
    }

    /** Цена слота в коинах (пока только надпись). */
    public int slotPrice() {
        return cfg().getInt("slot-price", 5);
    }

    public int inviteSeconds() {
        return cfg().getInt("invite-seconds", 60);
    }

    public int inputSeconds() {
        return Math.max(5, cfg().getInt("input-seconds", 30));
    }

    public int topPageSize() {
        return Math.max(1, cfg().getInt("top-page-size", 10));
    }

    public int descriptionMax() {
        return cfg().getInt("description-max-length", 120);
    }

    public int announcementMax() {
        return cfg().getInt("announcement-max-length", 120);
    }

    public int rolePrefixMax() {
        return cfg().getInt("role-prefix-max-length", 16);
    }

    public int maxRoles() {
        return Math.max(2, Math.min(15, cfg().getInt("max-roles", 15)));
    }

    public int maxPins() {
        return Math.max(1, Math.min(28, cfg().getInt("max-pins", 21)));
    }

    public int historySize() {
        return Math.max(1, Math.min(28, cfg().getInt("history-size", 28)));
    }

    /** Статус клана по рейтингу из statuses. */
    public String status(int rating) {
        ConfigurationSection s = cfg().getConfigurationSection("statuses");
        if (s == null) return "<gray>—</gray>";
        TreeMap<Integer, String> map = new TreeMap<>();
        for (String k : s.getKeys(false)) {
            try {
                map.put(Integer.parseInt(k.trim()), s.getString(k));
            } catch (NumberFormatException ignored) {
            }
        }
        Map.Entry<Integer, String> e = map.floorEntry(rating);
        return e == null ? "<gray>—</gray>" : e.getValue();
    }
}
