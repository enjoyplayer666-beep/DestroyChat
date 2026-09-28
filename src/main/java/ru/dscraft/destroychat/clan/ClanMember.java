package ru.dscraft.destroychat.clan;

import java.util.UUID;

/** Участник клана. */
public class ClanMember {

    private final UUID uuid;
    private volatile String name;
    private volatile ClanRole role;
    /** Звание, которое выдал владелец/админ (MiniMessage-безопасный текст), null - нет. */
    private volatile String rank;
    private final long joinedAt;
    private volatile int kills;

    public ClanMember(UUID uuid, String name, ClanRole role, String rank, long joinedAt, int kills) {
        this.uuid = uuid;
        this.name = name;
        this.role = role;
        this.rank = rank;
        this.joinedAt = joinedAt;
        this.kills = kills;
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

    public ClanRole role() {
        return role;
    }

    public void role(ClanRole role) {
        this.role = role;
    }

    public String rank() {
        return rank;
    }

    public void rank(String rank) {
        this.rank = rank;
    }

    public long joinedAt() {
        return joinedAt;
    }

    public int kills() {
        return kills;
    }

    public void addKill() {
        kills++;
    }
}
