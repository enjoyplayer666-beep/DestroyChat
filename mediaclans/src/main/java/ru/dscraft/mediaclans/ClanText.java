package ru.dscraft.mediaclans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Тексты кланов: карточки клана и участника, тег в чате, топ.
 * Цвета сняты с сервера-образца по пикселям.
 */
public final class ClanText {

    /** Заголовки кнопок меню. */
    public static final String P = "#C38CE0";
    /** Ники и значения в сообщениях чата. */
    public static final String A = "#00FFE3";
    /** Ники в карточках. */
    public static final String CYAN = "#53E7F6";
    public static final String RED = "#FF3C3C";
    public static final String YELLOW = "#FFFF00";
    public static final String GOLD = "#FFCD00";
    public static final String GREEN = "#3DFF66";
    public static final String KILLS = "#FF3434";
    public static final String DG = "#666666";
    public static final String BOX = "#806DF8";

    private static final String BAR = "<" + BOX + ">|</" + BOX + "> ";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final String TOP_BORDER = "<#7B6FE0>";
    private static final String TOP_LINE = "<#7B6FE0>│</#7B6FE0> ";
    private static final String DASHES = "- - - - - - - - - - - ";
    private static final String DASHES_MID = "- - - - - - - ";
    /** Строк в описании клана. */
    public static final int DESC_LINES = 7;

    private ClanText() {
    }

    public static String date(long millis) {
        return DATE.format(Instant.ofEpochMilli(millis));
    }

    private static String boxTop(int dashes) {
        return "<" + BOX + ">╭" + "-".repeat(dashes) + "╮</" + BOX + ">";
    }

    private static String boxBottom(int dashes) {
        return "<" + BOX + ">╰" + "-".repeat(dashes) + "╯</" + BOX + ">";
    }

    /** Название клана с его цветами (по умолчанию белое). */
    public static Component name(Clan clan) {
        return Component.empty().color(NamedTextColor.WHITE).append(ColorUtil.rich(clan.name()));
    }

    public static Component rolePrefix(ClanRole role) {
        return role == null ? Component.text("?", NamedTextColor.GRAY)
                : Component.empty().color(NamedTextColor.WHITE).append(ColorUtil.rich(role.prefix()));
    }

    /** Тип вступления для карточек. */
    public static String joinType(Clan clan) {
        return switch (clan.joinType()) {
            case PASSWORD -> "<#FF9F2B>По паролю</#FF9F2B>";
            case OPEN -> "<#26FF68>Свободный</#26FF68>";
            default -> "<" + RED + ">По приглашению</" + RED + ">";
        };
    }

    /** Строки описания (до 7), пустые - "". */
    public static String[] descLines(Clan clan) {
        String[] out = new String[DESC_LINES];
        java.util.Arrays.fill(out, "");
        String d = clan.description();
        if (d == null) return out;
        String[] parts = d.split("\n", -1);
        for (int i = 0; i < DESC_LINES && i < parts.length; i++) out[i] = parts[i];
        return out;
    }

