package ru.dscraft.mediaclans;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Клан. Поля читаются из асинхронного чата, поэтому всё volatile / concurrent,
 * а меняется только из основного потока.
 */
public class Clan {

    public enum JoinType { INVITE, PASSWORD, OPEN }

    /** Название без цветов в нижнем регистре - ключ клана. */
    private volatile String id;
    /** Название как его ввёл владелец: с &-цветами и градиентами. */
    private volatile String name;
    private volatile UUID owner;
    private final String creatorName;
    private final long createdAt;
    private volatile JoinType joinType = JoinType.INVITE;
    private volatile String password;
    private volatile String description;
    private volatile String announcement;
    private volatile ItemStack icon = new ItemStack(Material.BELL);
    private volatile int rating;
    private volatile int kills;
    private volatile int deaths;
    /** Огонь по своим. */
    private volatile boolean pvp;
    private volatile String defaultRoleId = ClanRole.DEFAULT_ID;
    /** Открытые слоты участников (остальные - купить). */
    private volatile int slots = 14;
    private final Map<UUID, ClanMember> members = new ConcurrentHashMap<>();
    /** Роли по ID: TreeMap - сразу по порядку (старшие первые). Меняется под synchronized(roles). */
    private final TreeMap<String, ClanRole> roles = new TreeMap<>();
    private final List<HistoryEntry> history = new CopyOnWriteArrayList<>();
    private final List<Pin> pins = new CopyOnWriteArrayList<>();

    public Clan(String id, String name, UUID owner, String creatorName, long createdAt) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.creatorName = creatorName;
        this.createdAt = createdAt;
    }

    public String id() {
        return id;
    }

    void id(String id) {
        this.id = id;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public UUID owner() {
        return owner;
    }

    public void owner(UUID owner) {
        this.owner = owner;
    }

    public String creatorName() {
        return creatorName;
    }

    public long createdAt() {
        return createdAt;
    }

    public JoinType joinType() {
        return joinType;
    }

    public void joinType(JoinType joinType) {
        this.joinType = joinType;
    }

    public String password() {
        return password;
    }

    public void password(String password) {
        this.password = password;
    }

    public String description() {
        return description;
    }

    public void description(String description) {
        this.description = description;
    }

    public String announcement() {
        return announcement;
    }

    public void announcement(String announcement) {
        this.announcement = announcement;
    }

    public ItemStack icon() {
        return icon.clone();
    }

    public void icon(ItemStack icon) {
        ItemStack copy = icon.clone();
        copy.setAmount(1);
        this.icon = copy;
    }

    public int rating() {
        return rating;
    }

    public void rating(int rating) {
        this.rating = Math.max(0, rating);
    }

    public int kills() {
        return kills;
    }

    public void kills(int kills) {
        this.kills = kills;
    }

    public int deaths() {
        return deaths;
    }

    public void deaths(int deaths) {
        this.deaths = deaths;
    }

    public boolean pvp() {
        return pvp;
    }

    public void pvp(boolean pvp) {
        this.pvp = pvp;
    }

    /** Процент побед на ПВП: убийства / (убийства + смерти). */
    public int winPercent() {
        int total = kills + deaths;
        return total == 0 ? 0 : Math.round(kills * 100f / total);
    }

    // ---------------- роли ----------------

    public ClanRole role(String roleId) {
        synchronized (roles) {
            return roleId == null ? null : roles.get(roleId);
        }
    }

    public List<ClanRole> roles() {
        synchronized (roles) {
            return new ArrayList<>(roles.values());
        }
    }

    public void putRole(ClanRole role) {
        synchronized (roles) {
            roles.put(role.id(), role);
        }
    }

    /** Новый ID роли: участники и начальная роль переходят на него. */
    public void changeRoleId(String oldId, String newId) {
        synchronized (roles) {
            ClanRole role = roles.remove(oldId);
            if (role == null) return;
            role.id(newId);
            roles.put(role.id(), role);
        }
        for (ClanMember m : members.values()) {
            if (oldId.equals(m.roleId())) m.roleId(newId);
        }
        if (oldId.equals(defaultRoleId)) defaultRoleId = newId;
    }

    public int slots() {
        return slots;
    }

    public void slots(int slots) {
        this.slots = slots;
    }

    public void removeRole(String roleId) {
        synchronized (roles) {
            roles.remove(roleId);
        }
    }

    public ClanRole defaultRole() {
        ClanRole r = role(defaultRoleId);
        if (r != null && !r.leader()) return r;
        for (ClanRole role : roles()) {
            if (!role.leader()) return role;
        }
        return role(ClanRole.LEADER_ID);
    }

    public String defaultRoleId() {
        return defaultRole() == null ? defaultRoleId : defaultRole().id();
    }

    public void defaultRoleId(String id) {
        this.defaultRoleId = id;
    }

    /** Роль участника (владелец - всегда лидер, пропавшая роль - начальная). */
    public ClanRole roleOf(ClanMember member) {
        if (member == null) return null;
        if (member.uuid().equals(owner)) return role(ClanRole.LEADER_ID);
        ClanRole r = role(member.roleId());
        return r == null || r.leader() ? defaultRole() : r;
    }

    /** Есть ли у игрока право в этом клане (владелец может всё). */
    public boolean has(UUID player, Perm perm) {
        if (player == null) return false;
        if (player.equals(owner)) return true;
        ClanMember m = members.get(player);
        ClanRole r = roleOf(m);
        return r != null && r.has(perm);
    }

    /** Может ли actor управлять участником target: владелец - всеми, остальные - теми, у кого роль младше. */
    public boolean canManage(UUID actor, ClanMember target) {
        if (actor == null || target == null || actor.equals(target.uuid())) return false;
        if (actor.equals(owner)) return true;
        if (target.uuid().equals(owner)) return false;
        ClanRole mine = roleOf(members.get(actor));
        return mine != null && mine.above(roleOf(target));
    }

    // ---------------- участники ----------------

    public Map<UUID, ClanMember> membersMap() {
        return members;
    }

    public ClanMember member(UUID uuid) {
        return uuid == null ? null : members.get(uuid);
    }

    public ClanMember ownerMember() {
        return members.get(owner);
    }

    public String ownerName() {
        ClanMember m = ownerMember();
        return m == null ? "?" : m.name();
    }

    public int size() {
        return members.size();
    }

    /** Участники по порядку ролей (старшие первые), внутри роли - по дате вступления. */
    public List<ClanMember> sortedMembers() {
        List<ClanMember> list = new ArrayList<>(members.values());
        list.sort(Comparator.comparing((ClanMember m) -> {
                    ClanRole r = roleOf(m);
                    return r == null ? "~" : r.id();
                })
                .thenComparingLong(ClanMember::joinedAt));
        return list;
    }

    public List<Player> onlineMembers() {
        List<Player> out = new ArrayList<>();
        for (ClanMember m : members.values()) {
            Player p = Bukkit.getPlayer(m.uuid());
            if (p != null) out.add(p);
        }
        return out;
    }

    // ---------------- история и закреплённые ----------------

    public List<HistoryEntry> history() {
        return history;
    }

    public void log(HistoryEntry.Type type, String actor, String target, int max) {
        history.add(new HistoryEntry(type, actor, target, System.currentTimeMillis()));
        while (history.size() > max) history.remove(0);
    }

    public List<Pin> pins() {
        return pins;
    }
}
