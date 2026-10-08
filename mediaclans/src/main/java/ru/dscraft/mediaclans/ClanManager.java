package ru.dscraft.mediaclans;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
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
    private static final Pattern ROLE_ID = Pattern.compile("[a-z0-9_!]{1,16}");

    private final JavaPlugin plugin;
    private final Settings settings;
    private final File file;

    private final Map<String, Clan> clans = new ConcurrentHashMap<>();
    private final Map<UUID, String> byPlayer = new ConcurrentHashMap<>();
    /** Приглашения: кого пригласили -> (клан, кто, до какого времени). */
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    /** "убийца:жертва" -> время последнего засчитанного убийства (защита от фарма рейтинга). */
    private final Map<String, Long> killCooldowns = new ConcurrentHashMap<>();

    private volatile boolean dirty;

    public record Invite(String clanId, UUID inviter, String inviterName, long expiresAt) {
    }

    public ClanManager(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
    }

    public Settings settings() {
        return settings;
    }

    // ---------------- поиск ----------------

    public Clan getClan(UUID player) {
        String id = player == null ? null : byPlayer.get(player);
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
        return top().indexOf(clan) + 1;
    }

    public String status(Clan clan) {
        return settings.status(clan.rating());
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
        if (len < settings.nameMin() || len > settings.nameMax()) {
            return "Название должно быть от " + settings.nameMin() + " до " + settings.nameMax()
                    + " символов (без учёта цветов).";
        }
        if (!NAME.matcher(plain).matches()) return "В названии можно использовать только буквы, цифры и _.";
        if (!allowDecorations && ColorUtil.hasDecorations(ColorUtil.rich(raw))) {
            return "Жирный, курсив и другие стили в названии доступны с привилегии Ultra.";
        }
        return null;
    }

    public static boolean validRoleId(String id) {
        return id != null && ROLE_ID.matcher(id).matches();
    }

    // ---------------- изменения ----------------

    /** Роли нового клана: Лидер (все права) и Участник (клановый чат и история). */
    static void addDefaultRoles(Clan clan) {
        ClanRole leader = new ClanRole(ClanRole.LEADER_ID, "Лидер", "&#FF2B2BЛидер", Material.CRAFTER);
        leader.set(EnumSet.of(Perm.ALL));
        clan.putRole(leader);
        ClanRole member = new ClanRole(ClanRole.DEFAULT_ID, "Участник", "&#2BFFFFУчастник", Material.RABBIT_HIDE);
        member.set(EnumSet.of(Perm.CHAT, Perm.HISTORY));
        clan.putRole(member);
        clan.defaultRoleId(ClanRole.DEFAULT_ID);
    }

    public Clan create(Player owner, String rawName) {
        String id = toId(rawName);
        Clan clan = new Clan(id, rawName, owner.getUniqueId(), owner.getName(), System.currentTimeMillis());
        addDefaultRoles(clan);
        clan.slots(settings.freeSlots());
        ClanMember m = new ClanMember(owner.getUniqueId(), owner.getName(), ClanRole.LEADER_ID, System.currentTimeMillis());
        m.lastSeen(System.currentTimeMillis());
        clan.membersMap().put(owner.getUniqueId(), m);
        clan.log(HistoryEntry.Type.CREATE, owner.getName(), null, settings.historySize());
        clans.put(id, clan);
        byPlayer.put(owner.getUniqueId(), id);
        invites.remove(owner.getUniqueId());
        markDirty();
        return clan;
    }

    /** Переименование: меняется и ключ клана. */
    public void rename(Clan clan, String rawName) {
        String oldId = clan.id();
        clans.remove(oldId);
        String id = toId(rawName);
        clan.id(id);
        clan.name(rawName);
        clans.put(id, clan);
        for (UUID uuid : clan.membersMap().keySet()) byPlayer.put(uuid, id);
        invites.replaceAll((k, v) -> v.clanId().equals(oldId)
                ? new Invite(id, v.inviter(), v.inviterName(), v.expiresAt()) : v);
        markDirty();
    }

    public void disband(Clan clan) {
        clans.remove(clan.id());
        for (UUID uuid : clan.membersMap().keySet()) byPlayer.remove(uuid, clan.id());
        invites.values().removeIf(i -> i.clanId().equals(clan.id()));
        markDirty();
    }

    public void addMember(Clan clan, UUID uuid, String name) {
        ClanMember m = new ClanMember(uuid, name, clan.defaultRoleId(), System.currentTimeMillis());
        m.lastSeen(System.currentTimeMillis());
        clan.membersMap().put(uuid, m);
        byPlayer.put(uuid, clan.id());
        invites.remove(uuid);
        clan.log(HistoryEntry.Type.JOIN, name, null, settings.historySize());
        markDirty();
    }

    public void removeMember(Clan clan, UUID uuid) {
        clan.membersMap().remove(uuid);
        byPlayer.remove(uuid, clan.id());
        markDirty();
    }

    /** Передать владение: новый владелец получает роль лидера, старый - начальную роль. */
    public void transfer(Clan clan, UUID newOwner) {
        ClanMember old = clan.ownerMember();
        ClanMember target = clan.member(newOwner);
        if (target == null) return;
        if (old != null) old.roleId(clan.defaultRoleId());
        target.roleId(ClanRole.LEADER_ID);
        clan.owner(newOwner);
        clan.log(HistoryEntry.Type.TRANSFER, old == null ? "?" : old.name(), target.name(), settings.historySize());
        markDirty();
    }

    // ---------------- приглашения ----------------

    public void invite(UUID target, Clan clan, Player inviter) {
        invites.put(target, new Invite(clan.id(), inviter.getUniqueId(), inviter.getName(),
                System.currentTimeMillis() + settings.inviteSeconds() * 1000L));
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

    /** Можно ли засчитать убийство этой жертвы этим убийцей (не чаще раза в kill-cooldown-seconds). */
    /** "убийца:жертва" -> {время последнего убийства, сколько раз подряд убил в пределах кулдауна}. */
    private final Map<String, long[]> repeatKills = new ConcurrentHashMap<>();

    /**
     * Повторные убийства: 0 - первое (или после кулдауна), 1 - второе подряд в пределах kill-cooldown-seconds и т.д.
     */
    public int registerKill(UUID killer, UUID victim) {
        long cooldown = settings.killCooldownSeconds() * 1000L;
        String key = killer + ":" + victim;
        long now = System.currentTimeMillis();
        long[] prev = repeatKills.get(key);
        int repeat = prev != null && cooldown > 0 && now - prev[0] < cooldown ? (int) prev[1] + 1 : 0;
        repeatKills.put(key, new long[]{now, repeat});
        if (cooldown > 0) repeatKills.entrySet().removeIf(e -> now - e.getValue()[0] >= cooldown);
        return repeat;
    }

    public boolean tryCountKill(UUID killer, UUID victim) {
        long cooldown = settings.killCooldownSeconds() * 1000L;
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

    public boolean hasData() {
        return file.exists();
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
                Clan clan = readClan(id, s);
                if (clan != null) put(clan);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось загрузить клан " + id + " из clans.yml", e);
            }
        }
        plugin.getLogger().info("Загружено кланов: " + clans.size());
    }

    private void put(Clan clan) {
        clans.put(clan.id(), clan);
        for (UUID uuid : clan.membersMap().keySet()) byPlayer.put(uuid, clan.id());
    }

    private Clan readClan(String id, ConfigurationSection s) {
        Clan clan = new Clan(id, s.getString("name", id), UUID.fromString(s.getString("owner", "")),
                s.getString("creator", "?"), s.getLong("created", System.currentTimeMillis()));
        try {
            clan.joinType(Clan.JoinType.valueOf(s.getString("join-type", "INVITE")));
        } catch (IllegalArgumentException e) {
            clan.joinType(Clan.JoinType.INVITE);
        }
        clan.password(s.getString("password"));
        clan.description(s.getString("description"));
        clan.announcement(s.getString("announcement"));
        ItemStack icon = s.getItemStack("icon");
        if (icon != null && !icon.getType().isAir()) clan.icon(icon);
        if (clan.icon().getType() == Material.WHITE_BANNER) clan.icon(new ItemStack(Material.BELL)); // старая иконка по умолчанию
        clan.slots(Math.max(settings.freeSlots(), s.getInt("slots", settings.freeSlots())));
        clan.rating(s.getInt("rating"));
        clan.kills(s.getInt("kills"));
        clan.deaths(s.getInt("deaths"));
        clan.pvp(s.getBoolean("pvp", false));

        ConfigurationSection rs = s.getConfigurationSection("roles");
        if (rs != null) {
            for (String rid : rs.getKeys(false)) {
                ConfigurationSection r = rs.getConfigurationSection(rid);
                if (r == null) continue;
                Material icon2 = Material.matchMaterial(r.getString("icon", "PAPER"));
                ClanRole role = new ClanRole(rid, r.getString("name", rid), r.getString("prefix", rid),
                        icon2 == null || !icon2.isItem() ? Material.PAPER : icon2);
                EnumSet<Perm> perms = EnumSet.noneOf(Perm.class);
                for (String p : r.getStringList("perms")) {
                    try {
                        perms.add(Perm.valueOf(p));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                role.set(perms);
                clan.putRole(role);
            }
        }
        if (clan.role(ClanRole.LEADER_ID) == null || clan.roles().size() < 2) {
            ClanRole keepDefault = clan.role(ClanRole.DEFAULT_ID);
            addDefaultRoles(clan);
            if (keepDefault != null) clan.putRole(keepDefault);
        }
        clan.defaultRoleId(s.getString("default-role", ClanRole.DEFAULT_ID));

        ConfigurationSection ms = s.getConfigurationSection("members");
        if (ms != null) {
            for (String key : ms.getKeys(false)) {
                ConfigurationSection m = ms.getConfigurationSection(key);
                if (m == null) continue;
                UUID uuid = UUID.fromString(key);
                ClanMember member = new ClanMember(uuid, m.getString("name", "?"),
                        m.getString("role", clan.defaultRoleId()), m.getLong("joined"));
                member.rating(m.getInt("rating"));
                member.kills(m.getInt("kills"));
                member.deaths(m.getInt("deaths"));
                member.invited(m.getInt("invited"));
                member.kicked(m.getInt("kicked"));
                member.lastSeen(m.getLong("last-seen"));
                member.notifyJoins(m.getBoolean("notify", true));
                clan.membersMap().put(uuid, member);
            }
        }
        if (clan.membersMap().isEmpty()) return null;
        ClanMember owner = clan.ownerMember();
        if (owner != null) owner.roleId(ClanRole.LEADER_ID);

        for (Map<?, ?> h : s.getMapList("history")) {
            try {
                clan.history().add(new HistoryEntry(HistoryEntry.Type.valueOf(String.valueOf(h.get("type"))),
                        str(h.get("actor")), str(h.get("target")), Long.parseLong(String.valueOf(h.get("time")))));
            } catch (Exception ignored) {
            }
        }
        for (Map<?, ?> p : s.getMapList("pins")) {
            try {
                clan.pins().add(new Pin(str(p.get("author")), str(p.get("text")), Long.parseLong(String.valueOf(p.get("time")))));
            } catch (Exception ignored) {
            }
        }
        return clan;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Clan clan : clans.values()) {
            String p = "clans." + clan.id() + ".";
            yml.set(p + "name", clan.name());
            yml.set(p + "owner", clan.owner().toString());
            yml.set(p + "creator", clan.creatorName());
            yml.set(p + "created", clan.createdAt());
            yml.set(p + "join-type", clan.joinType().name());
            yml.set(p + "password", clan.password());
            yml.set(p + "description", clan.description());
            yml.set(p + "announcement", clan.announcement());
            yml.set(p + "icon", clan.icon());
            yml.set(p + "rating", clan.rating());
            yml.set(p + "kills", clan.kills());
            yml.set(p + "deaths", clan.deaths());
            yml.set(p + "pvp", clan.pvp());
            yml.set(p + "default-role", clan.defaultRoleId());
            yml.set(p + "slots", clan.slots());
            for (ClanRole r : clan.roles()) {
                String rp = p + "roles." + r.id() + ".";
                yml.set(rp + "name", r.name());
                yml.set(rp + "prefix", r.prefix());
                yml.set(rp + "icon", r.icon().name());
                List<String> perms = new ArrayList<>();
                for (Perm perm : r.perms()) perms.add(perm.name());
                yml.set(rp + "perms", perms);
            }
            for (ClanMember m : clan.membersMap().values()) {
                String mp = p + "members." + m.uuid() + ".";
                yml.set(mp + "name", m.name());
                yml.set(mp + "role", m.roleId());
                yml.set(mp + "joined", m.joinedAt());
                yml.set(mp + "rating", m.rating());
                yml.set(mp + "kills", m.kills());
                yml.set(mp + "deaths", m.deaths());
                yml.set(mp + "invited", m.invited());
                yml.set(mp + "kicked", m.kicked());
                yml.set(mp + "last-seen", m.lastSeen());
                yml.set(mp + "notify", m.notifyJoins());
            }
            List<Map<String, Object>> history = new ArrayList<>();
            for (HistoryEntry h : clan.history()) {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("type", h.type().name());
                map.put("actor", h.actor());
                if (h.target() != null) map.put("target", h.target());
                map.put("time", h.time());
                history.add(map);
            }
            yml.set(p + "history", history);
            List<Map<String, Object>> pins = new ArrayList<>();
            for (Pin pin : clan.pins()) {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("author", pin.author());
                map.put("text", pin.text());
                map.put("time", pin.time());
                pins.add(map);
            }
            yml.set(p + "pins", pins);
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

    // ---------------- перенос из DestroyChat ----------------

    /**
     * Один раз, если своего clans.yml ещё нет: кланы из plugins/DestroyChat/clans.yml.
     * Владелец -> Лидер, администраторы -> новая роль "Администратор", остальные -> Участник.
     * Звание участника становится префиксом его роли только если оно у всех одинаковое, иначе не переносится.
     */
    public int importFromDestroyChat() {
        File old = new File(plugin.getDataFolder().getParentFile(), "DestroyChat/clans.yml");
        if (!old.exists()) return 0;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(old);
        ConfigurationSection root = yml.getConfigurationSection("clans");
        if (root == null) return 0;
        int count = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Clan clan = new Clan(id, s.getString("name", id), UUID.fromString(s.getString("owner", "")),
                        s.getString("creator", "?"), s.getLong("created", System.currentTimeMillis()));
                clan.joinType(Clan.JoinType.INVITE);
                clan.description(s.getString("description"));
                Material icon = Material.matchMaterial(s.getString("icon", "WHITE_BANNER"));
                if (icon != null && icon.isItem() && !icon.isAir()) clan.icon(new ItemStack(icon));
                clan.rating(s.getInt("rating"));
                clan.kills(s.getInt("kills"));
                clan.deaths(s.getInt("deaths"));
                addDefaultRoles(clan);
                boolean hasAdmins = false;
                ConfigurationSection ms = s.getConfigurationSection("members");
                if (ms != null) {
                    for (String key : ms.getKeys(false)) {
                        ConfigurationSection m = ms.getConfigurationSection(key);
                        if (m == null) continue;
                        UUID uuid = UUID.fromString(key);
                        String oldRole = m.getString("role", "MEMBER");
                        String role = uuid.equals(clan.owner()) ? ClanRole.LEADER_ID
                                : "ADMIN".equals(oldRole) ? "admin" : ClanRole.DEFAULT_ID;
                        hasAdmins |= "admin".equals(role);
                        ClanMember member = new ClanMember(uuid, m.getString("name", "?"), role, m.getLong("joined"));
                        member.kills(m.getInt("kills"));
                        member.rating(m.getInt("kills") * settings.killRating());
                        clan.membersMap().put(uuid, member);
                    }
                }
                if (hasAdmins) {
                    ClanRole admin = new ClanRole("admin", "Администратор", "&bАдмин", Material.GOLDEN_HELMET);
                    admin.set(EnumSet.of(Perm.ANNOUNCE, Perm.INVITE, Perm.CHAT, Perm.ICON, Perm.DESCRIPTION,
                            Perm.KICK, Perm.JOIN_TYPE, Perm.HISTORY, Perm.SET_ROLE));
                    clan.putRole(admin);
                }
                if (clan.membersMap().isEmpty()) continue;
                clan.history().add(new HistoryEntry(HistoryEntry.Type.CREATE, clan.creatorName(), null, clan.createdAt()));
                put(clan);
                count++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось перенести клан " + id + " из DestroyChat", e);
            }
        }
        if (count > 0) save();
        return count;
    }
}
