package ru.dscraft.mediabans;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static ru.dscraft.mediabans.Style.Kind.ACTOR;
import static ru.dscraft.mediabans.Style.Kind.REASON;
import static ru.dscraft.mediabans.Style.Kind.TARGET;
import static ru.dscraft.mediabans.Style.Kind.TIME;

/** /ban /tempban /mute /tempmute /kick /unban /unmute /checkban /checkmute /banlist /mediabans reload. */
public final class PunishCommand implements CommandExecutor, TabCompleter {

    private static final int LIST_PAGE = 10;

    private final MediaBansPlugin plugin;

    public PunishCommand(MediaBansPlugin plugin) {
        this.plugin = plugin;
    }

    private Style style() {
        return plugin.style();
    }

    private Access access() {
        return plugin.access();
    }

    private PunishStore store() {
        return plugin.store();
    }

    /** Какое действие у команды (по главному имени, алиасы ведут туда же). */
    public static Access.Action action(String command) {
        return switch (command.toLowerCase(Locale.ROOT)) {
            case "ban" -> Access.Action.BAN;
            case "tempban" -> Access.Action.TEMPBAN;
            case "mute" -> Access.Action.MUTE;
            case "tempmute" -> Access.Action.TEMPMUTE;
            case "kick" -> Access.Action.KICK;
            case "unban" -> Access.Action.UNBAN;
            case "unmute" -> Access.Action.UNMUTE;
            case "checkban", "checkmute" -> Access.Action.CHECK;
            case "banlist" -> Access.Action.BANLIST;
            default -> null;
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("mediabans")) {
            if (!sender.isOp() && sender instanceof Player) {
                sender.sendMessage(plugin.noCommand());
                return true;
            }
            plugin.reload();
            sender.sendMessage(style().info("MediaBans: конфиг перезагружен."));
            return true;
        }
        Access.Action action = action(name);
        if (action == null) return true;
        if (!access().can(sender, action)) {
            sender.sendMessage(plugin.noCommand());
            return true;
        }
        switch (name) {
            case "ban" -> punish(sender, args, Punishment.Type.BAN, false, "/ban <ник> [причина]");
            case "tempban" -> punish(sender, args, Punishment.Type.BAN, true, "/tempban <ник> <время> [причина]");
            case "mute" -> punish(sender, args, Punishment.Type.MUTE, false, "/mute <ник> [причина]");
            case "tempmute" -> punish(sender, args, Punishment.Type.MUTE, true, "/tempmute <ник> <время> [причина]");
            case "kick" -> kick(sender, args);
            case "unban" -> pardon(sender, args, Punishment.Type.BAN);
            case "unmute" -> pardon(sender, args, Punishment.Type.MUTE);
            case "checkban" -> check(sender, args, Punishment.Type.BAN);
            case "checkmute" -> check(sender, args, Punishment.Type.MUTE);
            case "banlist" -> banlist(sender, args);
            default -> {
            }
        }
        return true;
    }

    // ---------------- бан / мут ----------------

    private record Target(UUID uuid, String name) {
    }

