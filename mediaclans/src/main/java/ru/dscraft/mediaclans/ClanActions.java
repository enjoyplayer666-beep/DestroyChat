package ru.dscraft.mediaclans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Действия с кланами. Их вызывают и команды /clan, и кнопки меню, поэтому все проверки прав здесь.
 * Все методы - из основного потока (кроме clanChat). Тексты - как на сервере-образце.
 */
public class ClanActions {

    /** Жирные/курсивные названия и префиксы - как у /prefix (Ultra+). */
    public static final String FORMAT_PERMISSION = "destroylobby.prefix.format";

    private static final String A = ClanText.A;
    private static final String P = ClanText.P;
    private static final String DG = ClanText.DG;

    private final ClanManager manager;
    private final Settings settings;

    public ClanActions(ClanManager manager) {
        this.manager = manager;
        this.settings = manager.settings();
    }

    public ClanManager manager() {
        return manager;
    }

    public Settings settings() {
        return settings;
    }

    // ---------------- сообщения ----------------

    public void msg(Player player, String miniMessage, TagResolver... resolvers) {
        player.sendMessage(ColorUtil.parse(settings.prefix() + "<white>" + miniMessage + "</white>", resolvers));
    }

    public void error(Player player, String text) {
        player.sendMessage(ColorUtil.parse(settings.prefix()).append(Component.text(text, NamedTextColor.RED)));
    }

    /** Сообщение всем участникам клана в сети. */
    public void broadcast(Clan clan, String miniMessage, TagResolver... resolvers) {
        Component text = ColorUtil.parse(settings.prefix() + "<white>" + miniMessage + "</white>", resolvers);
        for (Player p : clan.onlineMembers()) p.sendMessage(text);
    }

