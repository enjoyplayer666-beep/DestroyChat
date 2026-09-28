package ru.dscraft.destroychat.clan;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клан. Поля читаются из асинхронного чата, поэтому всё volatile / concurrent,
 * а меняется только из основного потока.
 */
public class Clan {

    /** Название без цветов в нижнем регистре - ключ клана. */
    private final String id;
    /** Название как его ввёл владелец: с &-цветами и градиентами. */
    private volatile String name;
    private volatile UUID owner;
    private final String creatorName;
    private final long createdAt;
    private volatile boolean open;
    private volatile String description;
    private volatile Material icon;
    private volatile int rating;
    private volatile int kills;
    private volatile int deaths;
    private final Map<UUID, ClanMember> members = new ConcurrentHashMap<>();

    public Clan(String id, String name, UUID owner, String creatorName, long createdAt) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.creatorName = creatorName;
        this.createdAt = createdAt;
        this.icon = Material.WHITE_BANNER;
    }

    public String id() {
        return id;
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

    public boolean open() {
        return open;
    }

    public void open(boolean open) {
        this.open = open;
    }

    public String description() {
        return description;
    }

    public void description(String description) {
        this.description = description;
    }

    public Material icon() {
        return icon;
    }

    public void icon(Material icon) {
        this.icon = icon;
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

    /** Процент побед на ПВП: убийства / (убийства + смерти). */
    public int winPercent() {
        int total = kills + deaths;
        return total == 0 ? 0 : Math.round(kills * 100f / total);
    }

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

    /** Участники по порядку: владелец, админы, остальные; внутри - по дате вступления. */
    public List<ClanMember> sortedMembers() {
        List<ClanMember> list = new ArrayList<>(members.values());
        list.sort(Comparator.comparingInt((ClanMember m) -> m.role().ordinal())
                .thenComparingLong(ClanMember::joinedAt));
        return list;
    }

    public List<Player> onlineMembers() {
        List<Player> out = new ArrayList<>();
        Collection<ClanMember> all = members.values();
        for (ClanMember m : all) {
            Player p = Bukkit.getPlayer(m.uuid());
            if (p != null) out.add(p);
        }
        return out;
    }
}
