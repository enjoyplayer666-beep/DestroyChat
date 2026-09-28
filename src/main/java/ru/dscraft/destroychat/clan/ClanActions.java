package ru.dscraft.destroychat.clan;

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
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.util.ColorUtil;
import ru.dscraft.destroychat.util.Perms;

import java.util.UUID;

/**
 * Действия с кланами. Их вызывают и команды /clan, и кнопки в меню, поэтому все проверки прав здесь.
 * Все методы - из основного потока.
 */
public class ClanActions {

    private final ClanManager manager;
    private final ChatConfig config;

    public ClanActions(ClanManager manager, ChatConfig config) {
        this.manager = manager;
        this.config = config;
    }

    public ClanManager manager() {
        return manager;
    }

    public ChatConfig config() {
        return config;
    }

    // ---------------- сообщения ----------------

    public void msg(Player player, String miniMessage, TagResolver... resolvers) {
        player.sendMessage(ColorUtil.parse(ClanText.PREFIX + miniMessage, resolvers));
    }

    public void error(Player player, String text) {
        player.sendMessage(ColorUtil.parse(ClanText.PREFIX).append(Component.text(text, NamedTextColor.RED)));
    }

    /** Сообщение всем участникам клана в сети. */
    public void broadcast(Clan clan, String miniMessage, TagResolver... resolvers) {
        Component text = ColorUtil.parse(ClanText.PREFIX + miniMessage, resolvers);
        for (Player p : clan.onlineMembers()) p.sendMessage(text);
    }

    private static TagResolver ph(String key, String name) {
        return Placeholder.unparsed(key, name);
    }

    // ---------------- создание / роспуск ----------------

    public void create(Player player, String rawName) {
        if (manager.getClan(player) != null) {
            error(player, "Ты уже состоишь в клане. Сначала выйди из него: /c leave");
            return;
        }
        String err = manager.validateName(rawName, player.hasPermission(Perms.PREFIX_FORMAT));
        if (err != null) {
            error(player, err);
            return;
        }
        if (manager.getById(rawName) != null) {
            error(player, "Клан с таким названием уже есть.");
            return;
        }
        Clan clan = manager.create(player, rawName);
        msg(player, "<green>Клан <clan> <green>создан! Приглашай игроков: <white>/c invite \\<ник></white></green>",
                Placeholder.component("clan", ClanText.name(clan)));
    }

    /** Перекрасить название (текст без цветов должен остаться тем же). */
    public void recolor(Player player, String rawName) {
        Clan clan = requireRole(player, ClanRole.OWNER);
        if (clan == null) return;
        String err = manager.validateName(rawName, player.hasPermission(Perms.PREFIX_FORMAT));
        if (err != null) {
            error(player, err);
            return;
        }
        if (!ClanManager.toId(rawName).equals(clan.id())) {
            error(player, "Можно менять только цвета, само название должно остаться прежним.");
            return;
        }
        clan.name(rawName);
        manager.markDirty();
        msg(player, "<green>Готово, теперь клан выглядит так: <clan></green>",
                Placeholder.component("clan", ClanText.name(clan)));
    }

    public void disband(Player player) {
        Clan clan = requireRole(player, ClanRole.OWNER);
        if (clan == null) return;
        broadcast(clan, "<red>Клан <clan> <red>распущен владельцем.</red>",
                Placeholder.component("clan", ClanText.name(clan)));
        manager.disband(clan);
    }

    // ---------------- вступление / выход ----------------

