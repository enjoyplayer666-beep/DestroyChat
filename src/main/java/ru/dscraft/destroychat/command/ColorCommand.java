package ru.dscraft.destroychat.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.dscraft.destroychat.hook.LuckPermsHook;
import ru.dscraft.destroychat.util.ChatColors;
import ru.dscraft.destroychat.util.ColorUtil;
import ru.dscraft.destroychat.util.Perms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /color &lt;цвет&gt; - постоянный цвет сообщений в чате (Elite SP).
 * Цвет: &a / a, #FF55FF, градиент #FF5555:#FFFF55 (2-6 цветов), rainbow, названия (red, красный...),
 * а также &-формат: &x&D&D&D&D&D&D&l, §x§F§F§0§0§0§0 §x§0§0§F§F§0§0 (градиент), &#FF55FF&l, &a&l.
 * /color reset - вернуть обычный цвет.
 */
public class ColorCommand implements CommandExecutor, TabCompleter {

    private final LuckPermsHook luckPermsHook;

    public ColorCommand(LuckPermsHook luckPermsHook) {
        this.luckPermsHook = luckPermsHook;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду можно выполнить только находясь в игре.");
            return true;
        }
        if (!player.hasPermission(Perms.CHAT_COLOR)) {
            player.sendMessage(Component.text("Цвет сообщений доступен с привилегии Elite SP.", NamedTextColor.RED));
            return true;
        }
        if (!luckPermsHook.isEnabled()) {
            player.sendMessage(Component.text("LuckPerms недоступен, цвет сейчас поставить нельзя.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(player);
            return true;
        }

        String input = String.join(" ", args).trim();
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.equals("reset") || lower.equals("off") || lower.equals("сброс")) {
            boolean removed = luckPermsHook.clearMetaValue(player, Perms.META_CHAT_COLOR);
            player.sendMessage(ColorUtil.parse(removed
                    ? "<green>Цвет сообщений сброшен.</green>"
                    : "<gray>Цвет сообщений и так стандартный.</gray>"));
            return true;
        }

        String tag = ChatColors.toTag(input);
        if (tag == null || !ChatColors.isValidTag(tag)) {
            player.sendMessage(Component.text("Не понял цвет «" + input + "».", NamedTextColor.RED));
            usage(player);
            return true;
        }

        if (!luckPermsHook.setMetaValue(player, Perms.META_CHAT_COLOR, tag)) {
            player.sendMessage(Component.text("Не удалось сохранить цвет, попробуй ещё раз.", NamedTextColor.RED));
            return true;
        }
        player.sendMessage(Component.text("Готово! Так теперь выглядят твои сообщения: ", NamedTextColor.GRAY)
                .append(ColorUtil.safe(tag + "Привет, DestroyCraft!")));
        return true;
    }

    private void usage(Player player) {
        player.sendMessage(ColorUtil.parse("<gray>Использование: <white>/color \\<цвет></white> | <white>/color reset</white></gray>"));
        player.sendMessage(ColorUtil.parse("<gray>Примеры: <white>/color &d</white>, <white>/color #FF55FF</white>, "
                + "<white>/color #FF5555:#FFFF55</white> <gray>(градиент)</gray>, <white>/color красный</white>, <white>/color rainbow</white></gray>"));
        player.sendMessage(ColorUtil.parse("<gray>Формат &-кодов: <white>/color &x&D&D&D&D&D&D&l</white> <gray>(с жирным)</gray>, "
                + "<white>/color &x&F&F&0&0&0&0 &x&0&0&F&F&0&0</white> <gray>(градиент)</gray>, <white>/color &a&l</white></gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>(List.of("reset", "rainbow", "#FF55FF", "#FF5555:#FFFF55",
                "&x&D&D&D&D&D&D&l", "&x&F&F&0&0&0&0&l&x&0&0&F&F&0&0"));
        options.addAll(ChatColors.RU.keySet());
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(o);
        }
        return out;
    }
}
