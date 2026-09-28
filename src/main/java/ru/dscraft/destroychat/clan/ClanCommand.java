package ru.dscraft.destroychat.clan;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.dscraft.destroychat.util.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /clan (/c) - меню кланов и все клановые команды. Без аргументов открывает список кланов.
 */
public class ClanCommand implements CommandExecutor, TabCompleter {

    public static final String ADMIN_PERMISSION = "destroychat.clan.admin";

    private static final List<String> SUBS = List.of(
            "create", "invite", "accept", "deny", "join", "leave", "kick", "admin", "rank", "owner",
            "desc", "type", "icon", "color", "disband", "top", "view", "menu", "help");

    private final ClanActions actions;
    private final ClanManager manager;

    public ClanCommand(ClanActions actions) {
        this.actions = actions;
        this.manager = actions.manager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return consoleCommand(sender, args);
        }
        if (!actions.config().clansEnabled()) {
            player.sendMessage(ColorUtil.parse("<red>Кланы сейчас выключены.</red>"));
            return true;
        }
        if (args.length == 0) {
            ClanMenus.openList(player, manager, 0);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String rest = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        Clan mine = manager.getClan(player);

        switch (sub) {
            case "create", "создать" -> {
                if (args.length < 2) {
                    actions.error(player, "Использование: /c create <название>");
                    return true;
                }
                actions.create(player, args[1]);
            }
            case "invite", "пригласить" -> {
                if (args.length < 2) {
                    actions.error(player, "Использование: /c invite <ник>");
                    return true;
                }
                actions.invite(player, args[1]);
            }
            case "accept", "принять" -> actions.accept(player);
            case "deny", "отказаться" -> actions.deny(player);
            case "join", "вступить" -> {
                if (args.length < 2) {
                    actions.error(player, "Использование: /c join <клан>");
                    return true;
                }
                actions.join(player, manager.getById(args[1]), false);
            }
            case "leave", "выйти" -> actions.leave(player);
            case "kick", "исключить" -> {
                UUID target = memberArg(player, mine, args, "/c kick <ник>");
                if (target != null) actions.kick(player, target);
            }
            case "admin", "админ" -> {
                UUID target = memberArg(player, mine, args, "/c admin <ник>");
                if (target != null) actions.toggleAdmin(player, target);
            }
            case "rank", "звание" -> {
                if (args.length < 3) {
                    actions.error(player, "Использование: /c rank <ник> <звание> | /c rank <ник> reset");
                    return true;
                }
                UUID target = memberArg(player, mine, args, null);
                if (target == null) return true;
                String rank = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                boolean reset = rank.equalsIgnoreCase("reset") || rank.equalsIgnoreCase("сброс");
                actions.setRank(player, target, reset ? null : rank);
            }
            case "owner", "владелец" -> {
                UUID target = memberArg(player, mine, args, "/c owner <ник>");
                if (target != null) actions.transfer(player, target);
            }
            case "desc", "description", "описание" -> {
                if (rest.isEmpty()) {
                    actions.error(player, "Использование: /c desc <текст> | /c desc reset");
                    return true;
                }
                actions.setDescription(player, rest.equalsIgnoreCase("reset") ? null : rest);
            }
            case "type", "тип" -> actions.toggleType(player);
            case "icon", "иконка" -> actions.setIcon(player);
            case "color", "цвет" -> {
                if (args.length < 2) {
                    actions.error(player, "Использование: /c color <название с цветами>, например /c color &cMy&6Clan");
                    return true;
                }
                actions.recolor(player, args[1]);
            }
            case "disband", "delete", "распустить" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                    actions.error(player, "Клан будет удалён навсегда! Для подтверждения: /c disband confirm");
                    return true;
                }
                actions.disband(player);
            }
            case "top", "топ" -> {
                int page = 1;
                if (args.length >= 2) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                ClanText.sendTop(player, manager, actions.config(), page);
            }
            case "view", "info", "инфо" -> {
                Clan clan = args.length >= 2 ? manager.getById(args[1]) : mine;
                if (clan == null) {
                    actions.error(player, args.length >= 2 ? "Такого клана нет." : "Ты не состоишь в клане.");
                    return true;
                }
                ClanMenus.openClan(player, manager, clan, 0);
            }
            case "menu", "меню" -> {
                if (mine != null) ClanMenus.openClan(player, manager, mine, 0);
                else ClanMenus.openList(player, manager, 0);
            }
            case "forcedelete", "setrating" -> adminCommand(player, args);
            default -> help(player);
        }
        return true;
    }

    private UUID memberArg(Player player, Clan clan, String[] args, String usage) {
        if (args.length < 2) {
            if (usage != null) actions.error(player, "Использование: " + usage);
            return null;
        }
        if (clan == null) {
            actions.error(player, "Ты не состоишь в клане.");
            return null;
        }
        UUID uuid = actions.findMember(clan, args[1]);
        if (uuid == null) actions.error(player, "Игрок " + args[1] + " не в твоём клане.");
        return uuid;
    }

    private void help(Player player) {
        String[] lines = {
                "<#8C7BFF>━━━━━━━━━━ <white>Кланы</white> ━━━━━━━━━━</#8C7BFF>",
                "<white>/c</white> <gray>- список кланов</gray>",
                "<white>/c menu</white> <gray>- меню своего клана</gray>",
                "<white>/c top [страница]</white> <gray>- топ кланов</gray>",
                "<white>/c create \\<название></white> <gray>- создать клан (можно с цветами &c, &#FF55FF)</gray>",
                "<white>/c invite \\<ник></white> <gray>- пригласить игрока</gray>",
                "<white>/c accept</white> <gray>/</gray> <white>/c deny</white> <gray>- принять / отклонить приглашение</gray>",
                "<white>/c join \\<клан></white> <gray>- вступить в открытый клан</gray>",
                "<white>/c leave</white> <gray>- выйти из клана</gray>",
                "<white>/c kick \\<ник></white> <gray>- исключить участника</gray>",
                "<white>/c rank \\<ник> \\<звание></white> <gray>- выдать звание</gray>",
                "<white>/c admin \\<ник></white> <gray>- дать/забрать права администратора</gray>",
                "<white>/c owner \\<ник></white> <gray>- передать владение</gray>",
                "<white>/c desc \\<текст></white> <gray>- описание клана</gray>",
                "<white>/c type</white> <gray>- открытый / по приглашению</gray>",
                "<white>/c icon</white> <gray>- иконка клана = предмет в руке</gray>",
                "<white>/c color \\<название></white> <gray>- перекрасить название</gray>",
                "<white>/c disband confirm</white> <gray>- распустить клан</gray>",
                "<gray>За убийство игрока клан получает <gold>" + actions.config().clanKillRating() + " КР</gold>.</gray>"
        };
        for (String line : lines) player.sendMessage(ColorUtil.parse(line));
    }

    // ---------------- админ ----------------

    private boolean consoleCommand(CommandSender sender, String[] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("forcedelete") || args[0].equalsIgnoreCase("setrating"))) {
            adminCommand(sender, args);
        } else {
            sender.sendMessage("Из консоли: /clan forcedelete <клан> | /clan setrating <клан> <рейтинг>");
        }
        return true;
    }

    private void adminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            if (sender instanceof Player p) help(p);
            return;
        }
        Clan clan = args.length >= 2 ? manager.getById(args[1]) : null;
        if (clan == null) {
            sender.sendMessage("§cТакого клана нет.");
            return;
        }
        if (args[0].equalsIgnoreCase("forcedelete")) {
            actions.broadcast(clan, "<red>Клан удалён администрацией.</red>");
            manager.disband(clan);
            sender.sendMessage("§aКлан " + clan.id() + " удалён.");
        } else {
            if (args.length < 3) {
                sender.sendMessage("§cИспользование: /clan setrating <клан> <рейтинг>");
                return;
            }
            try {
                clan.rating(Integer.parseInt(args[2]));
                manager.markDirty();
                sender.sendMessage("§aРейтинг клана " + clan.id() + ": " + clan.rating());
            } catch (NumberFormatException e) {
                sender.sendMessage("§cРейтинг должен быть числом.");
            }
        }
    }

    // ---------------- tab ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(SUBS);
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                subs.add("forcedelete");
                subs.add("setrating");
            }
            filter(out, subs, args[0]);
            return out;
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "invite" -> {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (manager.getClan(p) == null) names.add(p.getName());
                    }
                    filter(out, names, args[1]);
                }
                case "kick", "admin", "rank", "owner" -> {
                    Clan clan = sender instanceof Player p ? manager.getClan(p) : null;
                    if (clan != null) {
                        List<String> names = new ArrayList<>();
                        for (ClanMember m : clan.membersMap().values()) names.add(m.name());
                        filter(out, names, args[1]);
                    }
                }
                case "join", "view", "info", "forcedelete", "setrating" -> {
                    List<String> ids = new ArrayList<>();
                    for (Clan c : manager.all()) ids.add(c.id());
                    filter(out, ids, args[1]);
                }
                case "disband" -> filter(out, List.of("confirm"), args[1]);
                case "desc" -> filter(out, List.of("reset"), args[1]);
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("rank")) {
            filter(out, List.of("reset"), args[2]);
        }
        return out;
    }

    private static void filter(List<String> out, List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        }
    }
}
