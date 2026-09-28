package ru.dscraft.destroychat.clan;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.dscraft.destroychat.DestroyChatPlugin;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.util.ColorUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Все кланы сервера: хранение в clans.yml, поиск клана игрока, приглашения, топ.
 * Изменения сохраняются раз в минуту (если что-то поменялось) и при выключении сервера.
 */
public class ClanManager {

    private static final Pattern NAME = Pattern.compile("[A-Za-zА-Яа-яЁё0-9_]+");

    private final DestroyChatPlugin plugin;
    private final ChatConfig config;
    private final File file;

    private final Map<String, Clan> clans = new ConcurrentHashMap<>();
    private final Map<UUID, String> byPlayer = new ConcurrentHashMap<>();
    /** Приглашения: кого пригласили -> (клан, до какого времени). */
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    /** "убийца:жертва" -> время последнего засчитанного убийства (защита от фарма рейтинга). */
    private final Map<String, Long> killCooldowns = new ConcurrentHashMap<>();

    private volatile boolean dirty;

    public record Invite(String clanId, String inviterName, long expiresAt) {
    }

    public ClanManager(DestroyChatPlugin plugin, ChatConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
    }

    public ChatConfig config() {
        return config;
    }

    // ---------------- поиск ----------------

    public Clan getClan(UUID player) {
        String id = byPlayer.get(player);
        return id == null ? null : clans.get(id);
    }

    public Clan getClan(Player player) {
        return getClan(player.getUniqueId());
    }

    /** Клан по названию (цвета и регистр не важны). */
    public Clan getById(String name) {
        if (name == null) return null;
        return clans.get(toId(name));
    }

    public List<Clan> all() {
        return new ArrayList<>(clans.values());
    }

    /** Кланы по рейтингу (при равенстве - по убийствам, потом по дате создания). */
    public List<Clan> top() {
        List<Clan> list = all();
        list.sort(Comparator.comparingInt((Clan c) -> -c.rating())
                .thenComparingInt(c -> -c.kills())
                .thenComparingLong(Clan::createdAt));
        return list;
    }

    /** Место клана в топе, начиная с 1. */
    public int place(Clan clan) {
        List<Clan> top = top();
        return top.indexOf(clan) + 1;
    }

    /** Статус клана по рейтингу из clans.statuses. */
    public String status(Clan clan) {
        return config.clanStatus(clan.rating());
    }

    // ---------------- названия ----------------

    public static String toId(String rawName) {
        return ColorUtil.plain(ColorUtil.rich(rawName)).trim().toLowerCase(Locale.ROOT);
    }

    /** @return текст ошибки или null, если название подходит */
    public String validateName(String raw, boolean allowDecorations) {
        if (raw == null || raw.isBlank()) return "Название не может быть пустым.";
        String plain = ColorUtil.plain(ColorUtil.rich(raw)).trim();
        int len = plain.codePointCount(0, plain.length());
        if (len < config.clanNameMinLength() || len > config.clanNameMaxLength()) {
            return "Название должно быть от " + config.clanNameMinLength() + " до "
                    + config.clanNameMaxLength() + " символов (без учёта цветов).";
        }
        if (!NAME.matcher(plain).matches()) {
            return "В названии можно использовать только буквы, цифры и _.";
        }
        if (!allowDecorations && ColorUtil.hasDecorations(ColorUtil.rich(raw))) {
            return "Жирный, курсив и другие стили в названии доступны с привилегии Ultra.";
        }
        return null;
    }

    // ---------------- изменения ----------------

    public Clan create(Player owner, String rawName) {
        String id = toId(rawName);
        Clan clan = new Clan(id, rawName, owner.getUniqueId(), owner.getName(), System.currentTimeMillis());
        clan.membersMap().put(owner.getUniqueId(),
                new ClanMember(owner.getUniqueId(), owner.getName(), ClanRole.OWNER, null, System.currentTimeMillis(), 0));
        clans.put(id, clan);
        byPlayer.put(owner.getUniqueId(), id);
        invites.remove(owner.getUniqueId());
        markDirty();
        return clan;
    }

    public void disband(Clan clan) {
        clans.remove(clan.id());
        for (UUID uuid : clan.membersMap().keySet()) {
            byPlayer.remove(uuid, clan.id());
        }
        invites.values().removeIf(i -> i.clanId().equals(clan.id()));
        markDirty();
    }

    public void addMember(Clan clan, UUID uuid, String name) {
        clan.membersMap().put(uuid, new ClanMember(uuid, name, ClanRole.MEMBER, null, System.currentTimeMillis(), 0));
        byPlayer.put(uuid, clan.id());
        invites.remove(uuid);
        markDirty();
    }

