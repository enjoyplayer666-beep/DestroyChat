package ru.dscraft.mediabans;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/** Активные баны и муты в plugins/MediaBans/punishments.yml. Читать можно из любого потока. */
public final class PunishStore {

    private final File file;
    private final Logger logger;
    private final Map<UUID, Punishment> bans = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();

    public PunishStore(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    private Map<UUID, Punishment> map(Punishment.Type type) {
        return type == Punishment.Type.BAN ? bans : mutes;
    }

    // ---------------- чтение ----------------

    /** Действующее наказание по UUID или null (истёкшее не считается). */
    public Punishment active(Punishment.Type type, UUID uuid) {
        Punishment p = map(type).get(uuid);
        return p == null || p.expired(System.currentTimeMillis()) ? null : p;
    }

    /** По нику без учёта регистра (для /unban и /checkban тех, кто давно не заходил). */
    public Punishment activeByName(Punishment.Type type, String name) {
        long now = System.currentTimeMillis();
        for (Punishment p : map(type).values()) {
            if (p.name().equalsIgnoreCase(name) && !p.expired(now)) return p;
        }
        return null;
    }

    /** Бан при входе: по UUID, а если UUID сменился - по нику. */
    public Punishment activeBan(UUID uuid, String name) {
        Punishment p = active(Punishment.Type.BAN, uuid);
        return p != null ? p : activeByName(Punishment.Type.BAN, name);
    }

    /** Все действующие, новые сверху. */
    public List<Punishment> all(Punishment.Type type) {
        long now = System.currentTimeMillis();
        List<Punishment> list = new ArrayList<>();
        for (Punishment p : map(type).values()) {
            if (!p.expired(now)) list.add(p);
        }
        list.sort(Comparator.comparingLong(Punishment::start).reversed());
        return list;
    }

    // ---------------- запись (основной поток) ----------------

    public void put(Punishment p) {
        map(p.type()).put(p.uuid(), p);
        save();
    }

    public Punishment remove(Punishment p) {
        Punishment removed = map(p.type()).remove(p.uuid());
        save();
        return removed;
    }

    /** Убрать истёкшие (раз в минуту). */
    public void cleanup() {
        long now = System.currentTimeMillis();
        boolean changed = bans.values().removeIf(p -> p.expired(now));
        changed |= mutes.values().removeIf(p -> p.expired(now));
        if (changed) save();
    }

    public void load() {
        bans.clear();
        mutes.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        read(yml.getConfigurationSection("bans"), Punishment.Type.BAN);
        read(yml.getConfigurationSection("mutes"), Punishment.Type.MUTE);
    }

    private void read(ConfigurationSection section, Punishment.Type type) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID uuid = UUID.fromString(key);
                map(type).put(uuid, new Punishment(type, uuid, s.getString("name", "?"), s.getString("actor", "?"),
                        s.getBoolean("staff"), s.getString("reason", ""), s.getLong("start"), s.getLong("end")));
            } catch (IllegalArgumentException e) {
                logger.warning("Пропущено наказание с неверным UUID: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        write(yml, "bans", bans);
        write(yml, "mutes", mutes);
        try {
            file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            logger.warning("Не удалось сохранить " + file.getName() + ": " + e.getMessage());
        }
    }

    private static void write(YamlConfiguration yml, String root, Map<UUID, Punishment> map) {
        yml.createSection(root);
        for (Punishment p : map.values()) {
            String path = root + "." + p.uuid();
            yml.set(path + ".name", p.name());
            yml.set(path + ".actor", p.actor());
            yml.set(path + ".staff", p.byStaff());
            yml.set(path + ".reason", p.reason());
            yml.set(path + ".start", p.start());
            yml.set(path + ".end", p.end());
        }
    }
}
