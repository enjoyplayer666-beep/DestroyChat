package ru.dscraft.mediabans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Оформление как на скрине:
 * [!] Временный мут [!]
 *
 * ✘ Нарушитель · ник      (красный)
 * ☄ Замутил · ник         (зелёный)
 * ✭ Причина · текст       (фиолетовый)
 * ⌛ Срок · 1 секунду      (бирюзовый)
 */
public final class Style {

    public enum Kind { TARGET, ACTOR, REASON, TIME }

    public record Row(Kind kind, String label, String value) {
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static final String DEFAULT_TITLE = "<black>[</black><#C8E4E4>!</#C8E4E4><black>]</black> <white><bold><title></bold></white> "
            + "<black>[</black><#C8E4E4>!</#C8E4E4><black>]</black>";
    public static final String DEFAULT_TARGET = "<#FF5555>✘</#FF5555> <white><label></white> <dark_gray>·</dark_gray> <#FF5555><value></#FF5555>";
    public static final String DEFAULT_ACTOR = "<#2CF77E>☄</#2CF77E> <white><label></white> <dark_gray>·</dark_gray> <#2CF77E><value></#2CF77E>";
    public static final String DEFAULT_REASON = "<#BD66FA>✭</#BD66FA> <white><label></white> <dark_gray>·</dark_gray> <#BD66FA><value></#BD66FA>";
    public static final String DEFAULT_TIME = "<#C8E4E4>⌛</#C8E4E4> <white><label></white> <dark_gray>·</dark_gray> <#C8E4E4><value></#C8E4E4>";
    public static final String DEFAULT_ERROR = "<black>[</black><#C8E4E4>!</#C8E4E4><black>]</black> <#FF5555><text></#FF5555>";
    public static final String DEFAULT_INFO = "<black>[</black><#C8E4E4>!</#C8E4E4><black>]</black> <white><text></white>";
    public static final String DEFAULT_LIST_LINE = "<#FF5555>✘</#FF5555> <#FF5555><name></#FF5555> <dark_gray>·</dark_gray> "
            + "<#BD66FA><reason></#BD66FA> <dark_gray>·</dark_gray> <#C8E4E4><time></#C8E4E4>";

    private String title = DEFAULT_TITLE;
    private String target = DEFAULT_TARGET;
    private String actor = DEFAULT_ACTOR;
    private String reason = DEFAULT_REASON;
    private String time = DEFAULT_TIME;
    private String error = DEFAULT_ERROR;
    private String info = DEFAULT_INFO;
    private String listLine = DEFAULT_LIST_LINE;

    public void load(FileConfiguration cfg) {
        title = cfg.getString("style.title", DEFAULT_TITLE);
        target = cfg.getString("style.target", DEFAULT_TARGET);
        actor = cfg.getString("style.actor", DEFAULT_ACTOR);
        reason = cfg.getString("style.reason", DEFAULT_REASON);
        time = cfg.getString("style.time", DEFAULT_TIME);
        error = cfg.getString("style.error", DEFAULT_ERROR);
        info = cfg.getString("style.info", DEFAULT_INFO);
        listLine = cfg.getString("style.list-line", DEFAULT_LIST_LINE);
    }

    /** Заголовок, пустая строка, строки и пустая строка в конце - одним сообщением. */
    public Component block(String titleText, List<Row> rows) {
        List<Component> lines = new ArrayList<>();
        lines.add(title(titleText));
        lines.add(Component.empty());
        for (Row row : rows) lines.add(row(row));
        lines.add(Component.empty());
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** Экран бана/кика: то же, но без пустой строки в конце. */
    public Component screen(String titleText, List<Row> rows) {
        List<Component> lines = new ArrayList<>();
        lines.add(title(titleText));
        lines.add(Component.empty());
        for (Row row : rows) lines.add(row(row));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    public Component title(String text) {
        return MM.deserialize(title, Placeholder.unparsed("title", text));
    }

    public Component row(Row row) {
        String template = switch (row.kind()) {
            case TARGET -> target;
            case ACTOR -> actor;
            case REASON -> reason;
            case TIME -> time;
        };
        return MM.deserialize(template, Placeholder.unparsed("label", row.label()), Placeholder.unparsed("value", row.value()));
    }

    public Component error(String text) {
        return MM.deserialize(error, Placeholder.unparsed("text", text));
    }

    public Component info(String text) {
        return MM.deserialize(info, Placeholder.unparsed("text", text));
    }

    public Component listLine(String name, String reasonText, String timeText) {
        return MM.deserialize(listLine, Placeholder.unparsed("name", name), Placeholder.unparsed("reason", reasonText),
                Placeholder.unparsed("time", timeText));
    }
}