    private static void description(List<Component> lines, Clan clan, String header) {
        lines.add(ColorUtil.parse(header));
        boolean any = false;
        for (String line : descLines(clan)) {
            if (line.isBlank()) continue;
            any = true;
            lines.add(Component.text("  ").append(Component.empty().color(NamedTextColor.WHITE).append(ColorUtil.rich(line))));
        }
        if (!any) lines.add(ColorUtil.parse("<white>  Нет описания...</white>"));
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
        lines.add(ColorUtil.parse("<white>Создал <" + CYAN + "><creator></" + CYAN + ">, дата <date></white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse("<white>Владелец клана: <" + CYAN + "><owner></" + CYAN + "></white>", r));
        lines.add(ColorUtil.parse("<white>Тип вступления: " + joinType(clan) + "</white>", r));
        lines.add(ColorUtil.parse("<white>Игроков в клане: <" + YELLOW + "><count> ☺</" + YELLOW + "></white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse(boxTop(20)));
        lines.add(ColorUtil.parse(BAR + "<white>Рейтинг: <" + GOLD + "><rating> ★</" + GOLD + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Побед на ПВП: <" + GREEN + "><wins>%</" + GREEN + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Убито игроков: <" + KILLS + "><kills></" + KILLS + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Статус: <status></white>", r));
        lines.add(ColorUtil.parse(boxBottom(20)));
        lines.add(Component.empty());
        description(lines, clan, "<white>Описание:</white>");
        if (clickHint) {
            lines.add(Component.empty());
            lines.add(ColorUtil.parse("<white>Нажми чтобы посмотреть клан.</white>"));
        }
        return lines;
    }

    /** "Информация о клане" в меню клана. */
    public static List<Component> info(Clan clan, ClanManager manager) {
        TagResolver r = clanTags(clan, manager);
        List<Component> lines = new ArrayList<>();
        lines.add(ColorUtil.parse("<white>Игрок <" + CYAN + "><owner></" + CYAN + "> владелец клана</white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse("<white>Участников в клане: <" + YELLOW + "><count></" + YELLOW + "> <" + DG + ">(Онлайн: <online>)</" + DG + "></white>", r));
        lines.add(ColorUtil.parse("<white>Тип вступления: " + joinType(clan) + "</white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse(boxTop(24)));
        lines.add(ColorUtil.parse(BAR + "<white>Название: <" + P + ">[</" + P + "><clan><" + P + ">]</" + P + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Рейтинг: <" + GOLD + "><rating></" + GOLD + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Побед на ПВП: <" + GREEN + "><wins>%</" + GREEN + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Убито игроков: <" + KILLS + "><kills></" + KILLS + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Статус клана: <status></white>", r));
        lines.add(ColorUtil.parse(boxBottom(24)));
        lines.add(Component.empty());
        description(lines, clan, "<#6F6F6F>Описание:</#6F6F6F>");
        lines.add(Component.empty());
        lines.add(ColorUtil.parse("<white>Дата создания <date></white>", r));
        return lines;
    }

    /** Название карточки участника: "[Участник] ник [Онлайн]". */
    public static Component memberTitle(Clan clan, ClanMember m) {
        boolean online = Bukkit.getPlayer(m.uuid()) != null;
        return ColorUtil.parse("<" + DG + ">[</" + DG + "><role><" + DG + ">]</" + DG + "> <" + P + "><name></" + P + "> "
                        + "<" + DG + ">[</" + DG + ">" + (online ? "<" + GREEN + ">Онлайн</" + GREEN + ">" : "<" + RED + ">Оффлайн</" + RED + ">")
                        + "<" + DG + ">]</" + DG + ">",
                Placeholder.component("role", rolePrefix(clan.roleOf(m))),
                Placeholder.unparsed("name", m.name()));
    }

    /** Карточка участника в меню клана. */
    public static List<Component> member(Clan clan, ClanMember m, boolean clickHint) {
        ClanRole role = clan.roleOf(m);
        TagResolver r = TagResolver.resolver(
                Placeholder.unparsed("joined", date(m.joinedAt())),
                Placeholder.component("rank", Component.empty().color(net.kyori.adventure.text.format.TextColor.fromHexString(YELLOW))
                        .append(Component.text(role == null ? "?" : ColorUtil.plain(ColorUtil.rich(role.name()))))),
                Placeholder.unparsed("rating", String.valueOf(m.rating())),
                Placeholder.unparsed("kills", String.valueOf(m.kills())),
                Placeholder.unparsed("deaths", String.valueOf(m.deaths())),
                Placeholder.unparsed("wins", String.valueOf(m.winPercent())),
                Placeholder.unparsed("invited", String.valueOf(m.invited())),
                Placeholder.unparsed("kicked", String.valueOf(m.kicked())));
        List<Component> lines = new ArrayList<>();
        lines.add(ColorUtil.parse("<white>Дата вступления <joined></white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse(boxTop(20)));
        lines.add(ColorUtil.parse(BAR + "<white>Звание: <rank></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Рейтинг: <" + GOLD + "><rating> ★</" + GOLD + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Убил игроков: <" + KILLS + "><kills></" + KILLS + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Смертей: <#BEBEBE><deaths></#BEBEBE></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Побед на ПВП: <" + GREEN + "><wins>%</" + GREEN + "></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Пригласил: <#00FFE3><invited></#00FFE3></white>", r));
        lines.add(ColorUtil.parse(BAR + "<white>Выгнал: <" + RED + "><kicked></" + RED + "></white>", r));
        lines.add(ColorUtil.parse(boxBottom(20)));
        if (clickHint) {
            lines.add(Component.empty());
            lines.add(ColorUtil.parse("<white>Нажми для взаимодействия.</white>"));
        }
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
