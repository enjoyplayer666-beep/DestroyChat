package ru.dscraft.mediaclans;

import org.bukkit.Bukkit;
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

/** /clan (/c) - меню кланов и клановые команды. Без аргументов открывает список кланов. */
public final class ClanCommand implements CommandExecutor, TabCompleter {

    /** Старое право из DestroyChat тоже подходит. */
    private static final String[] ADMIN_PERMISSIONS = {"mediaclans.admin", "destroychat.clan.admin"};

    private static final List<String> SUBS = List.of(
            "create", "invite", "accept", "deny", "join", "leave", "kick", "role", "owner",
            "desc", "announce", "pin", "disband", "top", "view", "menu", "help");

    private final ClanActions actions;
    private final ClanManager manager;
    private final ClanMenus menus;

    public ClanCommand(ClanActions actions, ClanMenus menus) {
        this.actions = actions;
        this.manager = actions.manager();
        this.menus = menus;
    }

    private static boolean isAdmin(CommandSender sender) {
        for (String p : ADMIN_PERMISSIONS) if (sender.hasPermission(p)) return true;
        return false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("forcedelete") || args[0].equalsIgnoreCase("setrating"))) {
            adminCommand(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Из консоли: /clan forcedelete <клан> | /clan setrating <клан> <рейтинг>");
            return true;
        }
        if (args.length == 0) {
            menus.openList(player, 0);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String rest = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        Clan mine = manager.getClan(player);

        switch (sub) {
            case "create", "создать" -> {
                if (args.length < 2) menus.askCreate(player);
                else actions.create(player, args[1]);
            }
            case "invite", "пригласить" -> {
                if (args.length < 2) actions.error(player, "Использование: /c invite <ник>");
                else actions.invite(player, args[1]);
            }
            case "accept", "принять" -> actions.accept(player);
            case "deny", "отказаться" -> actions.deny(player);
            case "join", "вступить" -> {
                if (args.length < 2) {
                    actions.error(player, "Использование: /c join <клан> [пароль]");
                } else if (args.length >= 3) {
                    actions.joinWithPassword(player, manager.getById(args[1]), args[2]);
                } else {
                    actions.join(player, manager.getById(args[1]));
                }
            }
            case "leave", "выйти" -> actions.leave(player);
            case "kick", "исключить" -> {
                UUID target = memberArg(player, mine, args, "/c kick <ник>");
                if (target != null) actions.kick(player, target);
            }
            case "role", "роль" -> {
                if (args.length < 3) {
                    actions.error(player, "Использование: /c role <ник> <ID роли>");
                    return true;
                }
                UUID target = memberArg(player, mine, args, null);
                if (target != null) actions.setRole(player, target, args[2].toLowerCase(Locale.ROOT));
            }
            case "owner", "владелец" -> {
                UUID target = memberArg(player, mine, args, "/c owner <ник>");
                if (target != null) actions.transfer(player, target);
            }
            case "desc", "description", "описание" -> {
                if (rest.isEmpty()) actions.error(player, "Использование: /c desc <текст> | /c desc reset");
                else actions.setDescription(player, rest);
            }
            case "announce", "объявление" -> {
                if (rest.isEmpty()) actions.error(player, "Использование: /c announce <текст>");
                else actions.announce(player, rest);
            }
            case "pin", "закрепить" -> {
                if (rest.isEmpty()) actions.error(player, "Использование: /c pin <текст>");
                else actions.pin(player, rest);
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
                ClanText.sendTop(player, manager, page);
            }
            case "view", "info", "инфо" -> {
                Clan clan = args.length >= 2 ? manager.getById(args[1]) : mine;
                if (clan == null) {
                    actions.error(player, args.length >= 2 ? "Такого клана нет." : "Ты не состоишь в клане.");
                    return true;
                }
                menus.openClan(player, clan, 0);
            }
            case "menu", "меню" -> {
                if (mine != null) menus.openClan(player, mine, 0);
                else menus.openList(player, 0);
            }
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
                "<#FF9F43>━━━━━━━━━━ <white>Кланы</white> ━━━━━━━━━━</#FF9F43>",
                "<white>/c</white> <gray>- все кланы сервера</gray>",
                "<white>/c menu</white> <gray>- меню своего клана</gray>",
                "<white>/c top [страница]</white> <gray>- топ кланов</gray>",
                "<white>/c create \\<название></white> <gray>- создать клан (можно с цветами &c, &#FF55FF)</gray>",
                "<white>/c invite \\<ник></white> <gray>- пригласить игрока</gray>",
                "<white>/c accept</white> <gray>/</gray> <white>/c deny</white> <gray>- принять / отклонить приглашение</gray>",
                "<white>/c join \\<клан> [пароль]</white> <gray>- вступить в клан</gray>",
                "<white>/c leave</white> <gray>- выйти из клана</gray>",
                "<white>/c kick \\<ник></white> <gray>- исключить участника</gray>",
                "<white>/c role \\<ник> \\<ID роли></white> <gray>- выдать роль</gray>",
                "<white>/c owner \\<ник></white> <gray>- передать владение</gray>",
                "<white>/c desc \\<текст></white> <gray>- описание клана</gray>",
                "<white>/c announce \\<текст></white> <gray>- объявление клану</gray>",
                "<white>/c pin \\<текст></white> <gray>- закрепить сообщение</gray>",
                "<white>/c disband confirm</white> <gray>- удалить клан</gray>",
                "<white>" + actions.settings().chatSymbol().replace("<", "\\<") + "сообщение</white> <gray>- клановый чат</gray>",
                "<gray>За убийство игрока клан получает <gold>" + actions.settings().killRating() + " КР</gold>.</gray>"
        };
        for (String line : lines) player.sendMessage(ColorUtil.parse(line));
    }

    // ---------------- админ ----------------

    private void adminCommand(CommandSender sender, String[] args) {
        if (!isAdmin(sender)) {
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
            return;
        }
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

    // ---------------- tab ----------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(SUBS);
            if (isAdmin(sender)) {
                subs.add("forcedelete");
                subs.add("setrating");
            }
            filter(out, subs, args[0]);
            return out;
        }
        Clan mine = sender instanceof Player p ? manager.getClan(p) : null;
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) if (manager.getClan(p) == null) names.add(p.getName());
                    filter(out, names, args[1]);
                }
                case "kick", "role", "owner" -> {
                    if (mine != null) {
                        List<String> names = new ArrayList<>();
                        for (ClanMember m : mine.membersMap().values()) names.add(m.name());
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
        } else if (args.length == 3 && args[0].equalsIgnoreCase("role") && mine != null) {
            List<String> ids = new ArrayList<>();
            for (ClanRole r : mine.roles()) if (!r.leader()) ids.add(r.id());
            filter(out, ids, args[2]);
        }
        return out;
    }

    private static void filter(List<String> out, List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
    }
}
