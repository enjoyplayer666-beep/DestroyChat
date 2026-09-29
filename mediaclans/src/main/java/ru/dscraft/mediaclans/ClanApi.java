package ru.dscraft.mediaclans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Для других плагинов через рефлексию, без зависимости при сборке:
 * DestroyChat берёт отсюда тег клана для чата, StatPlugin - название клана для /stat.
 */
public final class ClanApi {

    private static volatile ClanManager manager;

    private ClanApi() {
    }

    static void init(ClanManager clanManager) {
        manager = clanManager;
    }

    /** Название клана игрока с цветами (§-коды), или null - игрок не в клане. */
    public static String clanName(UUID player) {
        ClanManager m = manager;
        Clan clan = m == null ? null : m.getClan(player);
        return clan == null ? null : LegacyComponentSerializer.legacySection().serialize(ClanText.name(clan));
    }

    /** Тег клана для чата ([Клан] с карточкой при наведении), или null - игрок не в клане. Можно из асинхронного чата. */
    public static Component chatTag(Player player) {
        ClanManager m = manager;
        Clan clan = m == null ? null : m.getClan(player);
        return clan == null ? null : ClanText.chatTag(clan, m);
    }

    /**
     * Топ кланов по рейтингу для голограмм MediaTops: каждый элемент - {ID, название с §-цветами, рейтинг (Integer)}.
     */
    public static List<Object[]> top(int limit) {
        ClanManager m = manager;
        List<Object[]> out = new ArrayList<>();
        if (m == null) return out;
        for (Clan clan : m.all()) {
            out.add(new Object[]{clan.id(), LegacyComponentSerializer.legacySection().serialize(ClanText.name(clan)), clan.rating()});
        }
        out.sort((a, b) -> Integer.compare((Integer) b[2], (Integer) a[2]));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** Все кланы: ID -> {название с §-цветами, рейтинг}. */
    public static List<Object[]> all() {
        return top(Integer.MAX_VALUE);
    }

    /** ID клана игрока (название без цветов) или null. */
    public static String clanId(UUID player) {
        ClanManager m = manager;
        Clan clan = m == null ? null : m.getClan(player);
        return clan == null ? null : clan.id();
    }
}
