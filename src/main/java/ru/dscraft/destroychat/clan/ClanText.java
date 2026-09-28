package ru.dscraft.destroychat.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.util.ColorUtil;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Тексты кланов: карточка клана (лор предмета и подсказка в чате), тег в чате, топ. */
public final class ClanText {

    public static final String PREFIX = "<#8C7BFF>[Кланы]</#8C7BFF> ";
    private static final String BOX = "<#6F63C9>";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());

    private ClanText() {
    }

    public static String date(long millis) {
        return DATE.format(Instant.ofEpochMilli(millis));
    }

    /** Название клана с его цветами (по умолчанию белое). */
    public static Component name(Clan clan) {
        return Component.empty().color(NamedTextColor.WHITE)
                .append(ColorUtil.rich(clan.name()));
    }

    /** Карточка клана, как в меню: создатель, владелец, рейтинг, статус, описание. */
    public static List<Component> card(Clan clan, ClanManager manager, boolean clickHint) {
        TagResolver r = TagResolver.resolver(
                Placeholder.unparsed("creator", clan.creatorName()),
                Placeholder.unparsed("date", date(clan.createdAt())),
                Placeholder.unparsed("owner", clan.ownerName()),
                Placeholder.unparsed("count", String.valueOf(clan.size())),
                Placeholder.unparsed("rating", String.valueOf(clan.rating())),
                Placeholder.unparsed("wins", String.valueOf(clan.winPercent())),
                Placeholder.unparsed("kills", String.valueOf(clan.kills())),
                Placeholder.component("status", ColorUtil.parse(manager.status(clan))));

        List<Component> lines = new ArrayList<>();
        lines.add(ColorUtil.parse("<white>Создал <aqua><creator></aqua>, дата <date></white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse("<white>Владелец клана: <aqua><owner></aqua></white>", r));
        lines.add(ColorUtil.parse(clan.open()
                ? "<white>Тип вступления: <green>Открытый</green></white>"
                : "<white>Тип вступления: <red>По приглашению</red></white>", r));
        lines.add(ColorUtil.parse("<white>Игроков в клане: <yellow><count> ☺</yellow></white>", r));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse(BOX + "┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐"));
        lines.add(ColorUtil.parse(BOX + "│</#6F63C9> <white>Рейтинг: <gold><rating> ★</gold></white>", r));
        lines.add(ColorUtil.parse(BOX + "│</#6F63C9> <white>Побед на ПВП: <green><wins>%</green></white>", r));
        lines.add(ColorUtil.parse(BOX + "│</#6F63C9> <white>Убито игроков: <red><kills></red></white>", r));
        lines.add(ColorUtil.parse(BOX + "│</#6F63C9> <white>Статус: <status></white>", r));
        lines.add(ColorUtil.parse(BOX + "└ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘"));
        lines.add(Component.empty());
        lines.add(ColorUtil.parse("<white>Описание:</white>"));
        String desc = clan.description();
        if (desc == null || desc.isBlank()) {
            lines.add(ColorUtil.parse("<gray>  Нет описания...</gray>"));
        } else {
            for (String line : wrap(desc, 36)) {
                lines.add(ColorUtil.parse("<gray>  <line></gray>", Placeholder.unparsed("line", line)));
            }
        }
        if (clickHint) {
            lines.add(Component.empty());
            lines.add(ColorUtil.parse("<gray>Нажми чтобы посмотреть клан.</gray>"));
        }
        return lines;
    }

    /** Тег клана для чата: [Название], наведение - карточка, клик - меню клана. */
    public static Component chatTag(Clan clan, ClanManager manager, ChatConfig config) {
        List<Component> hover = new ArrayList<>();
        hover.add(name(clan));
        hover.addAll(card(clan, manager, true));
        return ColorUtil.parse(config.clanChatTag(), Placeholder.component("clan", name(clan)))
                .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), hover)))
                .clickEvent(ClickEvent.runCommand("/clan view " + clan.id()));
    }

    /** /c top [страница] в чат, как на скрине: "1. клан - владелец [8030 КР]". */
    public static void sendTop(Player player, ClanManager manager, ChatConfig config, int page) {
        List<Clan> top = manager.top();
        int size = config.clanTopPageSize();
        int pages = Math.max(1, (top.size() + size - 1) / size);
        page = Math.max(1, Math.min(page, pages));

        player.sendMessage(ColorUtil.parse("<#8C7BFF>              [<white>Топ кланов</white>]</#8C7BFF>"));
        if (top.isEmpty()) {
            player.sendMessage(ColorUtil.parse("<gray>Пока нет ни одного клана. Создай первый: <white>/c create \\<название></white></gray>"));
            return;
        }
        for (int i = (page - 1) * size; i < Math.min(top.size(), page * size); i++) {
            Clan clan = top.get(i);
            Component line = ColorUtil.parse("<red><place>.</red> <clan> <dark_gray>-</dark_gray> <white><owner></white> "
                            + "<gray>[</gray><red><rating> КР</red><gray>]</gray>",
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
            player.sendMessage(ColorUtil.parse("<white>Следующая страница: <aqua><cmd></aqua></white>",
                            Placeholder.unparsed("cmd", cmd))
                    .clickEvent(ClickEvent.runCommand(cmd)));
        }
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
