package ru.dscraft.mediabans;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Кто что может.
 * Команда проекта (опы, консоль, группы staff-groups, право mediabans.staff) - всё без ограничений.
 * Донатеры - по группам из groups: срок мута/бана не больше своего, /ban, /mute и /kick нельзя.
 */
public final class Access {

    public enum Action { BAN, TEMPBAN, MUTE, TEMPMUTE, KICK, UNBAN, UNMUTE, CHECK, BANLIST }

    /** Что можно донатерской группе; mute/ban - максимальный срок в мс, 0 - нельзя. */
    public record Limits(long mute, long ban, boolean unmute, boolean unban, boolean check, boolean banlist) {
    }

    public static final String STAFF_PERMISSION = "mediabans.staff";

    private final List<String> staffGroups = new ArrayList<>();
    private final Map<String, Limits> groups = new LinkedHashMap<>();

    public void load(FileConfiguration cfg, Logger logger) {
        staffGroups.clear();
        for (String g : cfg.getStringList("staff-groups")) staffGroups.add(g.toLowerCase(Locale.ROOT));
        groups.clear();
        ConfigurationSection section = cfg.getConfigurationSection("groups");
        if (section == null) return;
        for (String group : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(group);
            if (s == null) continue;
            groups.put(group.toLowerCase(Locale.ROOT), new Limits(
                    limit(s.getString("tempmute", "0"), group, logger),
                    limit(s.getString("tempban", "0"), group, logger),
                    s.getBoolean("unmute"), s.getBoolean("unban"), s.getBoolean("check"), s.getBoolean("banlist")));
        }
    }

    private static long limit(String raw, String group, Logger logger) {
        if (raw == null || raw.isBlank() || raw.trim().equals("0")) return 0;
        long ms = Durations.parse(raw);
        if (ms < 0) {
            logger.warning("Неверное время '" + raw + "' у группы " + group + " - считаю как 0 (нельзя).");
            return 0;
        }
        return ms;
    }

    /** Команда проекта: консоль, оп, право mediabans.staff или группа из staff-groups. */
    public boolean isStaff(CommandSender sender) {
        if (!(sender instanceof Player p)) return true;
        if (p.isOp() || p.hasPermission(STAFF_PERMISSION)) return true;
        for (String g : staffGroups) {
            if (p.hasPermission("group." + g)) return true;
        }
        return false;
    }

    /** Права донатера: первая подходящая группа сверху вниз, null - никаких. */
    public Limits limits(CommandSender sender) {
        if (!(sender instanceof Player p)) return null;
        for (Map.Entry<String, Limits> e : groups.entrySet()) {
            if (p.hasPermission("group." + e.getKey())) return e.getValue();
        }
        return null;
    }

    public boolean can(CommandSender sender, Action action) {
        if (isStaff(sender)) return true;
        Limits l = limits(sender);
        if (l == null) return false;
        return switch (action) {
            case TEMPMUTE -> l.mute() > 0;
            case TEMPBAN -> l.ban() > 0;
            case UNMUTE -> l.unmute();
            case UNBAN -> l.unban();
            case CHECK -> l.check();
            case BANLIST -> l.banlist();
            default -> false; // /ban, /mute, /kick - только команда проекта
        };
    }

    /** Цель из команды проекта? Для тех, кто не в сети, группы смотрятся через LuckPerms. */
    public CompletableFuture<Boolean> isStaffTarget(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return CompletableFuture.completedFuture(isStaff(online));
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.isOp()) return CompletableFuture.completedFuture(true);
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms") && !staffGroups.isEmpty()) {
            return LuckPermsHook.inAnyGroup(uuid, staffGroups).exceptionally(e -> false);
        }
        return CompletableFuture.completedFuture(false);
    }
}
