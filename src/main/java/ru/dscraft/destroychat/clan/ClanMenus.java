package ru.dscraft.destroychat.clan;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import ru.dscraft.destroychat.util.ColorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Меню кланов.
 * <ul>
 *   <li>Список кланов ("Кланы, страница: 1/4") - иконки кланов по рейтингу, наведение - карточка клана;</li>
 *   <li>Клан ("Клан, игроки: 13") - верхние 3 ряда: функции клана, ниже - головы участников;</li>
 *   <li>Участник - звание, права администратора, исключение, передача владения.</li>
 * </ul>
 */
public final class ClanMenus {

    /** Ячейки под кланы в списке: 4 ряда по 7. */
    static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** Ячейки под участников в меню клана: ряды 4-5 (под тремя рядами функций). */
    static final int[] MEMBER_SLOTS = {
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44};

    // кнопки меню клана
    static final int SLOT_INFO = 4;
    static final int SLOT_STATS = 10;
    static final int SLOT_DESC = 12;
    static final int SLOT_TYPE = 14;
    static final int SLOT_INVITE = 16;
    static final int SLOT_ICON = 20;
    static final int SLOT_MEMBERSHIP = 22;
    static final int SLOT_TOP = 24;
    static final int SLOT_BACK = 45;
    static final int SLOT_PREV = 48;
    static final int SLOT_CLOSE = 49;
    static final int SLOT_NEXT = 50;
    static final int SLOT_MY_CLAN = 53;

    // кнопки меню участника
    static final int M_HEAD = 4;
    static final int M_RANK = 11;
    static final int M_ADMIN = 13;
    static final int M_KICK = 15;
    static final int M_OWNER = 22;
    static final int M_BACK = 18;

    private ClanMenus() {
    }

    // ---------------- holders ----------------

    public abstract static class Menu implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static final class ListMenu extends Menu {
        final int page;
        final Map<Integer, String> clanSlots = new HashMap<>();

        ListMenu(int page) {
            this.page = page;
        }
    }

    public static final class ClanMenu extends Menu {
        final String clanId;
        final int page;
        final Map<Integer, UUID> memberSlots = new HashMap<>();

        ClanMenu(String clanId, int page) {
            this.clanId = clanId;
            this.page = page;
        }
    }

    public static final class MemberMenu extends Menu {
        final String clanId;
        final UUID target;

        MemberMenu(String clanId, UUID target) {
            this.clanId = clanId;
            this.target = target;
        }
    }

    // ---------------- список кланов ----------------

    public static void openList(Player player, ClanManager manager, int page) {
        List<Clan> top = manager.top();
        int pages = Math.max(1, (top.size() + LIST_SLOTS.length - 1) / LIST_SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));

        ListMenu holder = new ListMenu(page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ColorUtil.parse("<dark_gray>• Кланы, страница: " + (page + 1) + "/" + pages + "</dark_gray>"));
        holder.inventory = inv;

        int start = page * LIST_SLOTS.length;
        for (int i = 0; i < LIST_SLOTS.length && start + i < top.size(); i++) {
            Clan clan = top.get(start + i);
            inv.setItem(LIST_SLOTS[i], item(clan.icon(), ClanText.name(clan), ClanText.card(clan, manager, true)));
            holder.clanSlots.put(LIST_SLOTS[i], clan.id());
        }
        if (top.isEmpty()) {
            inv.setItem(22, item(Material.PAPER, ColorUtil.parse("<yellow>Кланов пока нет</yellow>"),
                    lines("<gray>Создай первый клан:</gray>", "<white>/c create \\<название></white>")));
        }

