package ru.dscraft.destroychat.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.hook.LuckPermsHook;
import ru.dscraft.destroychat.util.ColorUtil;
import ru.dscraft.destroychat.util.NameStyler;
import ru.dscraft.destroychat.util.Perms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /chatprefix &lt;текст&gt; - отдельный префикс только для чата (Ultra+), можно с градиентом.
 * /chatprefix reset    - убрать.
 * <p>
 * DestroyLobby перенаправляет сюда свою команду {@code /prefix chat ...}, поэтому для игрока
 * всё выглядит как в меню привилегий: /prefix chat.
 */
public class ChatPrefixCommand implements CommandExecutor, TabCompleter {

    private final ChatConfig config;
    private final LuckPermsHook luckPermsHook;

    public ChatPrefixCommand(ChatConfig config, LuckPermsHook luckPermsHook) {
        this.config = config;
        this.luckPermsHook = luckPermsHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду можно выполнить только находясь в игре.");
            return true;
        }
        if (!player.hasPermission(Perms.PREFIX_CHAT)) {
            deny(player, "Отдельный префикс для чата доступен с привилегии Ultra.");
            return true;
        }
        if (!luckPermsHook.isEnabled()) {
            deny(player, "LuckPerms недоступен, префикс сейчас поставить нельзя.");
            return true;
        }
        if (args.length == 0) {
            usage(player);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && (first.equals("reset") || first.equals("clear") || first.equals("off"))) {
            boolean removed = luckPermsHook.clearMetaValue(player, Perms.META_CHAT_PREFIX);
            player.sendMessage(ColorUtil.parse(removed
                    ? "<green>Чат-префикс убран, в чате снова префикс привилегии.</green>"
                    : "<gray>У тебя и так не было отдельного чат-префикса.</gray>"));
            return true;
        }

        String raw = String.join(" ", args).trim();
        String error = validate(player, raw, config.chatPrefixMaxLength());
        if (error != null) {
            deny(player, error);
            return true;
        }
        if (!raw.endsWith(" ")) raw = raw + " ";

        if (!luckPermsHook.setMetaValue(player, Perms.META_CHAT_PREFIX, raw)) {
            deny(player, "Не удалось сохранить чат-префикс, попробуй ещё раз.");
            return true;
        }
        // хвост из цветов после текста - цвет ника: /prefix chat &6КОРОЛЬ &2&l
        NameStyler.Split split = NameStyler.split(raw);
        Component name = split.nickStyle() != null
                ? ColorUtil.rich(split.nickStyle() + player.getName())
                : Component.text(player.getName(), NamedTextColor.GRAY);
        player.sendMessage(Component.text("Готово, в чате ты теперь: ", NamedTextColor.GRAY)
                .append(ColorUtil.rich(split.prefix()))
                .append(name));
        return true;
    }

    /** @return текст ошибки или null, если префикс подходит */
    private String validate(Player player, String raw, int maxVisible) {
        if (raw.isBlank()) return "Пустой префикс.";

        Component component = ColorUtil.rich(raw);
        String visible = ColorUtil.plain(component).trim();
        if (visible.isEmpty()) return "В префиксе нет текста, только цвета.";
        if (visible.codePointCount(0, visible.length()) > maxVisible) {
            return "Слишком длинный префикс (максимум " + maxVisible + " символов без учёта цветов).";
        }
        if (!player.hasPermission(Perms.PREFIX_FORMAT) && ColorUtil.hasDecorations(component)) {
            return "Жирный, курсив и другие стили доступны с привилегии Ultra.";
        }
        return null;
    }

    private void usage(Player player) {
        player.sendMessage(ColorUtil.parse("<gray>Использование: <white>/prefix chat \\<текст></white> | <white>/prefix chat reset</white></gray>"));
        player.sendMessage(ColorUtil.parse("<gray>Цвета: <white>&c &a &#FF55FF</white>, градиент: "
                + "<white>/prefix chat \\<gradient:#FF5555:#FFFF55>Король\\</gradient></white></gray>"));
        player.sendMessage(ColorUtil.parse("<gray>Цвет ника - в конце, после пробела: "
                + "<white>/prefix chat &6&l&oКОРОЛЬ &2&l&o</white></gray>"));
    }

    private void deny(Player player, String text) {
        player.sendMessage(Component.text(text, NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1 && "reset".startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("reset");
        return out;
    }
}