    public void removeMember(Clan clan, UUID uuid) {
        clan.membersMap().remove(uuid);
        byPlayer.remove(uuid, clan.id());
        markDirty();
    }

    /** Передать владение: старый владелец становится администратором. */
    public void transfer(Clan clan, UUID newOwner) {
        ClanMember old = clan.ownerMember();
        ClanMember target = clan.member(newOwner);
        if (target == null) return;
        if (old != null) old.role(ClanRole.ADMIN);
        target.role(ClanRole.OWNER);
        clan.owner(newOwner);
        markDirty();
    }

    // ---------------- приглашения ----------------

    public void invite(UUID target, Clan clan, String inviterName) {
        invites.put(target, new Invite(clan.id(), inviterName,
                System.currentTimeMillis() + config.clanInviteSeconds() * 1000L));
    }

    /** Действующее приглашение игрока или null. */
    public Invite getInvite(UUID target) {
        Invite invite = invites.get(target);
        if (invite == null) return null;
        if (invite.expiresAt() < System.currentTimeMillis() || !clans.containsKey(invite.clanId())) {
            invites.remove(target);
            return null;
        }
        return invite;
    }

    public void removeInvite(UUID target) {
        invites.remove(target);
    }

    // ---------------- убийства ----------------

    /**
     * Можно ли засчитать убийство этой жертвы этим убийцей (не чаще раза в kill-cooldown-seconds).
     * Если можно - запоминает время.
     */
    public boolean tryCountKill(UUID killer, UUID victim) {
        long cooldown = config.clanKillCooldownSeconds() * 1000L;
        String key = killer + ":" + victim;
        long now = System.currentTimeMillis();
        if (cooldown > 0) {
            Long last = killCooldowns.get(key);
            if (last != null && now - last < cooldown) return false;
            killCooldowns.entrySet().removeIf(e -> now - e.getValue() >= cooldown);
        }
        killCooldowns.put(key, now);
        return true;
    }

    // ---------------- хранение ----------------

    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void load() {
        clans.clear();
        byPlayer.clear();
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("clans");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Clan clan = new Clan(id, s.getString("name", id), UUID.fromString(s.getString("owner", "")),
                        s.getString("creator", "?"), s.getLong("created", System.currentTimeMillis()));
                clan.open(s.getBoolean("open", false));
                clan.description(s.getString("description"));
                Material icon = Material.matchMaterial(s.getString("icon", "WHITE_BANNER"));
                if (icon != null && icon.isItem() && !icon.isAir()) clan.icon(icon);
                clan.rating(s.getInt("rating"));
                clan.kills(s.getInt("kills"));
                clan.deaths(s.getInt("deaths"));

                ConfigurationSection ms = s.getConfigurationSection("members");
                if (ms != null) {
                    for (String key : ms.getKeys(false)) {
                        ConfigurationSection m = ms.getConfigurationSection(key);
                        if (m == null) continue;
                        UUID uuid = UUID.fromString(key);
                        ClanRole role;
                        try {
                            role = ClanRole.valueOf(m.getString("role", "MEMBER"));
                        } catch (IllegalArgumentException e) {
                            role = ClanRole.MEMBER;
                        }
                        if (uuid.equals(clan.owner())) role = ClanRole.OWNER;
                        else if (role == ClanRole.OWNER) role = ClanRole.ADMIN;
                        clan.membersMap().put(uuid, new ClanMember(uuid, m.getString("name", "?"), role,
                                m.getString("rank"), m.getLong("joined"), m.getInt("kills")));
                        byPlayer.put(uuid, id);
                    }
                }
                if (clan.membersMap().isEmpty()) continue;
                clans.put(id, clan);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось загрузить клан " + id + " из clans.yml", e);
            }
        }
        plugin.getLogger().info("Загружено кланов: " + clans.size());
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Clan clan : clans.values()) {
            String p = "clans." + clan.id() + ".";
            yml.set(p + "name", clan.name());
            yml.set(p + "owner", clan.owner().toString());
            yml.set(p + "creator", clan.creatorName());
            yml.set(p + "created", clan.createdAt());
            yml.set(p + "open", clan.open());
            yml.set(p + "description", clan.description());
            yml.set(p + "icon", clan.icon().name());
            yml.set(p + "rating", clan.rating());
            yml.set(p + "kills", clan.kills());
            yml.set(p + "deaths", clan.deaths());
            for (ClanMember m : clan.membersMap().values()) {
                String mp = p + "members." + m.uuid() + ".";
                yml.set(mp + "name", m.name());
                yml.set(mp + "role", m.role().name());
                yml.set(mp + "rank", m.rank());
                yml.set(mp + "joined", m.joinedAt());
                yml.set(mp + "kills", m.kills());
            }
        }
        dirty = false;
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить clans.yml", e);
        }
    }
}
