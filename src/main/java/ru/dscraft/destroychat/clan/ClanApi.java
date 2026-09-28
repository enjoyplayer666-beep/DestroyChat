package ru.dscraft.destroychat.clan;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;

/**
 * Для других плагинов (StatPlugin берёт отсюда клан игрока для /stat через рефлексию).
 */
public final class ClanApi {

    private static volatile ClanManager manager;

    private ClanApi() {
    }

    public static void init(ClanManager clanManager) {
        manager = clanManager;
    }

    /** Название клана игрока с цветами (§-коды), или null - игрок не в клане. */
    public static String clanName(UUID player) {
        ClanManager m = manager;
        Clan clan = m == null ? null : m.getClan(player);
        return clan == null ? null : LegacyComponentSerializer.legacySection().serialize(ClanText.name(clan));
    }
}