    static TagResolver ph(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    /** "Игрок [Лидер] ник" для сообщений о настройке клана. */
    private TagResolver actor(Clan clan, Player player) {
        return Placeholder.component("actor", ColorUtil.parse("<" + DG + ">[</" + DG + "><role><" + DG + ">]</" + DG + "> ",
                Placeholder.component("role", ClanText.rolePrefix(clan.roleOf(clan.member(player.getUniqueId()))))));
    }

    private void noPerm(Player player, Perm perm) {
        error(player, "У твоей роли нет права: " + perm.title() + ".");
    }

    // ---------------- проверки ----------------

    /** Клан игрока, если у него есть право; иначе пишет ошибку и возвращает null. */
    public Clan require(Player player, Perm perm) {
        Clan clan = manager.getClan(player);
        if (clan == null) {
            error(player, "Ты не состоишь в клане. Создай свой: /c create <название>");
            return null;
        }
        if (!clan.has(player.getUniqueId(), perm)) {
            noPerm(player, perm);
            return null;
        }
        return clan;
    }

    public Clan requireOwner(Player player) {
        Clan clan = manager.getClan(player);
        if (clan == null) {
            error(player, "Ты не состоишь в клане.");
            return null;
        }
        if (!clan.owner().equals(player.getUniqueId())) {
            error(player, "Это может только владелец клана.");
            return null;
        }
        return clan;
    }

    /** Может ли игрок менять эту роль: владелец - любую (у лидера - кроме прав и ID), остальные - только младше своей. */
    public boolean canEditRole(Clan clan, UUID player, ClanRole role) {
        if (role == null) return false;
        if (!clan.has(player, Perm.EDIT_ROLES)) return false;
        if (player.equals(clan.owner())) return true;
        if (role.leader()) return false;
        ClanRole mine = clan.roleOf(clan.member(player));
        return mine != null && mine.above(role);
    }

    // ---------------- создание / роспуск / название ----------------

    public void create(Player player, String rawName) {
        String perm = settings.createPermission();
        if (perm != null && !perm.isBlank() && !player.hasPermission(perm)) {
            error(player, "Создавать кланы можно с привилегии VIP. Купить: ds-craft.ru");
            return;
        }
        if (manager.getClan(player) != null) {
            error(player, "Ты уже состоишь в клане. Сначала выйди из него: /c leave");
            return;
        }
        String err = manager.validateName(rawName, player.hasPermission(FORMAT_PERMISSION));
        if (err != null) {
            error(player, err);
            return;
        }
        if (manager.getById(rawName) != null) {
            error(player, "Клан с таким названием уже есть.");
            return;
        }
        Clan clan = manager.create(player, rawName);
        msg(player, "Ты создал клан с названием <clan>", Placeholder.component("clan", ClanText.name(clan)));
    }

    public void rename(Player player, String rawName) {
        Clan clan = require(player, Perm.RENAME);
        if (clan == null) return;
        String err = manager.validateName(rawName, player.hasPermission(FORMAT_PERMISSION));
        if (err != null) {
            error(player, err);
            return;
        }
        Clan other = manager.getById(rawName);
        if (other != null && other != clan) {
            error(player, "Клан с таким названием уже есть.");
            return;
        }
        manager.rename(clan, rawName);
        broadcast(clan, "Игрок <actor><" + A + "><name></" + A + "> поменял название: <clan>",
                actor(clan, player), ph("name", player.getName()), Placeholder.component("clan", ClanText.name(clan)));
    }

    public void disband(Player player) {
        Clan clan = requireOwner(player);
        if (clan == null) return;
        for (Player p : clan.onlineMembers()) msg(p, "Клан в котором вы находились был удалён!");
        manager.disband(clan);
    }

    // ---------------- вступление / выход ----------------

    /** Кнопка "Пригласить игрока!": в чат ссылка, по клику подставляется /clan invite Ник. */
    public void invitePrompt(Player player) {
        player.closeInventory();
        Component link = ColorUtil.parse("<" + A + ">сюда</" + A + ">")
                .clickEvent(ClickEvent.suggestCommand("/clan invite Ник"))
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<white>Пригласить игрока...</white>")));
        player.sendMessage(ColorUtil.parse(settings.prefix() + "<white>Нажми <link> чтобы пригласить игрока.</white>",
                Placeholder.component("link", link)));
    }

    public void invite(Player player, String targetName) {
        Clan clan = require(player, Perm.INVITE);
        if (clan == null) return;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !player.canSee(target)) {
            error(player, "Игрок " + targetName + " не в сети.");
            return;
        }
        if (target.equals(player)) {
            error(player, "Нельзя пригласить самого себя.");
            return;
        }
        if (manager.getClan(target) != null) {
            error(player, target.getName() + " уже состоит в клане.");
            return;
        }
        if (clan.size() >= clan.slots()) {
            error(player, "В клане нет свободных слотов.");
            return;
        }
        manager.invite(target.getUniqueId(), clan, player);
        msg(player, "Приглашение <" + A + "><target></" + A + "> было отправлено.", ph("target", target.getName()));

        msg(target, "Игрок <" + A + "><inviter></" + A + "> приглашает в клан <clan>",
                ph("inviter", player.getName()), Placeholder.component("clan", ClanText.chatTag(clan, manager)));
        Component accept = ColorUtil.parse("<" + DG + ">[</" + DG + "><#2BFF5C>✓ Принять</#2BFF5C><" + DG + ">]</" + DG + ">")
                .clickEvent(ClickEvent.runCommand("/clan accept"))
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<white>Вступить в клан</white>")));
        Component deny = ColorUtil.parse("<" + DG + ">[</" + DG + "><#FF2B2B>✗ Отклонить</#FF2B2B><" + DG + ">]</" + DG + ">")
                .clickEvent(ClickEvent.runCommand("/clan deny"))
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<white>Отклонить приглашение</white>")));
        target.sendMessage(Component.text(" ").append(accept).append(Component.text(" ")).append(deny));
    }

    public void accept(Player player) {
        ClanManager.Invite invite = manager.getInvite(player.getUniqueId());
        if (invite == null) {
            error(player, "У тебя нет приглашений в клан.");
            return;
        }
        Clan clan = manager.getById(invite.clanId());
        if (clan == null) {
            error(player, "Этого клана больше нет.");
            return;
        }
        if (addTo(player, clan)) {
            msg(player, "Вы приняли приглашение и вступили в клан <clan>!", Placeholder.component("clan", ClanText.name(clan)));
            ClanMember inviter = clan.member(invite.inviter());
            if (inviter != null) {
                inviter.invited(inviter.invited() + 1);
                manager.markDirty();
            }
        }
    }

