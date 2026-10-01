package ru.dscraft.mediaclans;

import com.destroystokyo.paper.profile.PlayerProfile;
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
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Меню кланов - точь-в-точь как на сервере-образце (слоты, иконки и цвета сняты со скринов):
 * список кланов, клан, игрок клана, роль игрока, настройки, история, описание, закреплённые, роли, роль.
 */
public final class ClanMenus {

    /** Кланы в списке и история: 4 ряда по 7. */
    static final int[] GRID = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** Участники в меню клана: 3 ряда по 7 (21 на страницу). */
    static final int[] MEMBER_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** Роли: 3 ряда по 5. */
    static final int[] ROLE_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

    /** Права роли: 16 штук, остальные 5 - "Пусто...". */
    static final int[] PERM_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    /** Строки описания: 7 штук. */
    static final int[] DESC_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private static final String P = ClanText.P;
    private static final String DG = ClanText.DG;
    private static final String NEXT = "#79BE79";
    private static final String PREV = "#BE7979";
    private static final String CONFIRM = "#00FFE3";
    private static final String PERM_ON = "#26FF68";
    private static final String PERM_OFF = "#FF2626";
    private static final String YELLOW = "#FFF200";
    /** Голова "Клановый чат". */
    private static final String CHAT_HEAD = "39144e83e5b92249bf4299b32ae1b7a515dd34cd2a13f13572f6da59785fb74a";
    private static final Material[] FISH = {Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH};

    private final ClanManager manager;
    private final ClanActions actions;
    private final ChatInput input;

