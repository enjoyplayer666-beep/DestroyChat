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
import ru.dscraft.destroychat.util.NameStyler;
import ru.dscraft.destroychat.util.Perms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /nickcolor &lt;цвет&gt; - цвет ника в чате и табе (как /color: &amp;c, #FF55FF, градиент, &amp;x&amp;..&amp;l).
 * /nickcolor italic - включить/выключить наклон ника.
 * /nickcolor reset  - вернуть обычный вид.
 */
public class NickColorCommand implements CommandExecutor, TabCompleter {

    private final LuckPermsHook luckPermsHook;
    private final NameStyler styler;

    public NickColorCommand(LuckPermsHook luckPermsHook, NameStyler styler) {
        this.luckPermsHook = luckPermsHook;
        this.styler = styler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду можно выполнить только находясь в игре.");
            return true;
        }
        if (!player.hasPermission(Perms.NICK_COLOR)) {
            player.sendMessage(Component.text("Цвет ника тебе недоступен.", NamedTextColor.RED));
            return true;
        }
        if (!luckPermsHook.isEnabled()) {
            player.sendMessage(Component.text("LuckPerms недоступен, попробуй позже.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(player);
            return true;
        }
        String input = String.join(" ", args).trim();
        String lower = input.toLowerCase(Locale.ROOT);

        if (lower.equals("reset") || lower.equals("сброс") || lower.equals("off")) {
            luckPermsHook.clearMetaValue(player, Perms.META_NAME_COLOR);
            luckPermsHook.clearMetaValue(player, Perms.META_NAME_ITALIC);
            player.sendMessage(ColorUtil.parse("<green>Ник снова обычный.</green>"));
            return true;
        }
        if (lower.equals("italic") || lower.equals("наклон") || lower.equals("курсив")) {
            boolean on = !"true".equalsIgnoreCase(luckPermsHook.getMetaValue(player, Perms.META_NAME_ITALIC));
            if (on) luckPermsHook.setMetaValue(player, Perms.META_NAME_ITALIC, "true");
            else luckPermsHook.clearMetaValue(player, Perms.META_NAME_ITALIC);
            preview(player, on ? "Наклон ника включён: " : "Наклон ника выключен: ");
            return true;
        }

        String tag = ChatColors.toTag(input);
        if (tag == null || !ChatColors.isValidTag(tag)) {
            player.sendMessage(Component.text("Не понял цвет «" + input + "».", NamedTextColor.RED));
            usage(player);
            return true;
        }
        if (!luckPermsHook.setMetaValue(player, Perms.META_NAME_COLOR, tag)) {
            player.sendMessage(Component.text("Не удалось сохранить цвет, попробуй ещё раз.", NamedTextColor.RED));
            return true;
        }
        preview(player, "Готово, так теперь выглядит твой ник: ");
        return true;
    }

    private void preview(Player player, String text) {
        // LuckPerms обновляет кэш мета-данных не мгновенно, поэтому показываем через тик
        player.getServer().getScheduler().runTaskLater(
                player.getServer().getPluginManager().getPlugin("DestroyChat"),
                () -> player.sendMessage(Component.text(text, NamedTextColor.GRAY).append(styler.chatName(player))), 2L);
    }

    private void usage(Player player) {
        player.sendMessage(ColorUtil.parse("<gray>Использование: <white>/nickcolor \\<цвет></white> | "
                + "<white>/nickcolor italic</white> <gray>(наклон)</gray> | <white>/nickcolor reset</white></gray>"));
        player.sendMessage(ColorUtil.parse("<gray>Цвета как в /color: <white>&c</white>, <white>#FF55FF</white>, "
                + "<white>#FF5555:#FFFF55</white> <gray>(градиент)</gray>, <white>&x&D&D&D&D&D&D&l</white>, <white>красный</white></gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1) return out;
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>(List.of("italic", "reset", "#FF55FF", "#FF5555:#FFFF55", "&x&D&D&D&D&D&D&l"));
        options.addAll(ChatColors.RU.keySet());
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(typed)) out.add(o);
        }
        return out;
    }
}
