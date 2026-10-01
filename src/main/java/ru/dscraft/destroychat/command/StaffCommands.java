package ru.dscraft.destroychat.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.dscraft.destroychat.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Команды персонала:
 * /admin                 - телепорт на /warp admin;
 * /espeed fly|walk 1-10  - скорость полёта/ходьбы (1 - обычная, 10 - максимальная).
 */
public class StaffCommands implements CommandExecutor, TabCompleter {

    public static final String ADMIN_WARP = "destroychat.admin-warp";
    public static final String ESPEED = "destroychat.espeed";

    private static final String ERROR = "<#C9C9FB>Нет такой команды :/</#C9C9FB>";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только в игре.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("admin")) {
            if (!player.hasPermission(ADMIN_WARP)) {
                player.sendMessage(ColorUtil.parse(ERROR));
                return true;
            }
            // от имени консоли: у персонала может не быть права на /warp
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "warp admin " + player.getName());
            return true;
        }
        if (!player.hasPermission(ESPEED)) {
            player.sendMessage(ColorUtil.parse(ERROR));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.parse("<gray>Использование: <white>/espeed fly|walk 1-10</white></gray>"));
            return true;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        boolean fly;
        if (List.of("fly", "f", "полёт", "полет").contains(mode)) fly = true;
        else if (List.of("walk", "w", "ходьба").contains(mode)) fly = false;
        else {
            player.sendMessage(ColorUtil.parse("<gray>Использование: <white>/espeed fly|walk 1-10</white></gray>"));
            return true;
        }
        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            level = -1;
        }
        if (level < 1 || level > 10) {
            player.sendMessage(ColorUtil.parse("<red>Скорость - число от 1 до 10.</red>"));
            return true;
        }
        float speed = speed(level, fly);
        if (fly) player.setFlySpeed(speed);
        else player.setWalkSpeed(speed);
        player.sendMessage(ColorUtil.parse("<gray>Скорость " + (fly ? "полёта" : "ходьбы") + ": <white>" + level + "</white></gray>"));
        return true;
    }

    /** 1 - обычная скорость (полёт 0.1, ходьба 0.2), 10 - максимальная (1.0), между - поровну. */
    private static float speed(int level, boolean fly) {
        float base = fly ? 0.1f : 0.2f;
        return base + (level - 1) / 9f * (1f - base);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!command.getName().equalsIgnoreCase("espeed") || !sender.hasPermission(ESPEED)) return out;
        if (args.length == 1) {
            for (String s : List.of("fly", "walk")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2) {
            for (int i = 1; i <= 10; i++) out.add(String.valueOf(i));
        }
        return out;
    }
}