    /** Онлайн или уже заходивший игрок; null - такого не было. */
    private static Target resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return new Target(online.getUniqueId(), online.getName());
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) return new Target(cached.getUniqueId(), cached.getName() != null ? cached.getName() : name);
        return null;
    }

    private static String actorName(CommandSender sender) {
        return sender instanceof Player p ? p.getName() : "Консоль";
    }

    private static String reason(String[] args, int from) {
        if (args.length <= from) return "Не указана";
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private void punish(CommandSender sender, String[] args, Punishment.Type type, boolean temp, String usage) {
        if (args.length < (temp ? 2 : 1)) {
            sender.sendMessage(style().error("Использование: " + usage));
            return;
        }
        Target target = resolve(args[0]);
        if (target == null) {
            sender.sendMessage(style().error("Игрок " + args[0] + " ещё не заходил на сервер."));
            return;
        }
        if (sender instanceof Player p && p.getUniqueId().equals(target.uuid())) {
            sender.sendMessage(style().error("Нельзя наказать самого себя."));
            return;
        }
        boolean staff = access().isStaff(sender);
        long duration = 0;
        if (temp) {
            duration = Durations.parse(args[1]);
            if (duration <= 0) {
                sender.sendMessage(style().error("Неверное время: " + args[1] + ". Примеры: 30s, 10m, 1h, 1d."));
                return;
            }
            if (!staff) {
                Access.Limits limits = access().limits(sender);
                long max = limits == null ? 0 : (type == Punishment.Type.BAN ? limits.ban() : limits.mute());
                if (duration > max) {
                    sender.sendMessage(style().error((type == Punishment.Type.BAN ? "Банить" : "Мутить")
                            + " можно максимум на " + Durations.format(max) + "."));
                    return;
                }
            }
        }
        Punishment existing = store().active(type, target.uuid());
        if (existing != null && existing.byStaff() && !staff) {
            sender.sendMessage(style().error("Игрока " + target.name() + " уже наказала команда проекта."));
            return;
        }
        String reason = reason(args, temp ? 2 : 1);
        String actor = actorName(sender);
        long length = duration;
        access().isStaffTarget(target.uuid()).thenAccept(targetStaff -> plugin.sync(() -> {
            if (targetStaff && sender instanceof Player) {
                sender.sendMessage(style().error(type == Punishment.Type.BAN
                        ? "Нельзя забанить команду проекта." : "Нельзя замутить команду проекта."));
                return;
            }
            long now = System.currentTimeMillis();
            Punishment p = new Punishment(type, target.uuid(), target.name(), actor, staff, reason, now, length > 0 ? now + length : 0);
            store().put(p);
            plugin.broadcast(announce(p));
            Player online = Bukkit.getPlayer(target.uuid());
            if (online != null && type == Punishment.Type.BAN) online.kick(banScreen(p));
        }));
    }

    /** Сообщение в чат о наказании. */
    private Component announce(Punishment p) {
        boolean ban = p.type() == Punishment.Type.BAN;
        String title = ban ? (p.permanent() ? "Бан" : "Временный бан") : (p.permanent() ? "Мут" : "Временный мут");
        return style().block(title, List.of(
                new Style.Row(TARGET, "Нарушитель", p.name()),
                new Style.Row(ACTOR, ban ? "Забанил" : "Замутил", p.actor()),
                new Style.Row(REASON, "Причина", p.reason()),
                new Style.Row(TIME, "Срок", p.permanent() ? "навсегда" : Durations.format(p.end() - p.start()))));
    }

    /** Экран, который видит забаненный при кике и при входе. */
    public Component banScreen(Punishment p) {
        long now = System.currentTimeMillis();
        return style().screen(p.permanent() ? "Ты забанен" : "Ты временно забанен", List.of(
                new Style.Row(ACTOR, "Забанил", p.actor()),
                new Style.Row(REASON, "Причина", p.reason()),
                new Style.Row(TIME, "Осталось", p.permanent() ? "навсегда" : Durations.format(p.left(now)))));
    }

    /** Что пишется замученному, когда он пытается писать. */
    public Component mutedMessage(Punishment p) {
        long now = System.currentTimeMillis();
        return style().block("Ты в муте", List.of(
                new Style.Row(ACTOR, "Замутил", p.actor()),
                new Style.Row(REASON, "Причина", p.reason()),
                new Style.Row(TIME, "Осталось", p.permanent() ? "навсегда" : Durations.format(p.left(now)))));
    }

    // ---------------- кик ----------------

    private void kick(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(style().error("Использование: /kick <ник> [причина]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(style().error("Игрок " + args[0] + " не в сети."));
            return;
        }
        if (target.equals(sender)) {
            sender.sendMessage(style().error("Нельзя кикнуть самого себя."));
            return;
        }
        if (sender instanceof Player && access().isStaff(target)) {
            sender.sendMessage(style().error("Нельзя кикнуть команду проекта."));
            return;
        }
        String reason = reason(args, 1);
        String actor = actorName(sender);
        target.kick(style().screen("Ты кикнут", List.of(
                new Style.Row(ACTOR, "Кикнул", actor),
                new Style.Row(REASON, "Причина", reason))));
        plugin.broadcast(style().block("Кик", List.of(
                new Style.Row(TARGET, "Нарушитель", target.getName()),
                new Style.Row(ACTOR, "Кикнул", actor),
                new Style.Row(REASON, "Причина", reason))));
    }

    // ---------------- снятие ----------------

    private Punishment find(Punishment.Type type, String name) {
        Target target = resolve(name);
        Punishment p = target == null ? null : store().active(type, target.uuid());
        return p != null ? p : store().activeByName(type, name);
    }

    private void pardon(CommandSender sender, String[] args, Punishment.Type type) {
        boolean ban = type == Punishment.Type.BAN;
        if (args.length < 1) {
            sender.sendMessage(style().error("Использование: " + (ban ? "/unban" : "/unmute") + " <ник>"));
            return;
        }
        Punishment p = find(type, args[0]);
        if (p == null) {
            sender.sendMessage(style().error("Игрок " + args[0] + (ban ? " не забанен." : " не в муте.")));
            return;
        }
        if (p.byStaff() && !access().isStaff(sender)) {
            sender.sendMessage(style().error((ban ? "Этот бан" : "Этот мут") + " выдала команда проекта - снять его может только она."));
            return;
        }
        store().remove(p);
        plugin.broadcast(style().block(ban ? "Разбан" : "Размут", List.of(
                new Style.Row(TARGET, "Игрок", p.name()),
                new Style.Row(ACTOR, ban ? "Разбанил" : "Размутил", actorName(sender)))));
    }

    // ---------------- просмотр ----------------

    private void check(CommandSender sender, String[] args, Punishment.Type type) {
        boolean ban = type == Punishment.Type.BAN;
        if (args.length < 1) {
            sender.sendMessage(style().error("Использование: " + (ban ? "/checkban" : "/checkmute") + " <ник>"));
            return;
        }
        Punishment p = find(type, args[0]);
        if (p == null) {
            sender.sendMessage(style().error("Игрок " + args[0] + (ban ? " не забанен." : " не в муте.")));
            return;
        }
        long now = System.currentTimeMillis();
        sender.sendMessage(style().block(ban ? "Информация о бане" : "Информация о муте", List.of(
                new Style.Row(TARGET, "Нарушитель", p.name()),
                new Style.Row(ACTOR, ban ? "Забанил" : "Замутил", p.actor()),
                new Style.Row(REASON, "Причина", p.reason()),
                new Style.Row(TIME, "Осталось", p.permanent() ? "навсегда" : Durations.format(p.left(now))))));
    }

    private void banlist(CommandSender sender, String[] args) {
        List<Punishment> bans = store().all(Punishment.Type.BAN);
        if (bans.isEmpty()) {
            sender.sendMessage(style().info("Забаненных игроков нет."));
            return;
        }
        int pages = (bans.size() + LIST_PAGE - 1) / LIST_PAGE;
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        page = Math.max(1, Math.min(page, pages));
        List<Component> lines = new ArrayList<>();
        lines.add(style().title("Банлист " + page + "/" + pages));
        lines.add(Component.empty());
        long now = System.currentTimeMillis();
        for (int i = (page - 1) * LIST_PAGE; i < Math.min(bans.size(), page * LIST_PAGE); i++) {
            Punishment p = bans.get(i);
            lines.add(style().listLine(p.name(), p.reason(), p.permanent() ? "навсегда" : Durations.format(p.left(now))));
        }
        if (page < pages) {
            lines.add(Component.empty());
            lines.add(style().info("Следующая страница: /banlist " + (page + 1)));
        }
        lines.add(Component.empty());
        sender.sendMessage(Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines));
    }

    // ---------------- подсказки ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        Access.Action action = action(name);
        if (action == null || !access().can(sender, action)) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if (action == Access.Action.UNBAN || name.equals("checkban")) {
                for (Punishment p : store().all(Punishment.Type.BAN)) out.add(p.name());
            } else if (action == Access.Action.UNMUTE || name.equals("checkmute")) {
                for (Punishment p : store().all(Punishment.Type.MUTE)) out.add(p.name());
            } else if (action != Access.Action.BANLIST) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        } else if (args.length == 2 && (action == Access.Action.TEMPBAN || action == Access.Action.TEMPMUTE)) {
            out.addAll(List.of("10m", "30m", "1h", "1d", "7d"));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