    public void invite(Player player, String targetName) {
        Clan clan = requireManager(player);
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
        if (clan.size() >= config.clanMaxMembers()) {
            error(player, "В клане уже максимум игроков (" + config.clanMaxMembers() + ").");
            return;
        }
        manager.invite(target.getUniqueId(), clan, player.getName());
        msg(player, "<green>Приглашение отправлено игроку <white><target></white>.</green>",
                ph("target", target.getName()));

        Component accept = Component.text("[Принять]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/clan accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Вступить в клан", NamedTextColor.GRAY)));
        Component deny = Component.text("[Отказаться]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/clan deny"))
                .hoverEvent(HoverEvent.showText(Component.text("Отклонить приглашение", NamedTextColor.GRAY)));
        msg(target, "<white><inviter></white> <gray>приглашает тебя в клан</gray> <clan><gray>. "
                        + "Приглашение действует <white><sec></white> сек.</gray>",
                ph("inviter", player.getName()),
                Placeholder.component("clan", ClanText.chatTag(clan, manager, config)),
                Placeholder.unparsed("sec", String.valueOf(config.clanInviteSeconds())));
        target.sendMessage(accept.append(Component.text("  ")).append(deny));
    }

    public void accept(Player player) {
        ClanManager.Invite invite = manager.getInvite(player.getUniqueId());
        if (invite == null) {
            error(player, "У тебя нет приглашений в клан.");
            return;
        }
        Clan clan = manager.getById(invite.clanId());
        join(player, clan, true);
    }

    public void deny(Player player) {
        ClanManager.Invite invite = manager.getInvite(player.getUniqueId());
        if (invite == null) {
            error(player, "У тебя нет приглашений в клан.");
            return;
        }
        manager.removeInvite(player.getUniqueId());
        msg(player, "<gray>Приглашение отклонено.</gray>");
        Player inviter = Bukkit.getPlayerExact(invite.inviterName());
        if (inviter != null) {
            msg(inviter, "<white><name></white> <gray>отклонил приглашение в клан.</gray>", ph("name", player.getName()));
        }
    }

    /** Вступить в клан: открытый - сразу, закрытый - только по приглашению. */
    public void join(Player player, Clan clan, boolean viaInvite) {
        if (clan == null) {
            error(player, "Такого клана нет.");
            return;
        }
        if (manager.getClan(player) != null) {
            error(player, "Ты уже состоишь в клане.");
            return;
        }
        ClanManager.Invite invite = manager.getInvite(player.getUniqueId());
        boolean invited = invite != null && invite.clanId().equals(clan.id());
        if (!clan.open() && !invited) {
            error(player, "В этот клан можно вступить только по приглашению.");
            return;
        }
        if (clan.size() >= config.clanMaxMembers()) {
            error(player, "В клане уже максимум игроков.");
            return;
        }
        manager.addMember(clan, player.getUniqueId(), player.getName());
        broadcast(clan, "<white><name></white> <green>вступил в клан!</green>", ph("name", player.getName()));
    }

    public void leave(Player player) {
        Clan clan = manager.getClan(player);
        if (clan == null) {
            error(player, "Ты не состоишь в клане.");
            return;
        }
        if (clan.owner().equals(player.getUniqueId())) {
            error(player, "Владелец не может выйти из клана. Передай владение (/c owner <ник>) или распусти клан (/c disband).");
            return;
        }
        manager.removeMember(clan, player.getUniqueId());
        msg(player, "<gray>Ты вышел из клана</gray> <clan><gray>.</gray>", Placeholder.component("clan", ClanText.name(clan)));
        broadcast(clan, "<white><name></white> <red>вышел из клана.</red>", ph("name", player.getName()));
    }

    // ---------------- участники ----------------

    public void kick(Player player, UUID targetId) {
        Clan clan = manager.getClan(player);
        ClanMember target = checkTarget(player, clan, targetId);
        if (target == null) return;
        manager.removeMember(clan, targetId);
        broadcast(clan, "<white><name></white> <red>исключён из клана.</red>", ph("name", target.name()));
        Player online = Bukkit.getPlayer(targetId);
        if (online != null) {
            msg(online, "<red>Тебя исключили из клана</red> <clan><red>.</red>", Placeholder.component("clan", ClanText.name(clan)));
        }
    }

    public void toggleAdmin(Player player, UUID targetId) {
        Clan clan = requireRole(player, ClanRole.OWNER);
        if (clan == null) return;
        ClanMember target = clan.member(targetId);
        if (target == null) {
            error(player, "Этот игрок не в твоём клане.");
            return;
        }
        if (target.role() == ClanRole.OWNER) {
            error(player, "Ты владелец клана, у тебя и так все права.");
            return;
        }
        boolean give = target.role() == ClanRole.MEMBER;
        target.role(give ? ClanRole.ADMIN : ClanRole.MEMBER);
        manager.markDirty();
        broadcast(clan, give
                        ? "<white><name></white> <green>получил права администратора клана.</green>"
                        : "<white><name></white> <yellow>больше не администратор клана.</yellow>",
                ph("name", target.name()));
    }

    /** Звание участника; rank == null - убрать. */
    public void setRank(Player player, UUID targetId, String rank) {
        Clan clan = manager.getClan(player);
        ClanMember me = clan == null ? null : clan.member(player.getUniqueId());
        // владелец и админы могут дать звание и самим себе
        ClanMember target = me != null && me.uuid().equals(targetId) && me.role().canManageClan()
                ? me
                : checkTarget(player, clan, targetId);
        if (target == null) return;

        if (rank != null) {
            rank = rank.trim();
            String plain = ColorUtil.plain(ColorUtil.rich(rank)).trim();
            if (plain.isEmpty()) {
                error(player, "Звание не может быть пустым.");
                return;
            }
            if (plain.codePointCount(0, plain.length()) > config.clanRankMaxLength()) {
                error(player, "Слишком длинное звание (максимум " + config.clanRankMaxLength() + " символов).");
                return;
            }
            if (!player.hasPermission(Perms.PREFIX_FORMAT) && ColorUtil.hasDecorations(ColorUtil.rich(rank))) {
                error(player, "Жирный, курсив и другие стили доступны с привилегии Ultra.");
                return;
            }
        }
        target.rank(rank);
        manager.markDirty();
        if (rank == null) {
            broadcast(clan, "<gray>У игрока <white><name></white> больше нет звания.</gray>", ph("name", target.name()));
        } else {
            broadcast(clan, "<gray>Игрок <white><name></white> получил звание</gray> <rank><gray>.</gray>",
                    ph("name", target.name()), Placeholder.component("rank", ColorUtil.rich(rank)));
        }
    }

    public void transfer(Player player, UUID targetId) {
        Clan clan = requireRole(player, ClanRole.OWNER);
        if (clan == null) return;
        ClanMember target = clan.member(targetId);
        if (target == null || targetId.equals(player.getUniqueId())) {
            error(player, "Этот игрок не в твоём клане.");
            return;
        }
        manager.transfer(clan, targetId);
        broadcast(clan, "<gold>Новый владелец клана: <white><name></white>!</gold>", ph("name", target.name()));
    }

    // ---------------- настройки ----------------

    public void setDescription(Player player, String text) {
        Clan clan = requireManager(player);
        if (clan == null) return;
        if (text != null) {
            text = text.trim().replaceAll("\\s+", " ");
            if (text.isEmpty()) text = null;
        }
        if (text != null && text.length() > config.clanDescriptionMaxLength()) {
            error(player, "Слишком длинное описание (максимум " + config.clanDescriptionMaxLength() + " символов).");
            return;
        }
        clan.description(text);
        manager.markDirty();
        msg(player, text == null ? "<gray>Описание клана убрано.</gray>" : "<green>Описание клана обновлено.</green>");
    }

    public void toggleType(Player player) {
        Clan clan = requireManager(player);
        if (clan == null) return;
        clan.open(!clan.open());
        manager.markDirty();
        broadcast(clan, clan.open()
                ? "<gray>Тип вступления: <green>Открытый</green> - вступить может любой: /c join <id></gray>"
                : "<gray>Тип вступления: <red>По приглашению</red></gray>",
                Placeholder.unparsed("id", clan.id()));
    }

    /** Иконка клана = предмет в руке. */
    public void setIcon(Player player) {
        Clan clan = requireManager(player);
        if (clan == null) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        Material type = hand.getType();
        if (type.isAir()) {
            error(player, "Возьми в руку предмет, который станет иконкой клана.");
            return;
        }
        clan.icon(type);
        manager.markDirty();
        msg(player, "<green>Иконка клана изменена.</green>");
    }

    // ---------------- проверки ----------------

    /** Клан игрока, если у него есть роль не ниже нужной; иначе пишет ошибку и возвращает null. */
    public Clan requireRole(Player player, ClanRole role) {
        Clan clan = manager.getClan(player);
        if (clan == null) {
            error(player, "Ты не состоишь в клане. Создай свой: /c create <название>");
            return null;
        }
        ClanMember me = clan.member(player.getUniqueId());
        if (me == null || me.role().ordinal() > role.ordinal()) {
            error(player, role == ClanRole.OWNER ? "Это может только владелец клана." : "Это могут только владелец и администраторы клана.");
            return null;
        }
        return clan;
    }

    public Clan requireManager(Player player) {
        return requireRole(player, ClanRole.ADMIN);
    }

    /** Может ли actor управлять участником target (звание, исключение). */
    public static boolean canManage(ClanMember actor, ClanMember target) {
        if (actor == null || target == null || actor.uuid().equals(target.uuid())) return false;
        if (actor.role() == ClanRole.OWNER) return true;
        return actor.role() == ClanRole.ADMIN && target.role() == ClanRole.MEMBER;
    }

    private ClanMember checkTarget(Player player, Clan clan, UUID targetId) {
        if (clan == null) {
            error(player, "Ты не состоишь в клане.");
            return null;
        }
        ClanMember me = clan.member(player.getUniqueId());
        ClanMember target = clan.member(targetId);
        if (target == null) {
            error(player, "Этот игрок не в твоём клане.");
            return null;
        }
        if (!canManage(me, target)) {
            error(player, "У тебя нет прав управлять этим участником.");
            return null;
        }
        return target;
    }

    /** UUID участника клана по нику (онлайн или из списка клана). */
    public UUID findMember(Clan clan, String name) {
        if (clan == null) return null;
        for (ClanMember m : clan.membersMap().values()) {
            if (m.name().equalsIgnoreCase(name)) return m.uuid();
        }
        OfflinePlayer online = Bukkit.getPlayerExact(name);
        return online != null && clan.member(online.getUniqueId()) != null ? online.getUniqueId() : null;
    }
}
