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

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Действия с кланами. Их вызывают и команды /clan, и кнопки меню, поэтому все проверки прав здесь.
 * Все методы - из основного потока (кроме clanChat).
 */
public class ClanActions {

    /** Жирные/курсивные названия и префиксы - как у /prefix (Ultra+). */
    public static final String FORMAT_PERMISSION = "destroylobby.prefix.format";

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
        player.sendMessage(ColorUtil.parse(settings.prefix() + miniMessage, resolvers));
    }

    public void error(Player player, String text) {
        player.sendMessage(ColorUtil.parse(settings.prefix()).append(Component.text(text, NamedTextColor.RED)));
    }

    /** Сообщение всем участникам клана в сети. */
    public void broadcast(Clan clan, String miniMessage, TagResolver... resolvers) {
        Component text = ColorUtil.parse(settings.prefix() + miniMessage, resolvers);
        for (Player p : clan.onlineMembers()) p.sendMessage(text);
    }

    static TagResolver ph(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
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
            error(player, "У твоей роли нет права: " + perm.title() + ".");
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

    /** Может ли игрок менять эту роль: владелец - любую кроме лидера, остальные - только младше своей. */
    public boolean canEditRole(Clan clan, UUID player, ClanRole role) {
        if (role == null || role.leader()) return false;
        if (!clan.has(player, Perm.EDIT_ROLES)) return false;
        if (player.equals(clan.owner())) return true;
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
        msg(player, "<white>Клан</white> <clan> <white>создан! Приглашай игроков: <#FF9F43>/c invite \\<ник></#FF9F43></white>",
                Placeholder.component("clan", ClanText.name(clan)));
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
        broadcast(clan, "<white>Новое название клана:</white> <clan>", Placeholder.component("clan", ClanText.name(clan)));
    }

    public void disband(Player player) {
        Clan clan = requireOwner(player);
        if (clan == null) return;
        for (Player p : clan.onlineMembers()) {
            if (!p.equals(player)) msg(p, "<white>Клан в котором вы находились был удалён!</white>");
        }
        manager.disband(clan);
        msg(player, "<white>Вы успешно распустили клан.</white>");
    }

    // ---------------- вступление / выход ----------------

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
        if (clan.size() >= settings.maxMembers()) {
            error(player, "В клане уже максимум игроков (" + settings.maxMembers() + ").");
            return;
        }
        manager.invite(target.getUniqueId(), clan, player);
        msg(player, "<white>Приглашение отправлено игроку <aqua><target></aqua>.</white>", ph("target", target.getName()));

        Component accept = Component.text("[Принять]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/clan accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Вступить в клан", NamedTextColor.GRAY)));
        Component deny = Component.text("[Отказаться]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/clan deny"))
                .hoverEvent(HoverEvent.showText(Component.text("Отклонить приглашение", NamedTextColor.GRAY)));
        msg(target, "<aqua><inviter></aqua> <white>приглашает тебя в клан</white> <clan><white>. "
                        + "Приглашение действует <yellow><sec></yellow> сек.</white>",
                ph("inviter", player.getName()),
                Placeholder.component("clan", ClanText.chatTag(clan, manager)),
                ph("sec", String.valueOf(settings.inviteSeconds())));
        target.sendMessage(accept.append(Component.text("  ")).append(deny));
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
        msg(player, "<gray>Приглашение отклонено.</gray>");
        Player inviter = Bukkit.getPlayer(invite.inviter());
        if (inviter != null) msg(inviter, "<aqua><name></aqua> <white>отклонил приглашение в клан.</white>", ph("name", player.getName()));
    }

    /** Вступить без пароля: только по приглашению. */
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
        if (clan.joinType() == Clan.JoinType.PASSWORD) {
            error(player, "Для вступления нужен пароль клана: /c join " + clan.id() + " <пароль>");
        } else {
            error(player, "В этот клан можно вступить только по приглашению.");
        }
    }

    /** Вступить по паролю. */
    public void joinWithPassword(Player player, Clan clan, String password) {
        if (clan == null) {
            error(player, "Такого клана нет.");
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
        addTo(player, clan);
    }

    private boolean addTo(Player player, Clan clan) {
        if (manager.getClan(player) != null) {
            error(player, "Ты уже состоишь в клане.");
            return false;
        }
        if (clan.size() >= settings.maxMembers()) {
            error(player, "В клане уже максимум игроков.");
            return false;
        }
        manager.addMember(clan, player.getUniqueId(), player.getName());
        broadcast(clan, "<aqua><name></aqua> <white>вступил в клан!</white>", ph("name", player.getName()));
        return true;
    }

    public void leave(Player player) {
        Clan clan = manager.getClan(player);
        if (clan == null) {
            error(player, "Ты не состоишь в клане.");
            return;
        }
        if (clan.owner().equals(player.getUniqueId())) {
            error(player, "Владелец не может выйти из клана. Передай владение (/c owner <ник>) или удали клан.");
            return;
        }
        manager.removeMember(clan, player.getUniqueId());
        clan.log(HistoryEntry.Type.LEAVE, player.getName(), null, settings.historySize());
        msg(player, "<white>Ты вышел из клана</white> <clan><white>.</white>", Placeholder.component("clan", ClanText.name(clan)));
        broadcast(clan, "<aqua><name></aqua> <white>вышел из клана.</white>", ph("name", player.getName()));
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
            error(player, "Можно исключать только тех, у кого роль младше твоей.");
            return;
        }
        manager.removeMember(clan, targetId);
        ClanMember me = clan.member(player.getUniqueId());
        if (me != null) me.kicked(me.kicked() + 1);
        clan.log(HistoryEntry.Type.KICK, player.getName(), target.name(), settings.historySize());
        broadcast(clan, "<aqua><name></aqua> <white>исключён из клана.</white>", ph("name", target.name()));
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            msg(online, "<white>Тебя исключили из клана</white> <clan><white>.</white>", Placeholder.component("clan", ClanText.name(clan)));
        }
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
            error(player, "Роль лидера есть только у владельца. Передать клан: /c owner <ник>");
            return;
        }
        if (!clan.canManage(player.getUniqueId(), target)) {
            error(player, "Можно менять роль только тем, у кого роль младше твоей.");
            return;
        }
        ClanRole mine = clan.roleOf(clan.member(player.getUniqueId()));
        if (!player.getUniqueId().equals(clan.owner()) && !mine.above(role)) {
            error(player, "Можно выдавать только роли младше своей.");
            return;
        }
        target.roleId(role.id());
        manager.markDirty();
        broadcast(clan, "<aqua><name></aqua> <white>получил роль</white> <role><white>.</white>",
                ph("name", target.name()), Placeholder.component("role", ClanText.rolePrefix(role)));
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
        broadcast(clan, "<gold>Новый владелец клана: <white><name></white>!</gold>", ph("name", target.name()));
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

    public void setDescription(Player player, String text) {
        Clan clan = require(player, Perm.DESCRIPTION);
        if (clan == null) return;
        if (text != null) {
            text = text.trim().replaceAll("\\s+", " ");
            if (text.isEmpty() || text.equalsIgnoreCase("reset")) text = null;
        }
        if (text != null && text.length() > settings.descriptionMax()) {
            error(player, "Слишком длинное описание (максимум " + settings.descriptionMax() + " символов).");
            return;
        }
        clan.description(text);
        manager.markDirty();
        msg(player, text == null ? "<gray>Описание клана убрано.</gray>" : "<white>Описание клана обновлено.</white>");
    }

    public void toggleJoinType(Player player) {
        Clan clan = require(player, Perm.JOIN_TYPE);
        if (clan == null) return;
        if (clan.joinType() == Clan.JoinType.INVITE) {
            if (clan.password() == null) clan.password(String.valueOf(ThreadLocalRandom.current().nextInt(1000, 10000)));
            clan.joinType(Clan.JoinType.PASSWORD);
        } else {
            clan.joinType(Clan.JoinType.INVITE);
        }
        manager.markDirty();
        broadcast(clan, clan.joinType() == Clan.JoinType.PASSWORD
                ? "<white>Тип вступления: <gold>по паролю</gold>.</white>"
                : "<white>Тип вступления: <red>по приглашению</red>.</white>");
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
        msg(player, "<white>Новый пароль клана: <#B884FF><pw></#B884FF></white>", ph("pw", password));
    }

    public void togglePvp(Player player) {
        Clan clan = require(player, Perm.PVP);
        if (clan == null) return;
        clan.pvp(!clan.pvp());
        manager.markDirty();
        broadcast(clan, clan.pvp()
                ? "<white>Огонь по своим: <green>включён</green>.</white>"
                : "<white>Огонь по своим: <red>выключен</red>.</white>");
    }

    public void setIcon(Player player, ItemStack item) {
        Clan clan = require(player, Perm.ICON);
        if (clan == null) return;
        if (item == null || item.getType().isAir()) {
            error(player, "Нажми по предмету в своём инвентаре, чтобы сделать его иконкой клана.");
            return;
        }
        clan.icon(item);
        manager.markDirty();
        msg(player, "<white>Иконка клана изменена.</white>");
    }

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
        Component block = ColorUtil.parse("<#FF9F43><b>Объявление клана</b></#FF9F43> <dark_gray>•</dark_gray> <gray>от <aqua><name></aqua></gray>\n<white><text></white>",
                ph("name", player.getName()), ph("text", text));
        for (Player p : clan.onlineMembers()) {
            p.sendMessage(Component.empty());
            p.sendMessage(block);
            p.sendMessage(Component.empty());
        }
    }

    public void pin(Player player, String text) {
        Clan clan = require(player, Perm.PIN);
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
        broadcast(clan, "<aqua><name></aqua> <white>закрепил сообщение: <gray><text></gray></white>",
                ph("name", player.getName()), ph("text", text));
    }

    public void unpin(Player player, int index) {
        Clan clan = require(player, Perm.PIN);
        if (clan == null || index < 0 || index >= clan.pins().size()) return;
        clan.pins().remove(index);
        manager.markDirty();
        msg(player, "<white>Сообщение откреплено.</white>");
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

    public ClanRole createRole(Player player, String id) {
        Clan clan = require(player, Perm.EDIT_ROLES);
        if (clan == null) return null;
        id = id.trim().toLowerCase(Locale.ROOT);
        if (!ClanManager.validRoleId(id) || id.startsWith("!")) {
            error(player, "ID роли - латинские буквы, цифры и _, до 16 символов. Например: moderator");
            return null;
        }
        if (clan.role(id) != null) {
            error(player, "Роль с таким ID уже есть.");
            return null;
        }
        if (clan.roles().size() >= settings.maxRoles()) {
            error(player, "У клана уже максимум ролей (" + settings.maxRoles() + ").");
            return null;
        }
        ClanRole mine = clan.roleOf(clan.member(player.getUniqueId()));
        if (!player.getUniqueId().equals(clan.owner()) && mine != null && id.compareTo(mine.id()) <= 0) {
            error(player, "ID новой роли должен идти по алфавиту после ID твоей роли (" + mine.id() + "), чтобы она была младше.");
            return null;
        }
        ClanRole role = new ClanRole(id, id, "&7" + id, Material.PAPER);
        clan.putRole(role);
        manager.markDirty();
        msg(player, "<white>Роль <yellow><id></yellow> создана. Настрой её права в меню.</white>", ph("id", id));
        return role;
    }

    public void deleteRole(Player player, String roleId) {
        Clan clan = manager.getClan(player);
        ClanRole role = clan == null ? null : clan.role(roleId);
        if (clan == null || !canEditRole(clan, player.getUniqueId(), role)) {
            error(player, "Эту роль удалить нельзя.");
            return;
        }
        if (clan.roles().size() <= 2) {
            error(player, "В клане должно остаться хотя бы две роли.");
            return;
        }
        clan.removeRole(roleId);
        String fallback = clan.defaultRoleId();
        for (ClanMember m : clan.membersMap().values()) {
            if (roleId.equals(m.roleId())) m.roleId(fallback);
        }
        manager.markDirty();
        msg(player, "<white>Роль <yellow><id></yellow> удалена, её участники получили начальную роль.</white>", ph("id", roleId));
    }

    public void renameRole(Player player, String roleId, String name) {
        Clan clan = manager.getClan(player);
        ClanRole role = clan == null ? null : clan.role(roleId);
        if (clan == null || !canEditRole(clan, player.getUniqueId(), role)) {
            error(player, "Эту роль менять нельзя.");
            return;
        }
        name = name.trim();
        if (name.isEmpty() || name.length() > 24) {
            error(player, "Название роли - до 24 символов.");
            return;
        }
        role.name(name);
        manager.markDirty();
        msg(player, "<white>Название роли: <yellow><name></yellow></white>", ph("name", name));
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
        if (!player.hasPermission(FORMAT_PERMISSION) && ColorUtil.hasDecorations(ColorUtil.rich(prefix))) {
            error(player, "Жирный, курсив и другие стили доступны с привилегии Ultra.");
            return;
        }
        role.prefix(prefix);
        manager.markDirty();
        msg(player, "<white>Префикс роли:</white> <prefix>", Placeholder.component("prefix", ClanText.rolePrefix(role)));
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

    public void toggleRolePerm(Player player, String roleId, Perm perm) {
        Clan clan = manager.getClan(player);
        ClanRole role = clan == null ? null : clan.role(roleId);
        if (clan == null || !canEditRole(clan, player.getUniqueId(), role)) {
            error(player, role != null && role.leader() ? "У роли лидера всегда все права." : "Эту роль менять нельзя.");
            return;
        }
        if (!player.getUniqueId().equals(clan.owner()) && !role.hasOwn(perm) && !clan.has(player.getUniqueId(), perm)) {
            error(player, "Нельзя выдать право, которого нет у тебя самого.");
            return;
        }
        if (perm == Perm.ALL && !player.getUniqueId().equals(clan.owner())) {
            error(player, "Право «Все возможности клана» может выдать только владелец.");
            return;
        }
        role.toggle(perm);
        manager.markDirty();
    }

    /** Следующая роль (не лидер) по кругу становится начальной. */
    public void cycleDefaultRole(Player player) {
        Clan clan = require(player, Perm.DEFAULT_ROLE);
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