    public void deny(Player player) {
        ClanManager.Invite invite = manager.getInvite(player.getUniqueId());
        if (invite == null) {
            error(player, "У тебя нет приглашений в клан.");
            return;
        }
        manager.removeInvite(player.getUniqueId());
        msg(player, "Вы отклонили приглашение в клан.");
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) msg(inviter, "Игрок <" + A + "><name></" + A + "> отклонил приглашение.", ph("name", player.getName()));
    }

    /** Вступить из меню: открытый - сразу, по приглашению - только с приглашением, по паролю - пароль в чат (в меню). */
    public void join(Player player, Clan clan) {
        if (clan == null) {
            error(player, "Такого клана нет.");
            return;
        }
        ClanManager.Invite invite = manager.getInvite(player.getUniqueId());
        if (invite != null && invite.clanId().equals(clan.id())) {
            accept(player);
            return;
        }
        switch (clan.joinType()) {
            case OPEN -> {
                if (addTo(player, clan)) msg(player, "Вы вступили в клан <clan>!", Placeholder.component("clan", ClanText.name(clan)));
            }
            case PASSWORD -> error(player, "Для вступления нужен пароль клана: /c join " + clan.id() + " <пароль>");
            default -> error(player, "В этот клан можно вступить только по приглашению.");
        }
    }

    /** Вступить по паролю. */
    public void joinWithPassword(Player player, Clan clan, String password) {
        if (clan == null) {
            error(player, "Такого клана нет.");
            return;
        }
        if (clan.joinType() == Clan.JoinType.OPEN) {
            join(player, clan);
            return;
        }
        if (clan.joinType() != Clan.JoinType.PASSWORD || clan.password() == null) {
            error(player, "В этот клан можно вступить только по приглашению.");
            return;
        }
        if (!clan.password().equals(password.trim())) {
            error(player, "Неверный пароль клана.");
            return;
        }
        if (addTo(player, clan)) msg(player, "Вы вступили в клан <clan>!", Placeholder.component("clan", ClanText.name(clan)));
    }

    private boolean addTo(Player player, Clan clan) {
        if (manager.getClan(player) != null) {
            error(player, "Ты уже состоишь в клане.");
            return false;
        }
        if (clan.size() >= clan.slots()) {
            error(player, "В клане нет свободных слотов.");
            return false;
        }
        manager.addMember(clan, player.getUniqueId(), player.getName());
        for (Player p : clan.onlineMembers()) {
            if (!p.equals(player)) msg(p, "Игрок <" + A + "><name></" + A + "> вступил в клан!", ph("name", player.getName()));
        }
        return true;
    }

    public void leave(Player player) {
        Clan clan = manager.getClan(player);
        if (clan == null) {
            error(player, "Ты не состоишь в клане.");
            return;
        }
        if (clan.owner().equals(player.getUniqueId())) {
            error(player, "Владелец не может выйти из клана. Передай владение или удали клан.");
            return;
        }
        manager.removeMember(clan, player.getUniqueId());
        clan.log(HistoryEntry.Type.LEAVE, player.getName(), null, settings.historySize());
        msg(player, "Ты вышел из клана <clan>.", Placeholder.component("clan", ClanText.name(clan)));
        broadcast(clan, "Игрок <" + A + "><name></" + A + "> вышел из клана.", ph("name", player.getName()));
    }

    // ---------------- участники ----------------

    public void kick(Player player, UUID targetId) {
        Clan clan = require(player, Perm.KICK);
        if (clan == null) return;
        ClanMember target = clan.member(targetId);
        if (target == null) {
            error(player, "Этот игрок не в твоём клане.");
            return;
        }
        if (!clan.canManage(player.getUniqueId(), target)) {
            error(player, "Можно исключать только тех, у кого роль ниже твоей.");
            return;
        }
        manager.removeMember(clan, targetId);
        ClanMember me = clan.member(player.getUniqueId());
        if (me != null) me.kicked(me.kicked() + 1);
        clan.log(HistoryEntry.Type.KICK, player.getName(), target.name(), settings.historySize());
        broadcast(clan, "Игрок <" + A + "><a></" + A + "> выгнал из клана <" + A + "><t></" + A + ">.",
                ph("a", player.getName()), ph("t", target.name()));
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) msg(online, "Тебя выгнали из клана <clan>.", Placeholder.component("clan", ClanText.name(clan)));
    }

    public void setRole(Player player, UUID targetId, String roleId) {
        Clan clan = require(player, Perm.SET_ROLE);
        if (clan == null) return;
        ClanMember target = clan.member(targetId);
        ClanRole role = clan.role(roleId);
        if (target == null || role == null) {
            error(player, target == null ? "Этот игрок не в твоём клане." : "Такой роли нет.");
            return;
        }
        if (role.leader()) {
            error(player, "Роль лидера есть только у владельца. Передай клан кнопкой «Сделать игрока создателем».");
            return;
        }
        if (!clan.canManage(player.getUniqueId(), target)) {
            error(player, "Можно менять роль только тем, у кого роль ниже твоей.");
            return;
        }
        ClanRole mine = clan.roleOf(clan.member(player.getUniqueId()));
        if (!player.getUniqueId().equals(clan.owner()) && !mine.above(role)) {
            error(player, "Можно выдавать только роли ниже своей.");
            return;
        }
        target.roleId(role.id());
        clan.log(HistoryEntry.Type.ROLE, player.getName(), target.name() + "|" + ColorUtil.plain(ColorUtil.rich(role.name())),
                settings.historySize());
        manager.markDirty();
        broadcast(clan, "Игрок <" + A + "><a></" + A + "> изменил роль игроку <" + A + "><t></" + A + "> на <" + A + "><r></" + A + ">.",
                ph("a", player.getName()), ph("t", target.name()), ph("r", ColorUtil.plain(ColorUtil.rich(role.name()))));
    }

    public void transfer(Player player, UUID targetId) {
        Clan clan = requireOwner(player);
        if (clan == null) return;
        ClanMember target = clan.member(targetId);
        if (target == null || targetId.equals(player.getUniqueId())) {
            error(player, "Этот игрок не в твоём клане.");
            return;
        }
        manager.transfer(clan, targetId);
        broadcast(clan, "Игрок <" + A + "><a></" + A + "> делает <" + A + "><t></" + A + "> владельцем клана.",
                ph("a", player.getName()), ph("t", target.name()));
    }

    /** Телепорт к участнику клана из меню. */
    public void teleport(Player player, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            error(player, "Игрок не в сети.");
            return;
        }
        if (target.equals(player)) return;
        player.closeInventory();
        player.teleport(target);
        msg(player, "Ты телепортировался к игроку <" + A + "><name></" + A + ">.", ph("name", target.getName()));
    }

    /** Сообщения о входе/выходе участников: включить/выключить для себя. */
    public boolean toggleNotify(Player player) {
        Clan clan = manager.getClan(player);
        ClanMember me = clan == null ? null : clan.member(player.getUniqueId());
        if (me == null) return true;
        me.notifyJoins(!me.notifyJoins());
        manager.markDirty();
        return me.notifyJoins();
    }

    /** Купить слот участника (коины - пока только надпись, слот открывается сразу). */
    public void buySlot(Player player) {
        Clan clan = require(player, Perm.BUY_SLOTS);
        if (clan == null) return;
        if (clan.slots() >= settings.maxSlots()) {
            error(player, "Все слоты уже куплены.");
            return;
        }
        clan.slots(clan.slots() + 1);
        manager.markDirty();
        msg(player, "Слот куплен! Теперь в клане <" + A + "><n></" + A + "> мест.", ph("n", String.valueOf(clan.slots())));
    }

    /** UUID участника клана по нику. */
    public UUID findMember(Clan clan, String name) {
        if (clan == null) return null;
        for (ClanMember m : clan.membersMap().values()) {
            if (m.name().equalsIgnoreCase(name)) return m.uuid();
        }
        OfflinePlayer online = Bukkit.getPlayerExact(name);
        return online != null && clan.member(online.getUniqueId()) != null ? online.getUniqueId() : null;
    }

    // ---------------- настройки ----------------

    /** Описание целиком одной строкой (команда /c desc). */
    public void setDescription(Player player, String text) {
        setDescriptionLine(player, 0, text == null ? "" : text);
    }

    /** Строка описания (0..6), можно с цветами: &a, &#RRGGBB, градиенты. Пусто / "-" - убрать строку. */
    public void setDescriptionLine(Player player, int index, String text) {
        Clan clan = require(player, Perm.DESCRIPTION);
        if (clan == null || index < 0 || index >= ClanText.DESC_LINES) return;
        text = text == null ? "" : text.trim().replace("\n", " ");
        if (text.equals("-") || text.equalsIgnoreCase("reset")) text = "";
        if (ColorUtil.plain(ColorUtil.rich(text)).length() > settings.descriptionMax()) {
            error(player, "Слишком длинная строка (максимум " + settings.descriptionMax() + " символов).");
            return;
        }
        String[] lines = ClanText.descLines(clan);
        lines[index] = text;
        int last = -1;
        for (int i = 0; i < lines.length; i++) if (!lines[i].isBlank()) last = i;
        clan.description(last < 0 ? null : String.join("\n", java.util.Arrays.copyOf(lines, last + 1)));
        manager.markDirty();
        msg(player, "Строка описания сохранена!");
    }

    /** Вход: по приглашению -> по паролю -> открытый -> по приглашению. */
    public void cycleJoinType(Player player) {
        Clan clan = require(player, Perm.JOIN_TYPE);
        if (clan == null) return;
        Clan.JoinType next = switch (clan.joinType()) {
            case INVITE -> Clan.JoinType.PASSWORD;
            case PASSWORD -> Clan.JoinType.OPEN;
            default -> Clan.JoinType.INVITE;
        };
        if (next == Clan.JoinType.PASSWORD && clan.password() == null) {
            clan.password(String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000)));
        }
        clan.joinType(next);
        manager.markDirty();
        broadcast(clan, "Игрок <actor><" + A + "><name></" + A + "> поменял статус: " + ClanText.joinType(clan),
                actor(clan, player), ph("name", player.getName()));
    }

    public void setPassword(Player player, String password) {
        Clan clan = require(player, Perm.PASSWORD);
        if (clan == null) return;
        password = password.trim();
        if (password.length() < 3 || password.length() > 16 || password.contains(" ")) {
            error(player, "Пароль - от 3 до 16 символов без пробелов.");
            return;
        }
        clan.password(password);
        manager.markDirty();
        msg(player, "Новый пароль клана: <" + P + "><pw></" + P + ">", ph("pw", password));
    }

    public void togglePvp(Player player) {
        Clan clan = require(player, Perm.PVP);
        if (clan == null) return;
        clan.pvp(!clan.pvp());
        manager.markDirty();
        broadcast(clan, "Игрок <" + A + "><name></" + A + "> " + (clan.pvp() ? "включил" : "выключил") + " PvP.",
                ph("name", player.getName()));
    }

    public void setIcon(Player player, ItemStack item) {
        Clan clan = require(player, Perm.ICON);
        if (clan == null) return;
        if (item == null || item.getType().isAir()) return;
        clan.icon(item);
        manager.markDirty();
        msg(player, "Иконка клана изменена.");
    }

    /** Обращение к клану: всем участникам в сети. */
    public void announce(Player player, String text) {
        Clan clan = require(player, Perm.ANNOUNCE);
        if (clan == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        if (text.length() > settings.announcementMax()) {
            error(player, "Слишком длинное объявление (максимум " + settings.announcementMax() + " символов).");
            return;
        }
        clan.announcement(text);
        manager.markDirty();
        Component head = ColorUtil.parse("<#479CFF>Обращение к клану!</#479CFF>");
        Component body = ColorUtil.parse("<" + DG + ">[</" + DG + "><role><" + DG + ">]</" + DG + "> <" + A + "><name></" + A + "><" + DG + ">:</" + DG + "> <white><text></white>",
                Placeholder.component("role", ClanText.rolePrefix(clan.roleOf(clan.member(player.getUniqueId())))),
                ph("name", player.getName()), ph("text", text));
        for (Player p : clan.onlineMembers()) {
            p.sendMessage(head);
            p.sendMessage(body);
        }
    }

    public void pin(Player player, String text) {
        Clan clan = require(player, Perm.ANNOUNCE);
        if (clan == null) return;
        text = text.trim();
        if (text.isEmpty()) return;
        if (text.length() > settings.announcementMax()) {
            error(player, "Слишком длинное сообщение (максимум " + settings.announcementMax() + " символов).");
            return;
        }
        if (clan.pins().size() >= settings.maxPins()) {
            error(player, "Закреплено максимум сообщений (" + settings.maxPins() + "). Открепи старые.");
            return;
        }
        clan.pins().add(new Pin(player.getName(), text, System.currentTimeMillis()));
        manager.markDirty();
        broadcast(clan, "Игрок <" + A + "><name></" + A + "> закрепил сообщение: <gray><text></gray>",
                ph("name", player.getName()), ph("text", text));
    }

    public void unpin(Player player, int index) {
        Clan clan = require(player, Perm.ANNOUNCE);
        if (clan == null || index < 0 || index >= clan.pins().size()) return;
        clan.pins().remove(index);
        manager.markDirty();
        msg(player, "Сообщение откреплено.");
    }

    /** Клановый чат (из асинхронного чата). */
    public void clanChat(Player player, Clan clan, String message) {
        ClanMember me = clan.member(player.getUniqueId());
        Component line = ColorUtil.parse(settings.chatFormat(),
                Placeholder.component("role", ClanText.rolePrefix(clan.roleOf(me))),
                ph("name", player.getName()),
                ph("message", message));
        for (Player p : clan.onlineMembers()) p.sendMessage(line);
        Bukkit.getConsoleSender().sendMessage(Component.text("[Клан " + clan.id() + "] ").append(line));
    }

    // ---------------- роли ----------------

    /** "Создать новую!": роль "Новая роль" с ID roleNNNNN, префиксом "Префикс" и правами #4, #16. */
    public ClanRole createRole(Player player) {
        Clan clan = require(player, Perm.EDIT_ROLES);
        if (clan == null) return null;
        if (clan.roles().size() >= settings.maxRoles()) {
            error(player, "У клана уже максимум ролей (" + settings.maxRoles() + ").");
            return null;
        }
        String id;
        do {
            id = "role" + ThreadLocalRandom.current().nextInt(10000, 100000);
        } while (clan.role(id) != null);
        ClanRole role = new ClanRole(id, "Новая роль", "&#8B26FFПрефикс", Material.RABBIT_HIDE);
        role.set(EnumSet.of(Perm.CHAT, Perm.HISTORY));
        clan.putRole(role);
        manager.markDirty();
        return role;
    }

    public void deleteRole(Player player, String roleId) {
        Clan clan = manager.getClan(player);
        ClanRole role = clan == null ? null : clan.role(roleId);
        if (clan == null || role == null || role.leader() || role.id().equals(clan.defaultRoleId())
                || !canEditRole(clan, player.getUniqueId(), role)) {
            error(player, "Эту роль удалить нельзя.");
            return;
        }
        TagResolver who = actor(clan, player);
        clan.removeRole(roleId);
        String fallback = clan.defaultRoleId();
        for (ClanMember m : clan.membersMap().values()) {
            if (roleId.equals(m.roleId())) m.roleId(fallback);
        }
        manager.markDirty();
        broadcast(clan, "Игрок <actor><" + A + "><name></" + A + "> удалил роль: <" + A + "><id></" + A + ">",
                who, ph("name", player.getName()), ph("id", roleId));
    }

    public void renameRole(Player player, String roleId, String name) {
        Clan clan = manager.getClan(player);
        ClanRole role = clan == null ? null : clan.role(roleId);
        if (clan == null || !canEditRole(clan, player.getUniqueId(), role)) {
            error(player, "Эту роль менять нельзя.");
            return;
        }
        name = name.trim();
        if (name.isEmpty() || ColorUtil.plain(ColorUtil.rich(name)).length() > 24) {
            error(player, "Название роли - до 24 символов.");
            return;
        }
        role.name(name);
        manager.markDirty();
        broadcast(clan, "Игрок <actor><" + P + "><name></" + P + "> поменял название роли: <" + P + "><id></" + P + ">",
                actor(clan, player), ph("name", player.getName()), ph("id", role.id()));
    }

    public void setRolePrefix(Player player, String roleId, String prefix) {
        Clan clan = manager.getClan(player);
        ClanRole role = clan == null ? null : clan.role(roleId);
        if (clan == null || !canEditRole(clan, player.getUniqueId(), role)) {
            error(player, "Эту роль менять нельзя.");
            return;
        }
        prefix = prefix.trim();
        String plain = ColorUtil.plain(ColorUtil.rich(prefix)).trim();
        if (plain.isEmpty() || plain.length() > settings.rolePrefixMax()) {
            error(player, "Префикс роли - от 1 до " + settings.rolePrefixMax() + " символов (без цветов).");
            return;
        }
        role.prefix(prefix);
        manager.markDirty();
        broadcast(clan, "Игрок <actor><" + P + "><name></" + P + "> поменял префикс роли: <" + P + "><id></" + P + ">",
                actor(clan, player), ph("name", player.getName()), ph("id", role.id()));
    }

    /** Новый ID роли (у лидера ID менять нельзя). ID определяет порядок ролей. */
    public boolean changeRoleId(Player player, String roleId, String newId) {
        Clan clan = manager.getClan(player);
        ClanRole role = clan == null ? null : clan.role(roleId);
        if (clan == null || role == null || role.leader() || !canEditRole(clan, player.getUniqueId(), role)) {
            error(player, "Айди этой роли нельзя изменить.");
            return false;
        }
        newId = newId.trim().toLowerCase(Locale.ROOT);
        if (!ClanManager.validRoleId(newId) || newId.startsWith("!")) {
            error(player, "ID роли - латинские буквы, цифры и _, до 16 символов.");
            return false;
        }
        if (clan.role(newId) != null) {
            error(player, "Роль с таким ID уже есть.");
            return false;
        }
        ClanRole mine = clan.roleOf(clan.member(player.getUniqueId()));
        if (!player.getUniqueId().equals(clan.owner()) && mine != null && newId.compareTo(mine.id()) <= 0) {
            error(player, "ID должен идти по алфавиту после ID твоей роли (" + mine.id() + "), чтобы роль была ниже.");
            return false;
        }
        clan.changeRoleId(roleId, newId);
        manager.markDirty();
        broadcast(clan, "Игрок <actor><" + P + "><name></" + P + "> поменял ID роли: <" + P + "><id></" + P + ">",
                actor(clan, player), ph("name", player.getName()), ph("id", newId));
        return true;
    }

    public void setRoleIcon(Player player, String roleId, ItemStack item) {
        Clan clan = manager.getClan(player);
        ClanRole role = clan == null ? null : clan.role(roleId);
        if (clan == null || !canEditRole(clan, player.getUniqueId(), role)) {
            error(player, "Эту роль менять нельзя.");
            return;
        }
        if (item == null || item.getType().isAir()) return;
        role.icon(item.getType());
        manager.markDirty();
    }

    /** Переключить право роли; #1 "Все возможности" включает/выключает сразу все. */
    public void toggleRolePerm(Player player, String roleId, Perm perm) {
        Clan clan = manager.getClan(player);
        ClanRole role = clan == null ? null : clan.role(roleId);
        if (clan == null || role == null || role.leader() || !canEditRole(clan, player.getUniqueId(), role)) {
            error(player, role != null && role.leader() ? "Нельзя поменять права лидера." : "Эту роль менять нельзя.");
            return;
        }
        boolean owner = player.getUniqueId().equals(clan.owner());
        if (perm == Perm.ALL) {
            if (!owner) {
                error(player, "Право «Все возможности клана» может выдать только владелец.");
                return;
            }
            role.set(role.hasOwn(Perm.ALL) ? EnumSet.noneOf(Perm.class) : EnumSet.allOf(Perm.class));
        } else {
            if (!owner && !role.hasOwn(perm) && !clan.has(player.getUniqueId(), perm)) {
                error(player, "Нельзя выдать право, которого нет у тебя самого.");
                return;
            }
            role.toggle(perm);
            if (!role.hasOwn(perm)) {
                java.util.Set<Perm> set = role.perms();
                set.remove(Perm.ALL);
                role.set(set);
            }
        }
        manager.markDirty();
    }

    /** Следующая роль (не лидер) по кругу становится начальной. */
    public void cycleDefaultRole(Player player) {
        Clan clan = require(player, Perm.EDIT_ROLES);
        if (clan == null) return;
        java.util.List<ClanRole> roles = clan.roles().stream().filter(r -> !r.leader()).toList();
        if (roles.isEmpty()) return;
        int i = 0;
        for (int k = 0; k < roles.size(); k++) {
            if (roles.get(k).id().equals(clan.defaultRoleId())) i = (k + 1) % roles.size();
        }
        clan.defaultRoleId(roles.get(i).id());
        manager.markDirty();
    }
}
