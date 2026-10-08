package ru.dscraft.mediaclans;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Роль в клане. Роли сортируются по ID (по алфавиту): чем раньше ID, тем роль старше.
 * Роль лидера ({@link #LEADER_ID}) есть всегда, у неё все права, её носит владелец клана.
 */
public class ClanRole {

    public static final String LEADER_ID = "!leader";
    public static final String DEFAULT_ID = "wdefault";

    private volatile String id;
    private volatile String name;
    /** Префикс роли (&-коды, hex, градиенты), показывается как звание: [Лидер]. */
    private volatile String prefix;
    private volatile Material icon;
    private final EnumSet<Perm> perms = EnumSet.noneOf(Perm.class);

    public ClanRole(String id, String name, String prefix, Material icon) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.name = name;
        this.prefix = prefix;
        this.icon = icon;
    }

    public String id() {
        return id;
    }

    /** Смена ID роли (только через Clan.changeRoleId - там перестраивается порядок ролей). */
    void id(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String prefix() {
        return prefix;
    }

    public void prefix(String prefix) {
        this.prefix = prefix;
    }

    public Material icon() {
        return icon;
    }

    public void icon(Material icon) {
        this.icon = icon;
    }

    public boolean leader() {
        return LEADER_ID.equals(id);
    }

    public synchronized Set<Perm> perms() {
        return EnumSet.copyOf(perms);
    }

    public synchronized boolean has(Perm perm) {
        return leader() || perms.contains(Perm.ALL) || perms.contains(perm);
    }

    public synchronized boolean hasOwn(Perm perm) {
        return leader() || perms.contains(perm);
    }

    public synchronized void toggle(Perm perm) {
        if (!perms.remove(perm)) perms.add(perm);
    }

    public synchronized void set(Set<Perm> set) {
        perms.clear();
        perms.addAll(set);
    }

    /** Сколько прав у роли (у лидера - все). */
    public synchronized int count() {
        return leader() || perms.contains(Perm.ALL) ? Perm.values().length : perms.size();
    }

    /** true - эта роль старше другой (ID раньше по алфавиту). */
    public boolean above(ClanRole other) {
        return other == null || id.compareTo(other.id) < 0;
    }
}
