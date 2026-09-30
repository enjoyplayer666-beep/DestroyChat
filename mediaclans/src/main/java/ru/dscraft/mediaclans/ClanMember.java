package ru.dscraft.mediaclans;

import java.util.UUID;

/** Участник клана и его клановая статистика. */
public class ClanMember {

    private final UUID uuid;
    private volatile String name;
    private volatile String roleId;
    private final long joinedAt;
    private volatile int rating;
    private volatile int kills;
    private volatile int deaths;
    private volatile int invited;
    private volatile int kicked;
    private volatile long lastSeen;
    /** Показывать ли этому игроку сообщения о входе/выходе участников клана. */
    private volatile boolean notify = true;

    public ClanMember(UUID uuid, String name, String roleId, long joinedAt) {
        this.uuid = uuid;
        this.name = name;
        this.roleId = roleId;
        this.joinedAt = joinedAt;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String roleId() {
        return roleId;
    }

    public void roleId(String roleId) {
        this.roleId = roleId;
    }

    public long joinedAt() {
        return joinedAt;
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

    public int invited() {
        return invited;
    }

    public void invited(int invited) {
        this.invited = invited;
    }

    public int kicked() {
        return kicked;
    }

    public void kicked(int kicked) {
        this.kicked = kicked;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean notifyJoins() {
        return notify;
    }

    public void notifyJoins(boolean notify) {
        this.notify = notify;
    }

    /** Процент побед на ПВП за клан: убийства / (убийства + смерти). */
    public int winPercent() {
        int total = kills + deaths;
        return total == 0 ? 0 : Math.round(kills * 100f / total);
    }
}
