package ru.dscraft.mediaclans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Тексты кланов: карточки клана и участника, тег в чате, топ. */
public final class ClanText {

    private static final String BOX = "<#6F63C9>";
    private static final String BOX_TOP = BOX + "┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐";
    private static final String BOX_BOTTOM = BOX + "└ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘";
    private static final String BOX_LINE = BOX + "│</#6F63C9> ";
    private static final String TOP_BORDER = "<#7B6FE0>";
    private static final String TOP_LINE = "<#7B6FE0>│</#7B6FE0> ";
    private static final String DASHES = "- - - - - - - - - - - ";
    private static final String DASHES_MID = "- - - - - - - ";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());

    private ClanText() {
    }

    public static String date(long millis) {
        return DATE.format(Instant.ofEpochMilli(millis));
    }

    /** "5 мин. назад", "2 ч. 12 мин. назад", "3 дн. назад". */
    public static String ago(long millis) {
        long minutes = Math.max(0, (System.currentTimeMillis() - millis) / 60_000L);
        if (minutes < 1) return "только что";
        if (minutes < 60) return minutes + " мин. назад";
        long hours = minutes / 60;
        if (hours < 24) return hours + " ч. " + (minutes % 60) + " мин. назад";
        return hours / 24 + " дн. назад";
    }

    /** Название клана с его цветами (по умолчанию белое). */
    public static Component name(Clan clan) {
        return Component.empty().color(NamedTextColor.WHITE).append(ColorUtil.rich(clan.name()));
    }

    public static Component rolePrefix(ClanRole role) {
        return role == null ? Component.text("?", NamedTextColor.GRAY)
                : Component.empty().color(NamedTextColor.WHITE).append(ColorUtil.rich(role.prefix()));
    }

    private static String joinType(Clan clan) {
        return clan.joinType() == Clan.JoinType.PASSWORD ? "<gold>По паролю</gold>" : "<red>По приглашению</red>";
    }

    private static void description(List<Component> lines, Clan clan) {
        lines.add(ColorUtil.parse("<white>Описание:</white>"));
        String desc = clan.description();
        if (desc == null || desc.isBlank()) {
            lines.add(ColorUtil.parse("<gray>  Нет описания...</gray>"));
        } else {
            for (String line : wrap(desc, 36)) {
                lines.add(ColorUtil.parse("<gray>  <line></gray>", Placeholder.unparsed("line", line)));
            }
        }
    }

    private static TagResolver clanTags(Clan clan, ClanManager manager) {
        return TagResolver.resolver(
                Placeholder.unparsed("creator", clan.creatorName()),
                Placeholder.unparsed("date", date(clan.createdAt())),
                Placeholder.unparsed("owner", clan.ownerName()),
                Placeholder.unparsed("count", String.valueOf(clan.size())),
                Placeholder.unparsed("online", String.valueOf(clan.onlineMembers().size())),
                Placeholder.unparsed("rating", String.valueOf(clan.rating())),
                Placeholder.unparsed("wins", String.valueOf(clan.winPercent())),
                Placeholder.unparsed("kills", String.valueOf(clan.kills())),
                Placeholder.component("clan", name(clan)),
                Placeholder.component("status", ColorUtil.parse(manager.status(clan))));
    }

    /** Карточка клана в списке кланов, в чате и в топе. */
    public static List<Component> card(Clan clan, ClanManager manager, boolean clickHint) {
        TagResolver r = clanTags(clan, manager);
        List<Component> lines = new ArrayList<>();
        lines.add(ColorUtil.parse("<white>Создал <aqua><creator></aqua>, дата <date></white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse("<white>Владелец клана: <aqua><owner></aqua></white>", r));
        lines.add(ColorUtil.parse("<white>Тип вступления: " + joinType(clan) + "</white>", r));
        lines.add(ColorUtil.parse("<white>Игроков в клане: <yellow><count> ☺</yellow></white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse(BOX_TOP));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Рейтинг: <gold><rating> ★</gold></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Побед на ПВП: <green><wins>%</green></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Убито игроков: <red><kills></red></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Статус: <status></white>", r));
        lines.add(ColorUtil.parse(BOX_BOTTOM));
        lines.add(Component.empty());
        description(lines, clan);
        if (clickHint) {
            lines.add(Component.empty());
            lines.add(ColorUtil.parse("<gray>Нажми чтобы посмотреть клан.</gray>"));
        }
        return lines;
    }

    /** "Информация о клане" в меню клана. */
    public static List<Component> info(Clan clan, ClanManager manager) {
        TagResolver r = clanTags(clan, manager);
        List<Component> lines = new ArrayList<>();
        lines.add(ColorUtil.parse("<white>Игрок <aqua><owner></aqua> владелец клана</white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse("<white>Участников в клане: <yellow><count></yellow> <gray>(Онлайн: <online>)</gray></white>", r));
        lines.add(ColorUtil.parse("<white>Тип вступления: " + joinType(clan) + "</white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse(BOX_TOP));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Название: <gray>[</gray><clan><gray>]</gray></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Рейтинг: <gold><rating></gold></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Побед на ПВП: <green><wins>%</green></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Убито игроков: <red><kills></red></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Статус клана: <status></white>", r));
        lines.add(ColorUtil.parse(BOX_BOTTOM));
        lines.add(Component.empty());
        description(lines, clan);
        lines.add(Component.empty());
        lines.add(ColorUtil.parse("<white>Дата создания <date></white>", r));
        return lines;
    }

    /** Название карточки участника: "[Лидер] ник [Оффлайн]". */
    public static Component memberTitle(Clan clan, ClanMember m) {
        boolean online = Bukkit.getPlayer(m.uuid()) != null;
        return ColorUtil.parse("<gray>[</gray><role><gray>]</gray> <white><name></white> "
                        + (online ? "<green>[Онлайн]</green>" : "<gray>[Оффлайн]</gray>"),
                Placeholder.component("role", rolePrefix(clan.roleOf(m))),
                Placeholder.unparsed("name", m.name()));
    }

    /** Карточка участника в меню клана. */
    public static List<Component> member(Clan clan, ClanMember m, boolean clickHint) {
        TagResolver r = TagResolver.resolver(
                Placeholder.unparsed("joined", date(m.joinedAt())),
                Placeholder.component("role", rolePrefix(clan.roleOf(m))),
                Placeholder.unparsed("rating", String.valueOf(m.rating())),
                Placeholder.unparsed("kills", String.valueOf(m.kills())),
                Placeholder.unparsed("deaths", String.valueOf(m.deaths())),
                Placeholder.unparsed("wins", String.valueOf(m.winPercent())),
                Placeholder.unparsed("invited", String.valueOf(m.invited())),
                Placeholder.unparsed("kicked", String.valueOf(m.kicked())));
        List<Component> lines = new ArrayList<>();
        lines.add(ColorUtil.parse("<white>Дата вступления <joined></white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse(BOX_TOP));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Звание: <role></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Рейтинг: <gold><rating> ★</gold></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Убил игроков: <red><kills></red></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Смертей: <gray><deaths></gray></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Побед на ПВП: <green><wins>%</green></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Пригласил: <aqua><invited></aqua></white>", r));
        lines.add(ColorUtil.parse(BOX_LINE + "<white>Выгнал: <red><kicked></red></white>", r));
        lines.add(ColorUtil.parse(BOX_BOTTOM));
        lines.add(Component.empty());
        Player online = Bukkit.getPlayer(m.uuid());
        if (online != null) {
            lines.add(ColorUtil.parse("<white>Сейчас <green>в сети</green></white>"));
        } else {
            long seen = m.lastSeen();
            if (seen <= 0) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(m.uuid());
                seen = op.getLastSeen();
            }
            lines.add(seen > 0
                    ? ColorUtil.parse("<white>Последний вход: <#5AA9FF><ago></#5AA9FF></white>", Placeholder.unparsed("ago", ago(seen)))
                    : ColorUtil.parse("<white>Последний вход: <gray>неизвестно</gray></white>"));
        }
        if (clickHint) lines.add(ColorUtil.parse("<gray>Нажми для взаимодействия.</gray>"));
        return lines;
    }

    /** Тег клана для чата: [Название], наведение - карточка, клик - меню клана. */
    public static Component chatTag(Clan clan, ClanManager manager) {
        List<Component> hover = new ArrayList<>();
        hover.add(name(clan));
        hover.addAll(card(clan, manager, true));
        return ColorUtil.parse(manager.settings().chatTag(), Placeholder.component("clan", name(clan)))
                .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), hover)))
                .clickEvent(ClickEvent.runCommand("/clan view " + clan.id()));
    }

    /** /c top [страница] в чат: "1. клан - владелец [8030 КР]". */
    public static void sendTop(Player player, ClanManager manager, int page) {
        List<Clan> top = manager.top();
        int size = manager.settings().topPageSize();
        int pages = Math.max(1, (top.size() + size - 1) / size);
        page = Math.max(1, Math.min(page, pages));

        player.sendMessage(ColorUtil.parse(TOP_BORDER + "╭" + DASHES + "</#7B6FE0><white>[Топ кланов]</white>"
                + TOP_BORDER + DASHES + "╮</#7B6FE0>"));
        if (top.isEmpty()) {
            player.sendMessage(ColorUtil.parse(TOP_LINE + "<gray>Пока нет ни одного клана. Создай первый: <white>/c create \\<название></white></gray>"));
        }
        for (int i = (page - 1) * size; i < Math.min(top.size(), page * size); i++) {
            Clan clan = top.get(i);
            Component line = ColorUtil.parse(TOP_LINE + "<red><place>.</red> <clan> <gray>-</gray> <#C8C8C8><owner></#C8C8C8> "
                            + "<gray>[</gray><red><rating></red> <white>КР</white><gray>]</gray>",
                    Placeholder.unparsed("place", String.valueOf(i + 1)),
                    Placeholder.component("clan", name(clan)),
                    Placeholder.unparsed("owner", clan.ownerName()),
                    Placeholder.unparsed("rating", String.valueOf(clan.rating())));
            List<Component> hover = new ArrayList<>();
            hover.add(name(clan));
            hover.addAll(card(clan, manager, true));
            player.sendMessage(line
                    .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), hover)))
                    .clickEvent(ClickEvent.runCommand("/clan view " + clan.id())));
        }
        if (page < pages) {
            String cmd = "/c top " + (page + 1);
            player.sendMessage(ColorUtil.parse(TOP_LINE + "<#C8C8C8>Следующая страница: <white><cmd></white></#C8C8C8>",
                            Placeholder.unparsed("cmd", cmd))
                    .clickEvent(ClickEvent.runCommand(cmd))
                    .hoverEvent(HoverEvent.showText(ColorUtil.parse("<gray>Нажми, чтобы открыть</gray>"))));
        }
        player.sendMessage(ColorUtil.parse(TOP_BORDER + "╰" + DASHES + DASHES_MID + DASHES + "╯</#7B6FE0>"));
    }

    /** Перенос текста по словам. */
    public static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
