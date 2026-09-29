package ru.dscraft.mediaclans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Меню кланов (как на сервере-образце):
 * список кланов, клан, участник, настройки, история, закреплённые сообщения, роли, роль.
 * Каждое меню само хранит, что делает клик по каждому слоту.
 */
public final class ClanMenus {

    /** Кланы в списке и история: 4 ряда по 7. */
    static final int[] GRID = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** Участники в меню клана: ряды 2-4. */
    static final int[] MEMBER_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35};

    /** Роли: 3 ряда по 5, по центру. */
    static final int[] ROLE_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

    /** Права роли: 16 штук, остальные ячейки ряда - чёрные. */
    static final int[] PERM_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final ClanManager manager;
    private final ClanActions actions;
    private final ChatInput input;

    /** Настройки списка кланов у каждого игрока: сортировка и скрытие закрытых. */
    private final Map<UUID, Boolean> sortAscending = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hideClosed = new ConcurrentHashMap<>();
    /** Подтверждение удаления клана: до какого времени второй клик удалит клан. */
    private final Map<UUID, Long> disbandConfirm = new ConcurrentHashMap<>();

    public ClanMenus(ClanManager manager, ClanActions actions, ChatInput input) {
        this.manager = manager;
        this.actions = actions;
        this.input = input;
    }

    // ---------------- holder ----------------

    /** Меню: действия по слотам + клик по предмету своего инвентаря (выбор иконки). */
    public static final class Menu implements InventoryHolder {
        Inventory inventory;
        final Map<Integer, BiConsumer<Player, ClickType>> clicks = new HashMap<>();
        Consumer<ItemStack> ownItemClick;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private Menu create(String title) {
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, 54, ColorUtil.parse("<dark_gray>• " + title + "</dark_gray>"));
        return menu;
    }

    private void set(Menu menu, int slot, ItemStack item, BiConsumer<Player, ClickType> click) {
        menu.inventory.setItem(slot, item);
        if (click != null) menu.clicks.put(slot, click);
    }

    // ---------------- список кланов ----------------

    public void openList(Player player, int page) {
        boolean asc = sortAscending.getOrDefault(player.getUniqueId(), false);
        boolean hide = hideClosed.getOrDefault(player.getUniqueId(), false);
        List<Clan> top = manager.top();
        if (asc) java.util.Collections.reverse(top);
        int closed = (int) top.stream().filter(c -> c.joinType() == Clan.JoinType.INVITE).count();
        if (hide) top.removeIf(c -> c.joinType() == Clan.JoinType.INVITE);

        int pages = Math.max(1, (top.size() + GRID.length - 1) / GRID.length);
        int p = Math.max(0, Math.min(page, pages - 1));
        Menu menu = create("Кланы, страница: " + (p + 1) + "/" + pages);

        int start = p * GRID.length;
        for (int i = 0; i < GRID.length && start + i < top.size(); i++) {
            Clan clan = top.get(start + i);
            set(menu, GRID[i], item(clan.icon(), ClanText.name(clan), ClanText.card(clan, manager, true)),
                    (pl, c) -> openClan(pl, clan, 0));
        }
        if (top.isEmpty()) {
            set(menu, 22, item(Material.PAPER, ColorUtil.parse("<yellow>Кланов пока нет</yellow>"),
                    lines("<white>Создай первый клан!</white>")), null);
        }

        set(menu, 45, item(Material.LEAD, ColorUtil.parse("<#B884FF>Сортировка</#B884FF>"), lines(
                "<white>Сортировка кланов.</white>",
                "",
                "<gray>Текущая:</gray>",
                asc ? "<gray>  Больше рейтинга.</gray>" : "<aqua>✔</aqua> <white>Больше рейтинга.</white>",
                asc ? "<aqua>✔</aqua> <white>Меньше рейтинга.</white>" : "<gray>  Меньше рейтинга.</gray>",
                "",
                "<white>Нажми чтобы отсортировать</white>")), (pl, c) -> {
            sortAscending.put(pl.getUniqueId(), !asc);
            openList(pl, 0);
        });

        Clan mine = manager.getClan(player);
        if (mine != null) {
            List<Component> lore = new ArrayList<>(ClanText.card(mine, manager, false));
            lore.add(Component.empty());
            lore.add(ColorUtil.parse("<white>Нажми чтобы открыть свой клан.</white>"));
            set(menu, 49, item(mine.icon(), ColorUtil.parse("<#B884FF>Мой клан: </#B884FF>").append(ClanText.name(mine)), lore),
                    (pl, c) -> openClan(pl, mine, 0));
        } else {
            set(menu, 49, item(Material.ARMOR_STAND, ColorUtil.parse("<#B884FF>Создать свой клан</#B884FF>"),
                    lines("<white>Нажми если хочешь клан.</white>")), (pl, c) -> askCreate(pl));
        }

        if (p > 0) {
            set(menu, 46, item(Material.ARROW, ColorUtil.parse("<#B884FF>Предыдущая</#B884FF>"),
                    lines("<white>Открыть " + p + " страницу...</white>")), (pl, c) -> openList(pl, p - 1));
        }
        if (p < pages - 1) {
            set(menu, 52, item(Material.ARROW, ColorUtil.parse("<#B884FF>Следующая</#B884FF>"),
                    lines("<white>Открыть " + (p + 2) + " страницу...</white>")), (pl, c) -> openList(pl, p + 1));
        }
        set(menu, 53, item(hide ? Material.GRAY_DYE : Material.SLIME_BALL,
                ColorUtil.parse(hide ? "<#B884FF>Показать закрытые кланы</#B884FF>" : "<#B884FF>Скрыть закрытые кланы</#B884FF>"),
                lines(hide ? "<white>Скрыто <green>" + closed + "</green> закрытых кланов.</white>"
                        : "<white>Отображено <green>" + closed + "</green> закрытых кланов.</white>")), (pl, c) -> {
            hideClosed.put(pl.getUniqueId(), !hide);
            openList(pl, 0);
        });
        player.openInventory(menu.inventory);
    }

    public void askCreate(Player player) {
        input.ask(player, "Напиши в чат название для нового клана.", text -> {
            actions.create(player, text.split("\\s+")[0]);
            Clan created = manager.getClan(player);
            if (created != null) openClan(player, created, 0);
        });
    }

    // ---------------- клан ----------------

    public void openClan(Player player, Clan clan, int page) {
        if (manager.getById(clan.id()) != clan) {
            player.closeInventory();
            return;
        }
        List<ClanMember> members = clan.sortedMembers();
        int max = actions.settings().maxMembers();
        int pages = Math.max(1, (Math.max(members.size(), Math.min(max, MEMBER_SLOTS.length)) + MEMBER_SLOTS.length - 1) / MEMBER_SLOTS.length);
        int p = Math.max(0, Math.min(page, pages - 1));
        Menu menu = create("Клан, игроки: " + members.size());
        UUID me = player.getUniqueId();
        boolean inClan = clan.member(me) != null;

        set(menu, 3, item(Material.ITEM_FRAME, ColorUtil.parse("<#B884FF>Информация о клане</#B884FF>"),
                ClanText.info(clan, manager)), null);
        set(menu, 5, item(Material.BARREL, ColorUtil.parse("<#B884FF>Клановый чат</#B884FF>"),
                lines("<white>Поставь знак <#B884FF>" + escape(actions.settings().chatSymbol()) + "</#B884FF> в начале сообщения.</white>")), null);
        set(menu, 6, item(Material.LECTERN, ColorUtil.parse("<#B884FF>Закреплённые сообщения</#B884FF>"),
                lines("<white>У клана <#B884FF>" + clan.pins().size() + "</#B884FF> сообщений.</white>")),
                (pl, c) -> {
                    if (clan.member(pl.getUniqueId()) != null) openPins(pl, clan);
                });

        // ---- участники ----
        int start = p * MEMBER_SLOTS.length;
        for (int i = 0; i < MEMBER_SLOTS.length; i++) {
            int idx = start + i;
            if (idx < members.size()) {
                ClanMember m = members.get(idx);
                boolean manage = clan.canManage(me, m)
                        && (clan.has(me, Perm.KICK) || clan.has(me, Perm.SET_ROLE) || me.equals(clan.owner()));
                set(menu, MEMBER_SLOTS[i], head(m, ClanText.memberTitle(clan, m), ClanText.member(clan, m, manage)),
                        manage ? (pl, c) -> openMember(pl, clan, m.uuid()) : null);
            } else if (idx < max) {
                set(menu, MEMBER_SLOTS[i], item(Material.BLACK_CONCRETE, ColorUtil.parse("<gray>Свободное место</gray>"),
                        clan.has(me, Perm.INVITE) ? lines("<white>Нажми чтобы пригласить игрока.</white>") : List.of()),
                        clan.has(me, Perm.INVITE) ? (pl, c) -> askInvite(pl, clan) : null);
            }
        }

        // ---- нижний ряд ----
        if (inClan) {
            set(menu, 45, item(Material.REDSTONE_TORCH, ColorUtil.parse("<#B884FF>Опции</#B884FF>"),
                    lines("<white>Нажми чтобы настроить клан.</white>")), (pl, c) -> openSettings(pl, clan));
            if (!me.equals(clan.owner())) {
                set(menu, 47, item(Material.OAK_DOOR, ColorUtil.parse("<red>Покинуть клан</red>"),
                        lines("<white>Shift + ПКМ чтобы выйти из клана.</white>")), (pl, c) -> {
                    if (c != ClickType.SHIFT_RIGHT) return;
                    pl.closeInventory();
                    actions.leave(pl);
                });
            }
        } else if (manager.getClan(player) == null) {
            ClanManager.Invite invite = manager.getInvite(me);
            boolean invited = invite != null && invite.clanId().equals(clan.id());
            if (invited) {
                set(menu, 47, item(Material.OAK_DOOR, ColorUtil.parse("<green>Вступить в клан</green>"),
                        lines("<white>У тебя есть приглашение в этот клан.</white>")), (pl, c) -> {
                    actions.accept(pl);
                    openClan(pl, clan, 0);
                });
            } else if (clan.joinType() == Clan.JoinType.PASSWORD) {
                set(menu, 47, item(Material.OAK_DOOR, ColorUtil.parse("<gold>Вступить по паролю</gold>"),
                        lines("<white>Нажми и напиши пароль клана в чат.</white>")), (pl, c) ->
                        input.ask(pl, "Напиши в чат пароль клана.", text -> {
                            actions.joinWithPassword(pl, clan, text);
                            if (clan.member(pl.getUniqueId()) != null) openClan(pl, clan, 0);
                        }));
            } else {
                set(menu, 47, item(Material.IRON_DOOR, ColorUtil.parse("<red>Вход по приглашению</red>"),
                        lines("<white>Попроси участника клана пригласить тебя.</white>")), null);
            }
        }
        set(menu, 49, item(Material.MAP, ColorUtil.parse("<#B884FF>На главную страницу</#B884FF>"),
                lines("<white>Открыть все кланы сервера.</white>")), (pl, c) -> openList(pl, 0));
        if (clan.has(me, Perm.INVITE) && clan.size() < max) {
            set(menu, 51, item(Material.NAME_TAG, ColorUtil.parse("<#B884FF>Пригласить игрока</#B884FF>"),
                    lines("<white>Нажми и напиши ник в чат.</white>")), (pl, c) -> askInvite(pl, clan));
        }
        if (p > 0) {
            set(menu, 46, item(Material.ARROW, ColorUtil.parse("<#B884FF>Предыдущая</#B884FF>"), List.of()),
                    (pl, c) -> openClan(pl, clan, p - 1));
        }
        if (p < pages - 1) {
            set(menu, 52, item(Material.ARROW, ColorUtil.parse("<#B884FF>Следующая</#B884FF>"), List.of()),
                    (pl, c) -> openClan(pl, clan, p + 1));
        }
        if (me.equals(clan.owner())) {
            set(menu, 53, item(Material.BARRIER, ColorUtil.parse("<red>Удалить клан</red>"),
                    lines("<white>Удалить этот клан.</white>", "", "<gray>Нажми два раза для подтверждения.</gray>")), (pl, c) -> {
                Long until = disbandConfirm.get(pl.getUniqueId());
                if (until != null && until > System.currentTimeMillis()) {
                    disbandConfirm.remove(pl.getUniqueId());
                    pl.closeInventory();
                    actions.disband(pl);
                } else {
                    disbandConfirm.put(pl.getUniqueId(), System.currentTimeMillis() + 5000L);
                    actions.msg(pl, "<red>Нажми ещё раз в течение 5 секунд, чтобы удалить клан навсегда.</red>");
                }
            });
        }
        player.openInventory(menu.inventory);
    }

    private void askInvite(Player player, Clan clan) {
        input.ask(player, "Напиши в чат ник игрока для приглашения.", text -> actions.invite(player, text.split("\\s+")[0]));
    }

    // ---------------- участник ----------------

    public void openMember(Player player, Clan clan, UUID targetId) {
        ClanMember target = clan.member(targetId);
        if (target == null || !clan.canManage(player.getUniqueId(), target)) {
            openClan(player, clan, 0);
            return;
        }
        Menu menu = create("Клан, участник " + target.name());
        UUID me = player.getUniqueId();
        set(menu, 4, head(target, ClanText.memberTitle(clan, target), ClanText.member(clan, target, false)), null);
        if (clan.has(me, Perm.SET_ROLE)) {
            set(menu, 20, item(Material.ANVIL, ColorUtil.parse("<#B884FF>Изменить роль</#B884FF>"), List.of(
                    ColorUtil.parse("<white>Сейчас: </white>").append(ClanText.rolePrefix(clan.roleOf(target))),
                    ColorUtil.parse("<white>Нажми чтобы выбрать роль.</white>"))), (pl, c) -> openRolePick(pl, clan, targetId));
        }
        if (clan.has(me, Perm.KICK)) {
            set(menu, 22, item(Material.BARRIER, ColorUtil.parse("<red>Исключить из клана</red>"),
                    lines("<white>Shift + ПКМ чтобы исключить.</white>")), (pl, c) -> {
                if (c != ClickType.SHIFT_RIGHT) return;
                actions.kick(pl, targetId);
                openClan(pl, clan, 0);
            });
        }
        if (me.equals(clan.owner())) {
            set(menu, 24, item(Material.NETHER_STAR, ColorUtil.parse("<gold>Передать владение кланом</gold>"),
                    lines("<white>Ты получишь начальную роль.</white>", "", "<white>Shift + ПКМ чтобы передать.</white>")), (pl, c) -> {
                if (c != ClickType.SHIFT_RIGHT) return;
                actions.transfer(pl, targetId);
                openClan(pl, clan, 0);
            });
        }
        set(menu, 49, backDoor(), (pl, c) -> openClan(pl, clan, 0));
        player.openInventory(menu.inventory);
    }

    private void openRolePick(Player player, Clan clan, UUID targetId) {
        ClanMember target = clan.member(targetId);
        if (target == null) {
            openClan(player, clan, 0);
            return;
        }
        Menu menu = create("Клан, выбор роли");
        List<ClanRole> roles = clan.roles();
        for (int i = 0; i < roles.size() && i < ROLE_SLOTS.length; i++) {
            ClanRole role = roles.get(i);
            if (role.leader()) continue;
            boolean current = role == clan.roleOf(target);
            set(menu, ROLE_SLOTS[i], item(new ItemStack(role.icon()), ClanText.rolePrefix(role),
                    lines(current ? "<green>Текущая роль игрока.</green>" : "<white>Нажми чтобы выдать эту роль.</white>")),
                    (pl, c) -> {
                        actions.setRole(pl, targetId, role.id());
                        openMember(pl, clan, targetId);
                    });
        }
        set(menu, 49, backDoor(), (pl, c) -> openMember(pl, clan, targetId));
        player.openInventory(menu.inventory);
    }

    // ---------------- настройки ----------------

    public void openSettings(Player player, Clan clan) {
        if (clan.member(player.getUniqueId()) == null) {
            openClan(player, clan, 0);
            return;
        }
        Menu menu = create("Клан, настройки");
        UUID me = player.getUniqueId();

        set(menu, 11, item(Material.GRAY_SHULKER_BOX, ColorUtil.parse("<#B884FF>Настройка ролей</#B884FF>"),
                lines("<white>У клана <#B884FF>" + clan.roles().size() + "</#B884FF> ролей.</white>")), (pl, c) -> openRoles(pl, clan));
        set(menu, 13, item(Material.LANTERN, ColorUtil.parse("<#B884FF>История клана</#B884FF>"),
                lines("<white>Логи входов, выходов и киков.</white>")), (pl, c) -> {
            if (clan.has(pl.getUniqueId(), Perm.HISTORY)) openHistory(pl, clan);
            else actions.error(pl, "У твоей роли нет права: " + Perm.HISTORY.title() + ".");
        });
        boolean password = clan.joinType() == Clan.JoinType.PASSWORD;
        set(menu, 15, item(password ? Material.ENDER_EYE : Material.ENDER_PEARL,
                ColorUtil.parse(password ? "<gold>Вход по паролю</gold>" : "<red>Вход по приглашению</red>"),
                lines(password ? "<white>Нажми чтобы включить <u>вход по приглашению</u>.</white>"
                        : "<white>Нажми чтобы включить <u>вход по паролю</u>.</white>")), (pl, c) -> {
            actions.toggleJoinType(pl);
            openSettings(pl, clan);
        });
        set(menu, 20, item(Material.WOODEN_SWORD, ColorUtil.parse("<#B884FF>PvP клана</#B884FF>"),
                lines(clan.pvp() ? "<white>Огонь по своим: <green>Включён</green></white>" : "<white>Огонь по своим: <red>Выключен</red></white>",
                        clan.pvp() ? "<white>Нажми чтобы выключить.</white>" : "<white>Нажми чтобы включить.</white>")), (pl, c) -> {
            actions.togglePvp(pl);
            openSettings(pl, clan);
        });
        set(menu, 22, item(Material.PAINTING, ColorUtil.parse("<#B884FF>Изменить иконку</#B884FF>"),
                lines("<white>Нажми по предмету инвентаря.</white>")), null);
        menu.ownItemClick = item -> {
            actions.setIcon(player, item);
            openSettings(player, clan);
        };
        boolean seePassword = clan.has(me, Perm.PASSWORD);
        set(menu, 24, item(Material.FILLED_MAP, ColorUtil.parse("<#B884FF>Пароль клана</#B884FF>"),
                lines(seePassword
                        ? "<white>У клана <#B884FF>" + escape(clan.password() == null ? "нет" : clan.password()) + "</#B884FF> пароль,</white>"
                        : "<white>Пароль скрыт.</white>",
                        seePassword ? "<white>нажми чтобы изменить.</white>" : "")), (pl, c) -> {
            if (!clan.has(pl.getUniqueId(), Perm.PASSWORD)) {
                actions.error(pl, "У твоей роли нет права: " + Perm.PASSWORD.title() + ".");
                return;
            }
            input.ask(pl, "Напиши в чат пароль клана.", text -> {
                actions.setPassword(pl, text);
                openSettings(pl, clan);
            });
        });
        set(menu, 29, item(Material.NAME_TAG, ColorUtil.parse("<#B884FF>Изменить название</#B884FF>"),
                lines("<white>Нажми чтобы поменять название.</white>")), (pl, c) -> {
            if (!clan.has(pl.getUniqueId(), Perm.RENAME)) {
                actions.error(pl, "У твоей роли нет права: " + Perm.RENAME.title() + ".");
                return;
            }
            input.ask(pl, "Напиши в чат новое название клана.", text -> {
                actions.rename(pl, text.split("\\s+")[0]);
                openSettings(pl, clan);
            });
        });
        set(menu, 31, item(Material.WRITABLE_BOOK, ColorUtil.parse("<#B884FF>Изменить описание</#B884FF>"),
                lines("<white>Нажми чтобы поменять описание.</white>")), (pl, c) -> {
            if (!clan.has(pl.getUniqueId(), Perm.DESCRIPTION)) {
                actions.error(pl, "У твоей роли нет права: " + Perm.DESCRIPTION.title() + ".");
                return;
            }
            input.ask(pl, "Напиши в чат новое описание клана.", text -> {
                actions.setDescription(pl, text);
                openSettings(pl, clan);
            });
        });
        List<Component> ann = new ArrayList<>();
        ann.add(clan.announcement() == null ? ColorUtil.parse("<gray>Объявлений ещё не было.</gray>")
                : ColorUtil.parse("<white><text></white>", Placeholder.unparsed("text", clan.announcement())));
        ann.add(ColorUtil.parse("<white>Написать всем игрокам онлайн.</white>"));
        set(menu, 33, item(Material.PAPER, ColorUtil.parse("<#B884FF>Объявление для клана</#B884FF>"), ann), (pl, c) -> {
            if (!clan.has(pl.getUniqueId(), Perm.ANNOUNCE)) {
                actions.error(pl, "У твоей роли нет права: " + Perm.ANNOUNCE.title() + ".");
                return;
            }
            input.ask(pl, "Напиши в чат объявление для клана.", text -> actions.announce(pl, text));
        });
        set(menu, 49, backDoor(), (pl, c) -> openClan(pl, clan, 0));
        player.openInventory(menu.inventory);
    }

    // ---------------- история ----------------

    public void openHistory(Player player, Clan clan) {
        Menu menu = create("История клана");
        List<HistoryEntry> history = new ArrayList<>(clan.history());
        java.util.Collections.reverse(history);
        for (int i = 0; i < GRID.length && i < history.size(); i++) {
            HistoryEntry h = history.get(i);
            Material icon;
            String title;
            String line;
            switch (h.type()) {
                case CREATE -> {
                    icon = Material.NETHER_STAR;
                    title = "Создание клана";
                    line = "<white>Создатель: <#B884FF><a></#B884FF></white>";
                }
                case JOIN -> {
                    icon = Material.LIME_DYE;
                    title = "Вход в клан";
                    line = "<white>Игрок: <#B884FF><a></#B884FF></white>";
                }
                case LEAVE -> {
                    icon = Material.RED_DYE;
                    title = "Выход из клана";
                    line = "<white>Игрок: <#B884FF><a></#B884FF></white>";
                }
                case KICK -> {
                    icon = Material.BARRIER;
                    title = "Исключение";
                    line = "<white><#B884FF><a></#B884FF> исключил <#B884FF><t></#B884FF></white>";
                }
                default -> {
                    icon = Material.GOLDEN_HELMET;
                    title = "Передача владения";
                    line = "<white><#B884FF><a></#B884FF> передал клан <#B884FF><t></#B884FF></white>";
                }
            }
            set(menu, GRID[i], item(new ItemStack(icon), ColorUtil.parse("<#B884FF>" + title + "</#B884FF>"), List.of(
                    ColorUtil.parse(line, Placeholder.unparsed("a", h.actor() == null ? "?" : h.actor()),
                            Placeholder.unparsed("t", h.target() == null ? "?" : h.target())),
                    Component.empty(),
                    ColorUtil.parse("<gray>Дата: " + ClanText.date(h.time()) + "</gray>"))), null);
        }
        set(menu, 49, backDoor(), (pl, c) -> openSettings(pl, clan));
        player.openInventory(menu.inventory);
    }

    // ---------------- закреплённые сообщения ----------------

    public void openPins(Player player, Clan clan) {
        Menu menu = create("Закреплённые сообщения");
        boolean canPin = clan.has(player.getUniqueId(), Perm.PIN);
        List<Pin> pins = new ArrayList<>(clan.pins());
        for (int i = 0; i < GRID.length && i < pins.size(); i++) {
            Pin pin = pins.get(i);
            int index = i;
            List<Component> lore = new ArrayList<>();
            for (String line : ClanText.wrap(pin.text(), 36)) {
                lore.add(ColorUtil.parse("<white><l></white>", Placeholder.unparsed("l", line)));
            }
            lore.add(Component.empty());
            lore.add(ColorUtil.parse("<gray>Закрепил <aqua><a></aqua>, " + ClanText.date(pin.time()) + "</gray>",
                    Placeholder.unparsed("a", pin.author())));
            if (canPin) lore.add(ColorUtil.parse("<red>Shift + ПКМ</red><gray> - открепить</gray>"));
            set(menu, GRID[i], item(Material.PAPER, ColorUtil.parse("<#B884FF>Сообщение #" + (i + 1) + "</#B884FF>"), lore),
                    canPin ? (pl, c) -> {
                        if (c != ClickType.SHIFT_RIGHT) return;
                        actions.unpin(pl, index);
                        openPins(pl, clan);
                    } : null);
        }
        if (canPin) {
            set(menu, 51, item(Material.WRITABLE_BOOK, ColorUtil.parse("<#B884FF>Закрепить сообщение</#B884FF>"),
                    lines("<white>Нажми и напиши сообщение в чат.</white>")), (pl, c) ->
                    input.ask(pl, "Напиши в чат сообщение для закрепа.", text -> {
                        actions.pin(pl, text);
                        openPins(pl, clan);
                    }));
        }
        set(menu, 49, backDoor(), (pl, c) -> openClan(pl, clan, 0));
        player.openInventory(menu.inventory);
    }

    // ---------------- роли ----------------

    public void openRoles(Player player, Clan clan) {
        Menu menu = create("Клан, настройка ролей");
        UUID me = player.getUniqueId();
        List<ClanRole> roles = clan.roles();
        int total = Perm.values().length;
        for (int i = 0; i < ROLE_SLOTS.length; i++) {
            if (i < roles.size()) {
                ClanRole role = roles.get(i);
                String count = role.leader() || role.hasOwn(Perm.ALL) ? "∞" : String.valueOf(role.count());
                List<Component> lore = new ArrayList<>();
                lore.add(ColorUtil.parse("<white>Префикс этой роли </white>").append(ClanText.rolePrefix(role)));
                lore.add(Component.empty());
                lore.add(ColorUtil.parse("<gray>Права роли:</gray>"));
                lore.add(ColorUtil.parse("<white>  У роли <green>" + count + "</green> из <red>" + total + "</red> возможных.</white>"));
                lore.add(Component.empty());
                lore.add(ColorUtil.parse("<white>Нажми чтобы изменить роль.</white>"));
                set(menu, ROLE_SLOTS[i], item(new ItemStack(role.icon()),
                        ColorUtil.parse("<#B884FF><n></#B884FF> <gray>ID: <id></gray>",
                                Placeholder.unparsed("n", role.name()), Placeholder.unparsed("id", role.id())), lore),
                        (pl, c) -> {
                            if (role.leader()) actions.msg(pl, "<white>У роли лидера все права, её нельзя изменить.</white>");
                            else if (actions.canEditRole(clan, pl.getUniqueId(), role)) openRole(pl, clan, role.id());
                            else actions.error(pl, "Эту роль менять нельзя: нужна роль старше и право «" + Perm.EDIT_ROLES.title() + "».");
                        });
            } else {
                ItemStack free = item(new ItemStack(Material.BLACK_CONCRETE), ColorUtil.parse("<aqua>Создать новую!</aqua>"),
                        lines("<white>Нажми чтобы создать.</white>"));
                free.setAmount(i + 1);
                set(menu, ROLE_SLOTS[i], free, (pl, c) -> {
                    if (!clan.has(pl.getUniqueId(), Perm.EDIT_ROLES)) {
                        actions.error(pl, "У твоей роли нет права: " + Perm.EDIT_ROLES.title() + ".");
                        return;
                    }
                    input.ask(pl, "Напиши в чат ID новой роли (латиница).", text -> {
                        ClanRole created = actions.createRole(pl, text.split("\\s+")[0]);
                        if (created != null) openRole(pl, clan, created.id());
                    });
                });
            }
        }
        set(menu, 47, item(Material.LEAD, ColorUtil.parse("<yellow>Порядок ролей!</yellow>"), lines(
                "<white>Все роли сортируются по</white>",
                "<white>алфавиту, зависит от <yellow>ID</yellow> роли.</white>",
                "",
                "<red>→ Чем выше роль тем больше</red>",
                "<red>её вес и значимость.</red>")), null);
        set(menu, 49, backDoor(), (pl, c) -> openSettings(pl, clan));
        set(menu, 51, item(Material.GOLD_BLOCK, ColorUtil.parse("<yellow>Установить начальную роль</yellow>"),
                lines("<white>При входе выдаётся: <yellow>" + escape(clan.defaultRoleId()) + "</yellow></white>",
                        "", "<gray>Нажми чтобы выбрать следующую роль.</gray>")), (pl, c) -> {
            actions.cycleDefaultRole(pl);
            openRoles(pl, clan);
        });
        player.openInventory(menu.inventory);
    }

    public void openRole(Player player, Clan clan, String roleId) {
        ClanRole role = clan.role(roleId);
        if (role == null || !actions.canEditRole(clan, player.getUniqueId(), role)) {
            openRoles(player, clan);
            return;
        }
        Menu menu = create("Клан, настройка ролей");
        set(menu, 11, item(Material.ITEM_FRAME, ColorUtil.parse("<#B884FF>Изменить префикс</#B884FF>"), List.of(
                ColorUtil.parse("<white>Сейчас префикс: </white>").append(ClanText.rolePrefix(role)))), (pl, c) ->
                input.ask(pl, "Напиши в чат новый префикс роли.", text -> {
                    actions.setRolePrefix(pl, roleId, text);
                    openRole(pl, clan, roleId);
                }));
        set(menu, 13, item(new ItemStack(role.icon()), ColorUtil.parse("<#B884FF>Изменить иконку</#B884FF>"),
                lines("<white>Нажми по предмету инветаря.</white>")), null);
        menu.ownItemClick = item -> {
            actions.setRoleIcon(player, roleId, item);
            openRole(player, clan, roleId);
        };
        set(menu, 15, item(Material.FEATHER, ColorUtil.parse("<#B884FF>Изменить название</#B884FF>"),
                lines("<white>Сейчас название: <#B884FF>" + escape(role.name()) + "</#B884FF></white>")), (pl, c) ->
                input.ask(pl, "Напиши в чат новое название роли.", text -> {
                    actions.renameRole(pl, roleId, text);
                    openRole(pl, clan, roleId);
                }));

        Perm[] perms = Perm.values();
        for (int i = 0; i < PERM_SLOTS.length; i++) {
            if (i < perms.length) {
                Perm perm = perms[i];
                boolean on = role.hasOwn(perm);
                set(menu, PERM_SLOTS[i], item(new ItemStack(on ? Material.LIME_CONCRETE : Material.RED_CONCRETE),
                        ColorUtil.parse((on ? "<green>" : "<red>") + "#" + perm.number() + ". " + perm.title()),
                        lines(on ? "<white>У этой роли есть доступ.</white>" : "<white>У этой роли нет доступа.</white>",
                                "", "<gray>Нажми чтобы переключить.</gray>")), (pl, c) -> {
                    actions.toggleRolePerm(pl, roleId, perm);
                    openRole(pl, clan, roleId);
                });
            } else {
                set(menu, PERM_SLOTS[i], item(Material.BLACK_CONCRETE, ColorUtil.parse("<dark_gray>Недоступно</dark_gray>"), List.of()), null);
            }
        }
        set(menu, 45, item(Material.LEAD, ColorUtil.parse("<#B884FF>Назад к ролям</#B884FF>"), List.of()), (pl, c) -> openRoles(pl, clan));
        set(menu, 49, backDoor(), (pl, c) -> openRoles(pl, clan));
        set(menu, 51, item(Material.STRUCTURE_VOID, ColorUtil.parse("<red>Удалить роль</red>"),
                lines("<white>Нажми чтобы удалить.</white>", "<gray>Её участники получат начальную роль.</gray>")), (pl, c) -> {
            actions.deleteRole(pl, roleId);
            openRoles(pl, clan);
        });
        player.openInventory(menu.inventory);
    }

    // ---------------- предметы ----------------

    private ItemStack backDoor() {
        return item(Material.WARPED_DOOR, ColorUtil.parse("<#B884FF>Назад</#B884FF>"), List.of());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("<", "\\<");
    }

    static List<Component> lines(String... miniMessage) {
        List<Component> out = new ArrayList<>();
        for (String s : miniMessage) out.add(s.isEmpty() ? Component.empty() : ColorUtil.parse(s));
        return out;
    }

    static ItemStack item(Material material, Component name, List<Component> lore) {
        return item(new ItemStack(material), name, lore);
    }

    static ItemStack item(ItemStack base, Component name, List<Component> lore) {
        ItemStack stack = base.clone();
        stack.setAmount(1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            applyMeta(meta, name, lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack head(ClanMember m, Component name, List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(m.uuid()));
        applyMeta(meta, name, lore);
        head.setItemMeta(meta);
        return head;
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
