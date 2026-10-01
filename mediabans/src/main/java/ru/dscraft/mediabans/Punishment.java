package ru.dscraft.mediabans;

import java.util.UUID;

/**
 * Бан или мут игрока.
 *
 * @param byStaff выдала команда проекта (донатеры такое наказание не перебивают и не снимают)
 * @param end     когда закончится, 0 - навсегда
 */
public record Punishment(Type type, UUID uuid, String name, String actor, boolean byStaff, String reason, long start, long end) {

    public enum Type { BAN, MUTE }

    public boolean permanent() {
        return end <= 0;
    }

    public boolean expired(long now) {
        return end > 0 && now >= end;
    }

    /** Сколько осталось, мс. */
    public long left(long now) {
        return Math.max(0, end - now);
    }
}