    /** Настройки списка кланов у каждого игрока: сортировка и скрытие закрытых. */
    private final Map<UUID, Boolean> sortAscending = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hideClosed = new ConcurrentHashMap<>();

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
        menu.inventory = Bukkit.createInventory(menu, 54, Component.text("▪ " + title));
        return menu;
    }

    private void set(Menu menu, int slot, ItemStack item, BiConsumer<Player, ClickType> click) {
        menu.inventory.setItem(slot, item);
        if (click != null) menu.clicks.put(slot, click);
        else menu.clicks.remove(slot);
    }

    private static Component title(String color, String text) {
        return ColorUtil.parse("<" + color + ">" + text + "</" + color + ">");
    }

    private static ItemStack fish() {
        return new ItemStack(FISH[ThreadLocalRandom.current().nextInt(FISH.length)]);
    }

    /** Нет права - кликнутый предмет превращается в рыбу "Нет прав на это!". */
    private void noPerm(Menu menu, int slot) {
        set(menu, slot, item(fish(), title("#FF3434", "Нет прав на это!"), lines("<white>Твоя роль не может делать это!</white>")), null);
    }

    /** Действие над самим собой - рыба "Действия с собой нельзя сделать!". */
    private void noSelf(Menu menu, int slot) {
        set(menu, slot, item(fish(), title("#FF3434", "Действия с собой нельзя сделать!"),
                lines("<white>Что ты хочешь там сделать...</white>")), null);
    }

    /** Клик, который выполняется только при наличии права, иначе - рыба. */
    private BiConsumer<Player, ClickType> need(Menu menu, int slot, Clan clan, Perm perm, BiConsumer<Player, ClickType> action) {
        return (pl, c) -> {
            if (clan.has(pl.getUniqueId(), perm)) action.accept(pl, c);
            else noPerm(menu, slot);
        };
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("<", "\\<");
    }

    // ---------------- список кланов ----------------

    public void openList(Player player, int page) {
        boolean asc = sortAscending.getOrDefault(player.getUniqueId(), false);
        boolean hide = hideClosed.getOrDefault(player.getUniqueId(), false);
        List<Clan> top = manager.top();
        if (asc) java.util.Collections.reverse(top);
        int closed = (int) top.stream().filter(c -> c.joinType() != Clan.JoinType.OPEN).count();
        if (hide) top.removeIf(c -> c.joinType() != Clan.JoinType.OPEN);

        int pages = Math.max(1, (top.size() + GRID.length - 1) / GRID.length);
        int p = Math.max(0, Math.min(page, pages - 1));
        Menu menu = create("Кланы, страница: " + (p + 1) + "/" + pages);

        int start = p * GRID.length;
        for (int i = 0; i < GRID.length && start + i < top.size(); i++) {
            Clan clan = top.get(start + i);
            set(menu, GRID[i], item(clan.icon(), ClanText.name(clan), ClanText.card(clan, manager, true)),
                    (pl, c) -> openClan(pl, clan, 0));
        }

        set(menu, 45, item(Material.LEAD, title(P, "Сортировка"), lines(
                "<white>Сортировка кланов.</white>",
                "",
                "<" + DG + ">Текущая:</" + DG + ">",
                asc ? "<#939393>  Больше рейтинга.</#939393>" : "<#31FBFF>✓</#31FBFF> <#939393>Больше рейтинга.</#939393>",
                asc ? "<#31FBFF>✓</#31FBFF> <#939393>Меньше рейтинга.</#939393>" : "<#939393>  Меньше рейтинга.</#939393>",
                "",
                "<white>Нажми чтобы отсортировать.</white>")), (pl, c) -> {
            sortAscending.put(pl.getUniqueId(), !asc);
            openList(pl, 0);
        });

        Clan mine = manager.getClan(player);
        if (mine != null) {
            set(menu, 49, item(Material.MANGROVE_DOOR, title(P, "Мой клан"), lines("<white>Просмотр своего клана.</white>")),
                    (pl, c) -> openClan(pl, mine, 0));
        } else {
            set(menu, 49, item(Material.ARMOR_STAND, title(P, "Создать свой клан"), lines("<white>Нажми если хочешь клан.</white>")),
                    (pl, c) -> askCreate(pl));
        }
        if (p > 0) {
            set(menu, 50, item(Material.ARROW, title(PREV, "Предыдущая"), lines("<white>Открыть " + p + " страницу...</white>")),
                    (pl, c) -> openList(pl, p - 1));
        }
        if (p < pages - 1) {
            set(menu, 51, item(Material.ARROW, title(NEXT, "Следующая"), lines("<white>Открыть " + (p + 2) + " страницу...</white>")),
                    (pl, c) -> openList(pl, p + 1));
        }
        set(menu, 53, item(Material.SLIME_BALL, title(P, hide ? "Показать закрытые кланы" : "Скрыть закрытые кланы"),
                lines(hide ? "<white>Скрыто <" + CONFIRM + ">" + closed + "</" + CONFIRM + "> закрытых кланов.</white>"
                        : "<white>Отображено <" + CONFIRM + ">" + closed + "</" + CONFIRM + "> закрытых кланов.</white>")), (pl, c) -> {
            hideClosed.put(pl.getUniqueId(), !hide);
            openList(pl, 0);
        });
        player.openInventory(menu.inventory);
    }

    public void askCreate(Player player) {
        if (!actions.checkCanCreate(player)) {
            player.closeInventory();
            return;
        }
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
        int maxSlots = Math.max(actions.settings().maxSlots(), members.size());
        int pages = Math.max(1, (maxSlots + MEMBER_SLOTS.length - 1) / MEMBER_SLOTS.length);
        int p = Math.max(0, Math.min(page, pages - 1));
        Menu menu = create("Клан, игроки: " + members.size());
        UUID me = player.getUniqueId();
        boolean inClan = clan.member(me) != null;

        set(menu, 11, item(Material.ITEM_FRAME, title(P, "Информация о клане"), ClanText.info(clan, manager)), null);
        set(menu, 13, head(CHAT_HEAD, title(P, "Клановый чат"), lines(
                "<white>Поставь знак " + esc(actions.settings().chatSymbol()) + " в начале сообщения.</white>")), null);
        set(menu, 15, item(Material.LECTERN, title(P, "Закреплённые сообщения"),
                lines("<white>У клана <" + P + ">" + clan.pins().size() + "</" + P + "> сообщений.</white>")), (pl, c) -> {
            if (clan.member(pl.getUniqueId()) != null) openPins(pl, clan);
        });

        // ---- участники и слоты ----
        boolean canBuy = inClan && clan.has(me, Perm.BUY_SLOTS);
        int start = p * MEMBER_SLOTS.length;
        for (int i = 0; i < MEMBER_SLOTS.length; i++) {
            int idx = start + i;
            int slot = MEMBER_SLOTS[i];
            if (idx < members.size()) {
                ClanMember m = members.get(idx);
                ClanRole role = clan.roleOf(m);
                ItemStack icon = new ItemStack(role == null ? Material.RABBIT_HIDE : role.icon());
                set(menu, slot, item(icon, ClanText.memberTitle(clan, m), ClanText.member(clan, m, inClan)),
                        inClan ? (pl, c) -> openMember(pl, clan, m.uuid()) : null);
            } else if (idx < clan.slots()) {
                if (inClan && clan.has(me, Perm.INVITE)) {
                    set(menu, slot, item(Material.BLACK_CONCRETE, title(P, "Пригласить игрока!"),
                            lines("<white>Нажми чтобы отправить запрос.</white>")), need(menu, slot, clan, Perm.INVITE,
                            (pl, c) -> actions.invitePrompt(pl)));
                } else {
                    set(menu, slot, item(Material.GRAY_STAINED_GLASS_PANE, title("#939393", "Пустой слот"),
                            lines("<white>Тут никого нет.</white>")), null);
                }
            } else if (idx < actions.settings().maxSlots()) {
                if (canBuy) {
                    set(menu, slot, item(Material.GOLD_BLOCK, title("#FFE822", "Купить слот!"),
                            lines("<white>Купить слот за <" + YELLOW + ">" + actions.settings().slotPrice() + "</" + YELLOW + "> коинов.</white>")),
                            need(menu, slot, clan, Perm.BUY_SLOTS, (pl, c) -> {
                                actions.buySlot(pl);
                                openClan(pl, clan, p);
                            }));
                } else {
                    set(menu, slot, item(Material.BARRIER, title("#FF3434", "Слот заблокирован"),
                            lines("<white>У вас нет прав на покупку.</white>")), null);
                }
            }
        }

        // ---- нижний ряд ----
        if (inClan) {
            set(menu, 46, item(Material.REPEATER, title(P, "Опции"), lines("<white>Нажми чтобы настроить клан.</white>")),
                    (pl, c) -> openSettings(pl, clan));
        } else if (manager.getClan(player) == null) {
            set(menu, 47, item(Material.SPRUCE_DOOR, title(P, "Вступить в клан"), lines("<white>Нажми для вступления.</white>")),
                    (pl, c) -> {
                        ClanManager.Invite invite = manager.getInvite(pl.getUniqueId());
                        boolean invited = invite != null && invite.clanId().equals(clan.id());
                        if (!invited && clan.joinType() == Clan.JoinType.PASSWORD) {
                            input.ask(pl, "Напиши в чат пароль клана.", text -> {
                                actions.joinWithPassword(pl, clan, text);
                                if (clan.member(pl.getUniqueId()) != null) openClan(pl, clan, 0);
                            });
                            return;
                        }
                        actions.join(pl, clan);
                        if (clan.member(pl.getUniqueId()) != null) openClan(pl, clan, 0);
                    });
        }
        set(menu, 49, item(Material.BELL, title(P, "На главную страницу"), lines("<white>Открыть все кланы сервера.</white>")),
                (pl, c) -> openList(pl, 0));
        if (p > 0) {
            set(menu, 50, item(Material.ARROW, title(PREV, "Предыдущая"), lines("<white>Открыть " + p + " страницу...</white>")),
                    (pl, c) -> openClan(pl, clan, p - 1));
        }
        if (p < pages - 1) {
            set(menu, 51, item(Material.ARROW, title(NEXT, "Следующая"), lines("<white>Открыть " + (p + 2) + " страницу...</white>")),
                    (pl, c) -> openClan(pl, clan, p + 1));
        }
        if (me.equals(clan.owner())) {
            set(menu, 53, item(Material.STRUCTURE_VOID, title("#FF4920", "Удалить клан"), lines("<white>Удалить этот клан.</white>")),
                    (pl, c) -> set(menu, 53, item(Material.STRING, title(CONFIRM, "Подтверди удаление"),
                            lines("<white>Ты точно хочешь удалить клан?</white>")), (pl2, c2) -> {
                        pl2.closeInventory();
                        actions.disband(pl2);
                    }));
        } else if (inClan) {
            set(menu, 47, item(Material.LEATHER, title("#FF3434", "Покинуть клан"), lines("<white>Покинуть этот клан.</white>")),
                    (pl, c) -> set(menu, 47, item(Material.STRING, title(CONFIRM, "Подтверди выход"),
                            lines("<white>Ты точно хочешь выйти из клана?</white>")), (pl2, c2) -> {
                        pl2.closeInventory();
                        actions.leave(pl2);
                    }));
        }
        player.openInventory(menu.inventory);
    }

    // ---------------- игрок клана ----------------

    public void openMember(Player player, Clan clan, UUID targetId) {
        ClanMember target = clan.member(targetId);
        if (target == null || clan.member(player.getUniqueId()) == null) {
            openClan(player, clan, 0);
            return;
        }
        Menu menu = create("Клан, игрок: " + target.name());
        UUID me = player.getUniqueId();

        boolean self = me.equals(targetId);
        set(menu, 11, item(Material.LEATHER, title(P, "Телепорт к игроку"), lines("<white>Нажми чтобы телепортироваться.</white>")),
                (pl, c) -> {
                    if (self) noSelf(menu, 11);
                    else actions.teleport(pl, targetId);
                });
        if (clan.has(me, Perm.SET_ROLE)) {
            set(menu, 13, item(Material.ANVIL, title(P, "Роль игрока"), lines("<white>Нажми чтобы изменить роль.</white>")), (pl, c) -> {
                if (self) noSelf(menu, 13);
                else if (!clan.has(pl.getUniqueId(), Perm.SET_ROLE) || !clan.canManage(pl.getUniqueId(), clan.member(targetId))) noPerm(menu, 13);
                else openRolePick(pl, clan, targetId);
            });
        }
        ClanMember mine = clan.member(me);
        boolean notify = mine == null || mine.notifyJoins();
        set(menu, 15, notifyItem(notify), (pl, c) -> {
            boolean now = actions.toggleNotify(pl);
            menu.inventory.setItem(15, notifyItem(now));
        });
        if (clan.has(me, Perm.KICK)) {
            set(menu, 31, item(Material.BARRIER, title(P, "Кикнуть игрока"), lines("<white>Выгнать этого игрока из клана.</white>")),
                    (pl, c) -> {
                        if (self) {
                            noSelf(menu, 31);
                            return;
                        }
                        if (!clan.has(pl.getUniqueId(), Perm.KICK) || !clan.canManage(pl.getUniqueId(), clan.member(targetId))) {
                            noPerm(menu, 31);
                            return;
                        }
                        actions.kick(pl, targetId);
                        if (clan.member(targetId) == null) openClan(pl, clan, 0);
                    });
        }
        set(menu, 49, backDoor(), (pl, c) -> openClan(pl, clan, 0));
        player.openInventory(menu.inventory);
    }

    private ItemStack notifyItem(boolean on) {
        return item(on ? Material.LIME_CANDLE : Material.RED_CANDLE, title(P, "Сообщения выхода и входа на сервер"),
                lines(on ? "<white>Ты включил для себя сообщения.</white>" : "<white>Ты отключил для себя сообщения.</white>"));
    }

    private void openRolePick(Player player, Clan clan, UUID targetId) {
        ClanMember target = clan.member(targetId);
        if (target == null) {
            openClan(player, clan, 0);
            return;
        }
        Menu menu = create("Роль игрока");
        List<ClanRole> roles = clan.roles();
        ClanRole current = clan.roleOf(target);
        for (int i = 0; i < ROLE_SLOTS.length; i++) {
            if (i < roles.size()) {
                ClanRole role = roles.get(i);
                ItemStack icon = new ItemStack(role == current ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
                ItemStack it = item(icon, roleTitle(role), lines("<white>Дать игроку эту роль.</white>"));
                it.setAmount(i + 1);
                int slot = ROLE_SLOTS[i];
                set(menu, slot, it, (pl, c) -> {
                    ClanRole mine = clan.roleOf(clan.member(pl.getUniqueId()));
                    boolean ok = clan.has(pl.getUniqueId(), Perm.SET_ROLE) && clan.canManage(pl.getUniqueId(), clan.member(targetId))
                            && !role.leader() && (pl.getUniqueId().equals(clan.owner()) || (mine != null && mine.above(role)));
                    if (!ok) {
                        noPerm(menu, slot);
                        return;
                    }
                    actions.setRole(pl, targetId, role.id());
                    openRolePick(pl, clan, targetId);
                });
            } else {
                ItemStack empty = item(Material.BLACK_CONCRETE, Component.text(" "), List.of());
                empty.setAmount(i + 1);
                set(menu, ROLE_SLOTS[i], empty, null);
            }
        }
        if (player.getUniqueId().equals(clan.owner()) && !targetId.equals(clan.owner())) {
            set(menu, 47, item(Material.NETHER_STAR, title(P, "Сделать игрока создателем"),
                    lines("<white>Передать игроку право на клан.</white>")), (pl, c) ->
                    set(menu, 47, item(Material.STRING, title(CONFIRM, "Подтверди передачу"),
                            lines("<white>Сделать игрока владельцем?</white>")), (pl2, c2) -> {
                        actions.transfer(pl2, targetId);
                        openClan(pl2, clan, 0);
                    }));
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

        set(menu, 11, item(Material.ANVIL, title(P, "Настройка ролей"),
                lines("<white>У клана <" + P + ">" + clan.roles().size() + "</" + P + "> ролей.</white>")), need(menu, 11, clan, Perm.EDIT_ROLES,
                (pl, c) -> openRoles(pl, clan)));
        set(menu, 13, item(clan.icon(), title(P, "Изменить иконку"), lines("<white>Нажми по предмету инветаря.</white>")), null);
        menu.ownItemClick = item -> {
            if (!clan.has(player.getUniqueId(), Perm.ICON)) {
                noPerm(menu, 13);
                return;
            }
            actions.setIcon(player, item);
            openSettings(player, clan);
        };
        set(menu, 15, joinItem(clan), need(menu, 15, clan, Perm.JOIN_TYPE, (pl, c) -> {
            actions.cycleJoinType(pl);
            openSettings(pl, clan);
        }));

        ItemStack sword = item(new ItemStack(clan.pvp() ? Material.GOLDEN_SWORD : Material.WOODEN_SWORD), title(P, "PvP клана"), lines(
                "<white>Огонь по своим: " + (clan.pvp() ? "<#3DFF66>Включен</#3DFF66>" : "<" + ClanText.RED + ">Выключен</" + ClanText.RED + ">") + "</white>",
                clan.pvp() ? "<white>Нажми чтобы выключить.</white>" : "<white>Нажми чтобы включить.</white>",
                ""), true);
        set(menu, 20, sword, need(menu, 20, clan, Perm.PVP, (pl, c) -> {
            actions.togglePvp(pl);
            openSettings(pl, clan);
        }));
        set(menu, 22, item(Material.PAPER, title(P, "История клана"), lines("<white>Логи входов, выходов и киков.</white>")),
                need(menu, 22, clan, Perm.HISTORY, (pl, c) -> openHistory(pl, clan)));
        boolean see = clan.has(me, Perm.VIEW_PASSWORD) || clan.has(me, Perm.PASSWORD);
        String pw = clan.password() == null ? "нет" : clan.password();
        set(menu, 24, item(Material.SKULL_BANNER_PATTERN, title(P, "Пароль клана"), lines(
                see ? "<white>У клана <" + P + ">" + esc(pw) + "</" + P + "> пароль,</white>" : "<white>Пароль клана скрыт,</white>",
                "<white>нажми чтобы изменить.</white>")), need(menu, 24, clan, Perm.PASSWORD, (pl, c) ->
                input.ask(pl, "Напиши в чат пароль для клана.", text -> {
                    actions.setPassword(pl, text);
                    openSettings(pl, clan);
                })));
        set(menu, 29, item(Material.NAME_TAG, title(P, "Изменить название"), lines("<white>Нажми чтобы поменять название.</white>")), need(menu, 29, clan, Perm.RENAME, (pl, c) ->
                input.ask(pl, "Напиши в чат название для клана.", text -> actions.rename(pl, text.split("\\s+")[0]))));
        set(menu, 31, item(Material.KNOWLEDGE_BOOK, title(P, "Изменить описание"), lines("<white>Нажми чтобы поменять описание.</white>")),
                need(menu, 31, clan, Perm.DESCRIPTION, (pl, c) -> openDescription(pl, clan)));
        List<Component> ann = new ArrayList<>();
        ann.add(clan.announcement() == null ? ColorUtil.parse("<#CDCDCD>Объявлений ещё не было</#CDCDCD>")
                : ColorUtil.parse("<#CDCDCD><text></#CDCDCD>", Placeholder.unparsed("text", clan.announcement())));
        ann.add(ColorUtil.parse("<white>Написать всем игрокам онлайн.</white>"));
        set(menu, 33, item(Material.MAP, title(P, "Объявление для клана"), ann), need(menu, 33, clan, Perm.ANNOUNCE, (pl, c) ->
                input.ask(pl, "Напиши в чат текст объявления для клана.", text -> actions.announce(pl, text))));
        set(menu, 49, backDoor(), (pl, c) -> openClan(pl, clan, 0));
        player.openInventory(menu.inventory);
    }

    private ItemStack joinItem(Clan clan) {
        return switch (clan.joinType()) {
            case PASSWORD -> item(Material.MAP, title(ClanText.RED, "Клан под паролем"),
                    lines("<white>Нажми чтобы включить <u>открыть</u>.</white>"));
            case OPEN -> item(Material.ENDER_EYE, title(PERM_ON, "Клан открыт"),
                    lines("<white>Нажми чтобы <u>прикрыть клан</u>.</white>"));
            default -> item(Material.ENDER_EYE, title(ClanText.RED, "Вход по приглашению"),
                    lines("<white>Нажми чтобы включить <u>вход по паролю</u>.</white>"));
        };
    }

    // ---------------- описание ----------------

    public void openDescription(Player player, Clan clan) {
        Menu menu = create("Описание клана");
        String[] desc = ClanText.descLines(clan);
        List<Component> preview = new ArrayList<>();
        boolean any = false;
        for (String line : desc) {
            if (line.isBlank()) continue;
            any = true;
            preview.add(Component.empty().color(net.kyori.adventure.text.format.NamedTextColor.WHITE).append(ColorUtil.rich(line)));
        }
        if (!any) preview.add(ColorUtil.parse("<gray>Нет описания...</gray>"));
        set(menu, 13, item(Material.BEACON, ColorUtil.parse("<white>Описание клана:</white>"), preview), null);
        for (int i = 0; i < DESC_SLOTS.length; i++) {
            int index = i;
            boolean filled = !desc[i].isBlank();
            ItemStack it = filled
                    ? item(Material.LIME_CONCRETE, Component.empty().color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                    .append(ColorUtil.rich(desc[i])), List.of())
                    : item(Material.BLACK_CONCRETE, ColorUtil.parse("<white>Пусто...</white>"), List.of());
            it.setAmount(i + 1);
            set(menu, DESC_SLOTS[i], it, (pl, c) -> {
                if (filled && c.isShiftClick()) {
                    actions.setDescriptionLine(pl, index, "");
                    openDescription(pl, clan);
                    return;
                }
                input.ask(pl, "Напиши в чат строку для описания.", text -> {
                    actions.setDescriptionLine(pl, index, text);
                    openDescription(pl, clan);
                });
            });
        }
        set(menu, 49, backDoor(), (pl, c) -> openSettings(pl, clan));
        player.openInventory(menu.inventory);
    }

    // ---------------- история ----------------

    public void openHistory(Player player, Clan clan) {
        Menu menu = create("История клана");
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < 54; i++) menu.inventory.setItem(i, pane);
        for (int s : GRID) menu.inventory.setItem(s, null);
        List<HistoryEntry> history = new ArrayList<>(clan.history());
        java.util.Collections.reverse(history);
        for (int i = 0; i < GRID.length && i < history.size(); i++) {
            HistoryEntry h = history.get(i);
            String a = esc(h.actor() == null ? "?" : h.actor());
            String t = h.target() == null ? "?" : h.target();
            String v = "<" + P + ">";
            String ve = "</" + P + ">";
            Material icon;
            String head;
            List<String> body = new ArrayList<>();
            switch (h.type()) {
                case CREATE -> {
                    icon = Material.NETHER_STAR;
                    head = "<" + P + ">Создание клана</" + P + ">";
                    body.add("<white>Создатель: " + v + a + ve + "</white>");
                }
                case JOIN -> {
                    icon = Material.SLIME_BALL;
                    head = "<" + PERM_ON + ">Вступление</" + PERM_ON + ">";
                    body.add("<white>Игрок: " + v + a + ve + "</white>");
                }
                case LEAVE -> {
                    icon = Material.RED_DYE;
                    head = "<" + ClanText.RED + ">Выход из клана</" + ClanText.RED + ">";
                    body.add("<white>Игрок: " + v + a + ve + "</white>");
                }
                case KICK -> {
                    icon = Material.BARRIER;
                    head = "<" + ClanText.RED + ">Исключение</" + ClanText.RED + ">";
                    body.add("<white>Игрок: " + v + esc(t) + ve + "</white>");
                    body.add("<white>Кто выгнал: " + v + a + ve + "</white>");
                }
                case ROLE -> {
                    icon = Material.NAME_TAG;
                    head = "<#479CFF>Смена роли</#479CFF>";
                    String[] parts = t.split("\\|", 2);
                    body.add("<white>Игрок: " + v + esc(parts[0]) + ve + "</white>");
                    body.add("<white>Кто выдал: " + v + a + ve + "</white>");
                    body.add("<white>Новая роль: " + v + esc(parts.length > 1 ? parts[1] : "?") + ve + "</white>");
                }
                default -> {
                    icon = Material.GOLD_BLOCK;
                    head = "<" + YELLOW + ">Смена владельца</" + YELLOW + ">";
                    body.add("<white>Новый владелец: " + v + esc(t) + ve + "</white>");
                    body.add("<white>Кто передал: " + v + a + ve + "</white>");
                }
            }
            body.add("");
            body.add("<gray>Дата: " + ClanText.date(h.time()) + "</gray>");
            set(menu, GRID[i], item(new ItemStack(icon), ColorUtil.parse(head), lines(body.toArray(new String[0]))), null);
        }
        set(menu, 49, backDoor(), (pl, c) -> openSettings(pl, clan));
        player.openInventory(menu.inventory);
    }

    // ---------------- закреплённые сообщения ----------------

    public void openPins(Player player, Clan clan) {
        Menu menu = create("Закреплённые сообщения");
        boolean canPin = clan.has(player.getUniqueId(), Perm.ANNOUNCE);
        List<Pin> pins = new ArrayList<>(clan.pins());
        for (int i = 0; i < GRID.length && i < pins.size(); i++) {
            Pin pin = pins.get(i);
            int index = i;
            List<Component> lore = new ArrayList<>();
            for (String line : ClanText.wrap(pin.text(), 36)) {
                lore.add(ColorUtil.parse("<white><l></white>", Placeholder.unparsed("l", line)));
            }
            lore.add(Component.empty());
            lore.add(ColorUtil.parse("<gray>Закрепил <" + P + "><a></" + P + ">, " + ClanText.date(pin.time()) + "</gray>",
                    Placeholder.unparsed("a", pin.author())));
            if (canPin) lore.add(ColorUtil.parse("<white>Shift + ПКМ чтобы открепить.</white>"));
            set(menu, GRID[i], item(Material.PAPER, title(P, "Сообщение #" + (i + 1)), lore),
                    canPin ? (pl, c) -> {
                        if (c != ClickType.SHIFT_RIGHT) return;
                        actions.unpin(pl, index);
                        openPins(pl, clan);
                    } : null);
        }
        if (canPin) {
            set(menu, 51, item(Material.WRITABLE_BOOK, title(P, "Закрепить сообщение"),
                    lines("<white>Нажми чтобы закрепить сообщение.</white>")), (pl, c) ->
                    input.ask(pl, "Напиши в чат сообщение для закрепа.", text -> {
                        actions.pin(pl, text);
                        openPins(pl, clan);
                    }));
        }
        set(menu, 49, backDoor(), (pl, c) -> openClan(pl, clan, 0));
        player.openInventory(menu.inventory);
    }

    // ---------------- роли ----------------

    private static Component roleTitle(ClanRole role) {
        return ColorUtil.parse("<" + P + "><n></" + P + "> <" + DG + ">ID: <id></" + DG + ">",
                Placeholder.component("n", ColorUtil.rich(role.name())), Placeholder.unparsed("id", role.id()));
    }

    public void openRoles(Player player, Clan clan) {
        Menu menu = create("Клан, настройка ролей");
        List<ClanRole> roles = clan.roles();
        int total = Perm.values().length;
        for (int i = 0; i < ROLE_SLOTS.length; i++) {
            if (i < roles.size()) {
                ClanRole role = roles.get(i);
                String count = role.leader() || role.hasOwn(Perm.ALL) ? "∞" : String.valueOf(role.count());
                List<Component> lore = new ArrayList<>();
                lore.add(ColorUtil.parse("<white>Префикс этой роли </white>").append(ClanText.rolePrefix(role)));
                lore.add(Component.empty());
                lore.add(ColorUtil.parse("<" + DG + ">Права роли:</" + DG + ">"));
                lore.add(ColorUtil.parse("<white>  У роли <" + PERM_ON + ">" + count + "</" + PERM_ON + "> из <" + PERM_OFF + ">" + total
                        + "</" + PERM_OFF + "> возможных.</white>"));
                lore.add(Component.empty());
                lore.add(ColorUtil.parse("<white>Нажми чтобы изменить роль.</white>"));
                int slot = ROLE_SLOTS[i];
                set(menu, slot, item(new ItemStack(role.icon()), roleTitle(role), lore), (pl, c) -> {
                    if (actions.canEditRole(clan, pl.getUniqueId(), role)) openRole(pl, clan, role.id());
                    else noPerm(menu, slot);
                });
            } else {
                ItemStack free = item(Material.BLACK_CONCRETE, title("#00FFFF", "Создать новую!"), lines("<white>Нажми чтобы создать.</white>"));
                free.setAmount(i + 1);
                set(menu, ROLE_SLOTS[i], free, need(menu, ROLE_SLOTS[i], clan, Perm.EDIT_ROLES, (pl, c) -> {
                    actions.createRole(pl);
                    openRoles(pl, clan);
                }));
            }
        }
        set(menu, 47, item(Material.LEAD, title(YELLOW, "Порядок ролей!"), lines(
                "<white>Все роли сортируются по</white>",
                "<white>алфавиту, зависит от <" + YELLOW + ">ID</" + YELLOW + "> роли.</white>",
                "",
                "<#FF3434>→ Чем выше роль тем больше</#FF3434>",
                "<#FF3434>её вес и значимость.</#FF3434>")), null);
        set(menu, 49, backDoor(), (pl, c) -> openSettings(pl, clan));
        set(menu, 51, item(Material.GOLD_BLOCK, title(YELLOW, "Установить начальную роль"),
                lines("<white>При входе выдаёся: <" + YELLOW + ">" + esc(clan.defaultRoleId()) + "</" + YELLOW + "></white>")), need(menu, 51, clan, Perm.EDIT_ROLES, (pl, c) -> {
            actions.cycleDefaultRole(pl);
            openRoles(pl, clan);
        }));
        player.openInventory(menu.inventory);
    }

    public void openRole(Player player, Clan clan, String roleId) {
        ClanRole role = clan.role(roleId);
        if (role == null || !actions.canEditRole(clan, player.getUniqueId(), role)) {
            openRoles(player, clan);
            return;
        }
        Menu menu = create("Клан, настройка ролей");
        set(menu, 11, item(Material.ITEM_FRAME, title(P, "Изменить префикс"), List.of(
                ColorUtil.parse("<white>Сейчас префикс: </white>").append(ClanText.rolePrefix(role)))), (pl, c) ->
                input.ask(pl, "Напиши в чат префикс для роли.", text -> {
                    actions.setRolePrefix(pl, roleId, text);
                    openRole(pl, clan, roleId);
                }));
        set(menu, 13, item(new ItemStack(role.icon()), title(P, "Изменить иконку"), lines("<white>Нажми по предмету инветаря.</white>")), null);
        menu.ownItemClick = item -> {
            actions.setRoleIcon(player, roleId, item);
            openRole(player, clan, roleId);
        };
        set(menu, 15, item(Material.NAME_TAG, title(P, "Изменить название"), List.of(
                ColorUtil.parse("<white>Сейчас название: </white>").append(ColorUtil.parse("<" + P + "><n></" + P + ">",
                        Placeholder.component("n", ColorUtil.rich(role.name())))))), (pl, c) ->
                input.ask(pl, "Напиши в чат название для роли.", text -> {
                    actions.renameRole(pl, roleId, text);
                    openRole(pl, clan, roleId);
                }));

        if (role.leader()) {
            set(menu, 31, item(Material.CHAINMAIL_LEGGINGS, title(PERM_OFF, "Нельзя поменять права..."), List.of()), null);
        } else {
            Perm[] perms = Perm.values();
            for (int i = 0; i < PERM_SLOTS.length; i++) {
                if (i < perms.length) {
                    Perm perm = perms[i];
                    boolean on = role.hasOwn(perm);
                    set(menu, PERM_SLOTS[i], item(new ItemStack(on ? Material.LIME_CONCRETE : Material.RED_CONCRETE),
                            title(on ? PERM_ON : PERM_OFF, "#" + perm.number() + ". " + perm.title()),
                            lines(on ? "<white>У этой роли есть доступ.</white>" : "<white>У этой роли нет доступа.</white>")), (pl, c) -> {
                        actions.toggleRolePerm(pl, roleId, perm);
                        openRole(pl, clan, roleId);
                    });
                } else {
                    set(menu, PERM_SLOTS[i], item(Material.BLACK_CONCRETE, ColorUtil.parse("<white>Пусто...</white>"), List.of()), null);
                }
            }
        }

        set(menu, 45, item(Material.LEAD, title(P, "Изменить ID"),
                lines("<white>Сейчас айди: <" + P + ">" + esc(role.id()) + "</" + P + "></white>")), (pl, c) -> {
            if (role.leader()) {
                set(menu, 45, item(fish(), title(PERM_OFF, "Этой роли нельзя менять!"),
                        lines("<white>Айди этой роли нельзя изменить.</white>")), null);
                return;
            }
            input.ask(pl, "Напиши в чат новый ID для роли.", text -> {
                String newId = text.split("\\s+")[0];
                if (actions.changeRoleId(pl, roleId, newId)) openRole(pl, clan, newId.toLowerCase(java.util.Locale.ROOT));
                else openRole(pl, clan, roleId);
            });
        });
        set(menu, 49, backDoor(), (pl, c) -> openRoles(pl, clan));
        set(menu, 53, item(Material.STRUCTURE_VOID, title(P, "Удалить роль"), lines("<white>Нажми чтобы удалить роль.</white>")), (pl, c) -> {
            if (role.leader()) {
                set(menu, 53, item(fish(), title("#FF3434", "Эта роль главная!"), lines("<white>А значит нельзя удалить.</white>")), null);
            } else if (role.id().equals(clan.defaultRoleId())) {
                set(menu, 53, item(fish(), title("#FF3434", "Это стандартная роль!"), lines("<white>Она выдаётся новым игрокам.</white>")), null);
            } else {
                set(menu, 53, item(Material.STRING, title(CONFIRM, "Подтверди удаление"), lines("<white>Хочешь удалить эту роль?</white>")),
                        (pl2, c2) -> {
                            actions.deleteRole(pl2, roleId);
                            openRoles(pl2, clan);
                        });
            }
        });
        player.openInventory(menu.inventory);
    }

    // ---------------- предметы ----------------

    private ItemStack backDoor() {
        return item(Material.WARPED_DOOR, title(P, "Перейти назад"), lines("<white>Нажми чтобы перейти назад.</white>"));
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
        return item(base, name, lore, false);
    }

    /** showAttributes - оставить "Когда в ведущей руке: урон..." (как у меча "PvP клана"). */
    static ItemStack item(ItemStack base, Component name, List<Component> lore, boolean showAttributes) {
        ItemStack stack = base.clone();
        stack.setAmount(1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            applyMeta(meta, name, lore);
            if (showAttributes) meta.removeItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Голова с текстурой textures.minecraft.net/texture/<hash>. */
    private static ItemStack head(String texture, Component name, List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes()), "clanhead");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL("http://textures.minecraft.net/texture/" + texture));
            profile.setTextures(textures);
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
        }
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