        if (page > 0) inv.setItem(SLOT_PREV, item(Material.ARROW, ColorUtil.parse("<white>← Предыдущая страница</white>"), List.of()));
        if (page < pages - 1) inv.setItem(SLOT_NEXT, item(Material.ARROW, ColorUtil.parse("<white>Следующая страница →</white>"), List.of()));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, ColorUtil.parse("<red>Закрыть</red>"), List.of()));
        inv.setItem(SLOT_BACK, item(Material.GOLD_INGOT, ColorUtil.parse("<gold>Топ кланов</gold>"),
                lines("<gray>Кланы отсортированы по рейтингу.</gray>",
                        "<gray>За убийство игрока клан получает</gray>",
                        "<gold>" + manager.config().clanKillRating() + " КР</gold><gray> (кланового рейтинга).</gray>",
                        "",
                        "<gray>Нажми, чтобы вывести топ в чат.</gray>")));

        Clan mine = manager.getClan(player);
        if (mine != null) {
            List<Component> lore = new ArrayList<>(ClanText.card(mine, manager, false));
            lore.add(Component.empty());
            lore.add(ColorUtil.parse("<gray>Нажми, чтобы открыть свой клан.</gray>"));
            inv.setItem(SLOT_MY_CLAN, item(mine.icon(), ColorUtil.parse("<green>Мой клан: </green>").append(ClanText.name(mine)), lore));
        } else {
            inv.setItem(SLOT_MY_CLAN, item(Material.WRITABLE_BOOK, ColorUtil.parse("<green>Создать клан</green>"),
                    lines("<gray>Ты пока не в клане.</gray>",
                            "<gray>Создать свой:</gray> <white>/c create \\<название></white>",
                            "<gray>Или вступи в открытый клан из списка.</gray>")));
        }
        player.openInventory(inv);
    }

    // ---------------- клан ----------------

    public static void openClan(Player player, ClanManager manager, Clan clan, int page) {
        List<ClanMember> members = clan.sortedMembers();
        int pages = Math.max(1, (members.size() + MEMBER_SLOTS.length - 1) / MEMBER_SLOTS.length);
        page = Math.max(0, Math.min(page, pages - 1));

        ClanMenu holder = new ClanMenu(clan.id(), page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                ColorUtil.parse("<dark_gray>• Клан, игроки: " + members.size() + "</dark_gray>"));
        holder.inventory = inv;

        ClanMember me = clan.member(player.getUniqueId());
        boolean manage = me != null && me.role().canManageClan();
        Clan myClan = manager.getClan(player);

        // ---- 3 верхних ряда: функции ----
        inv.setItem(SLOT_INFO, item(clan.icon(), ClanText.name(clan), ClanText.card(clan, manager, false)));

        inv.setItem(SLOT_STATS, item(Material.NETHER_STAR, ColorUtil.parse("<gold>Статистика клана</gold>"), List.of(
                ColorUtil.parse("<white>Место в топе: <yellow>#" + manager.place(clan) + "</yellow></white>"),
                ColorUtil.parse("<white>Рейтинг: <gold>" + clan.rating() + " КР</gold></white>"),
                ColorUtil.parse("<white>Убито игроков: <red>" + clan.kills() + "</red></white>"),
                ColorUtil.parse("<white>Смертей: <gray>" + clan.deaths() + "</gray></white>"),
                ColorUtil.parse("<white>Побед на ПВП: <green>" + clan.winPercent() + "%</green></white>"),
                ColorUtil.parse("<white>Статус: </white>").append(ColorUtil.parse(manager.status(clan))),
                Component.empty(),
                ColorUtil.parse("<gray>За убийство игрока: <gold>+" + manager.config().clanKillRating() + " КР</gold></gray>"))));

        List<Component> desc = new ArrayList<>();
        if (clan.description() == null) desc.add(ColorUtil.parse("<gray>Нет описания...</gray>"));
        else for (String line : ClanText.wrap(clan.description(), 36)) {
            desc.add(ColorUtil.parse("<gray><line></gray>", Placeholder.unparsed("line", line)));
        }
        if (manage) {
            desc.add(Component.empty());
            desc.add(ColorUtil.parse("<yellow>ЛКМ</yellow><gray> - изменить описание</gray>"));
            desc.add(ColorUtil.parse("<yellow>ПКМ</yellow><gray> - убрать описание</gray>"));
        }
        inv.setItem(SLOT_DESC, item(Material.WRITABLE_BOOK, ColorUtil.parse("<white>Описание</white>"), desc));

        List<Component> type = new ArrayList<>();
        type.add(ColorUtil.parse(clan.open()
                ? "<white>Сейчас: <green>Открытый</green></white>"
                : "<white>Сейчас: <red>По приглашению</red></white>"));
        type.add(ColorUtil.parse(clan.open()
                ? "<gray>Вступить может любой игрок.</gray>"
                : "<gray>Вступить можно только по приглашению.</gray>"));
        if (manage) {
            type.add(Component.empty());
            type.add(ColorUtil.parse("<yellow>Нажми</yellow><gray>, чтобы переключить.</gray>"));
        }
        inv.setItem(SLOT_TYPE, item(clan.open() ? Material.OAK_DOOR : Material.IRON_DOOR,
                ColorUtil.parse("<white>Тип вступления</white>"), type));

        if (manage) {
            inv.setItem(SLOT_INVITE, item(Material.NAME_TAG, ColorUtil.parse("<green>Пригласить игрока</green>"),
                    lines("<gray>Нажми и напиши ник в чат,</gray>", "<gray>или:</gray> <white>/c invite \\<ник></white>")));
            inv.setItem(SLOT_ICON, item(Material.ITEM_FRAME, ColorUtil.parse("<white>Иконка клана</white>"),
                    lines("<gray>Возьми предмет в руку и нажми,</gray>", "<gray>он станет иконкой клана в списке.</gray>")));
        }

        if (me != null) {
            if (me.role() == ClanRole.OWNER) {
                inv.setItem(SLOT_MEMBERSHIP, item(Material.TNT, ColorUtil.parse("<red>Распустить клан</red>"),
                        lines("<gray>Клан будет удалён навсегда!</gray>", "", "<red>Shift + ПКМ</red><gray> - распустить</gray>")));
            } else {
                inv.setItem(SLOT_MEMBERSHIP, item(Material.RED_DYE, ColorUtil.parse("<red>Покинуть клан</red>"),
                        lines("<red>Shift + ПКМ</red><gray> - выйти из клана</gray>")));
            }
        } else if (myClan == null) {
            ClanManager.Invite invite = manager.getInvite(player.getUniqueId());
            boolean invited = invite != null && invite.clanId().equals(clan.id());
            if (clan.open() || invited) {
                inv.setItem(SLOT_MEMBERSHIP, item(Material.LIME_DYE, ColorUtil.parse("<green>Вступить в клан</green>"),
                        lines(invited ? "<gray>У тебя есть приглашение в этот клан.</gray>" : "<gray>Клан открытый.</gray>",
                                "", "<yellow>Нажми</yellow><gray>, чтобы вступить.</gray>")));
            } else {
                inv.setItem(SLOT_MEMBERSHIP, item(Material.GRAY_DYE, ColorUtil.parse("<gray>Вступление по приглашению</gray>"),
                        lines("<gray>Попроси владельца или администратора</gray>", "<gray>клана пригласить тебя.</gray>")));
            }
        }

        inv.setItem(SLOT_TOP, item(Material.GOLD_INGOT, ColorUtil.parse("<gold>Топ кланов</gold>"),
                lines("<white>Место клана: <yellow>#" + manager.place(clan) + "</yellow></white>", "",
                        "<gray>Нажми, чтобы открыть список кланов.</gray>")));

        // ---- участники ----
        int start = page * MEMBER_SLOTS.length;
        for (int i = 0; i < MEMBER_SLOTS.length && start + i < members.size(); i++) {
            ClanMember m = members.get(start + i);
            inv.setItem(MEMBER_SLOTS[i], memberHead(m, ClanActions.canManage(me, m)
                    || (me != null && me.uuid().equals(m.uuid()) && me.role().canManageClan())));
            holder.memberSlots.put(MEMBER_SLOTS[i], m.uuid());
        }

        // ---- нижний ряд ----
        inv.setItem(SLOT_BACK, item(Material.ARROW, ColorUtil.parse("<white>← К списку кланов</white>"), List.of()));
        if (page > 0) inv.setItem(SLOT_PREV, item(Material.SPECTRAL_ARROW, ColorUtil.parse("<white>← Участники</white>"), List.of()));
        if (page < pages - 1) inv.setItem(SLOT_NEXT, item(Material.SPECTRAL_ARROW, ColorUtil.parse("<white>Участники →</white>"), List.of()));
        inv.setItem(SLOT_CLOSE, item(Material.BARRIER, ColorUtil.parse("<red>Закрыть</red>"), List.of()));

        player.openInventory(inv);
    }

    private static ItemStack memberHead(ClanMember m, boolean manageable) {
        boolean online = Bukkit.getPlayer(m.uuid()) != null;
        List<Component> lore = new ArrayList<>();
        lore.add(ColorUtil.parse(m.role().display()));
        lore.add(ColorUtil.parse("<white>Звание: </white>").append(m.rank() == null
                ? ColorUtil.parse("<gray>нет</gray>")
                : ColorUtil.rich(m.rank())));
        lore.add(ColorUtil.parse("<white>Убийств за клан: <red>" + m.kills() + "</red></white>"));
        lore.add(ColorUtil.parse("<white>В клане с: <gray>" + ClanText.date(m.joinedAt()) + "</gray></white>"));
        lore.add(ColorUtil.parse(online ? "<green>● В сети</green>" : "<gray>● Не в сети</gray>"));
        if (manageable) {
            lore.add(Component.empty());
            lore.add(ColorUtil.parse("<yellow>Нажми</yellow><gray>, чтобы управлять участником.</gray>"));
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(m.uuid()));
        String color = switch (m.role()) {
            case OWNER -> "<gold>";
            case ADMIN -> "<aqua>";
            default -> "<white>";
        };
        Component name = ColorUtil.parse(color + "<name>", Placeholder.unparsed("name", m.name()));
        if (m.rank() != null) {
            name = name.append(ColorUtil.parse(" <dark_gray>|</dark_gray> ")).append(ColorUtil.rich(m.rank()));
        }
        applyMeta(meta, name, lore);
        head.setItemMeta(meta);
        return head;
    }

    // ---------------- участник ----------------

    public static void openMember(Player player, Clan clan, ClanMember target) {
        MemberMenu holder = new MemberMenu(clan.id(), target.uuid());
        Inventory inv = Bukkit.createInventory(holder, 27,
                ColorUtil.parse("<dark_gray>• Участник: <name></dark_gray>", Placeholder.unparsed("name", target.name())));
        holder.inventory = inv;

        ClanMember me = clan.member(player.getUniqueId());
        boolean owner = me != null && me.role() == ClanRole.OWNER;
        boolean self = me != null && me.uuid().equals(target.uuid());

        inv.setItem(M_HEAD, memberHead(target, false));
        inv.setItem(M_RANK, item(Material.NAME_TAG, ColorUtil.parse("<yellow>Звание</yellow>"), List.of(
                ColorUtil.parse("<white>Сейчас: </white>").append(target.rank() == null
                        ? ColorUtil.parse("<gray>нет</gray>") : ColorUtil.rich(target.rank())),
                Component.empty(),
                ColorUtil.parse("<yellow>ЛКМ</yellow><gray> - установить (напиши в чат)</gray>"),
                ColorUtil.parse("<yellow>ПКМ</yellow><gray> - убрать звание</gray>"),
                ColorUtil.parse("<gray>Можно с цветами: &c, &#FF55FF, градиенты.</gray>"))));

        if (owner && !self) {
            boolean admin = target.role() == ClanRole.ADMIN;
            inv.setItem(M_ADMIN, item(admin ? Material.GOLDEN_HELMET : Material.LEATHER_HELMET,
                    ColorUtil.parse(admin ? "<red>Забрать права администратора</red>" : "<aqua>Дать права администратора</aqua>"),
                    lines("<gray>Администратор может приглашать и</gray>",
                            "<gray>исключать участников, давать звания,</gray>",
                            "<gray>менять описание, иконку и тип вступления.</gray>")));
            inv.setItem(M_OWNER, item(Material.NETHER_STAR, ColorUtil.parse("<gold>Передать владение кланом</gold>"),
                    lines("<gray>Ты станешь администратором.</gray>", "", "<red>Shift + ПКМ</red><gray> - передать</gray>")));
        }
        if (!self) {
            inv.setItem(M_KICK, item(Material.BARRIER, ColorUtil.parse("<red>Исключить из клана</red>"),
                    lines("<red>Shift + ПКМ</red><gray> - исключить</gray>")));
        }
        inv.setItem(M_BACK, item(Material.ARROW, ColorUtil.parse("<white>← Назад к клану</white>"), List.of()));
        player.openInventory(inv);
    }

    // ---------------- предметы ----------------

    static List<Component> lines(String... miniMessage) {
        List<Component> out = new ArrayList<>();
        for (String s : miniMessage) out.add(s.isEmpty() ? Component.empty() : ColorUtil.parse(s));
        return out;
    }

    static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            applyMeta(meta, name, lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void applyMeta(ItemMeta meta, Component name, List<Component> lore) {
        meta.displayName(noItalic(name));
        List<Component> l = new ArrayList<>(lore.size());
        for (Component c : lore) l.add(noItalic(c));
        meta.lore(l);
        meta.addItemFlags(ItemFlag.values());
    }

    private static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
