package ru.dscraft.destroychat.clan;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import ru.dscraft.destroychat.util.ColorUtil;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кланы: клики в меню, ввод текста в чат (звание, описание, ник для приглашения),
 * клановый рейтинг за убийства.
 */
public class ClanListener implements Listener {

    private enum InputType { CREATE, DESCRIPTION, INVITE, RANK }

    private record PendingInput(InputType type, String clanId, UUID target, long expiresAt) {
    }

    private static final long INPUT_TIMEOUT_MS = 60_000L;

    private final Plugin plugin;
    private final ClanActions actions;
    private final ClanManager manager;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public ClanListener(Plugin plugin, ClanActions actions) {
        this.plugin = plugin;
        this.actions = actions;
        this.manager = actions.manager();
    }

    // ---------------- меню ----------------

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ClanMenus.Menu) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClanMenus.Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getRawSlot();
        ClickType click = event.getClick();

        if (menu instanceof ClanMenus.ListMenu list) {
            clickList(player, list, slot);
        } else if (menu instanceof ClanMenus.ClanMenu clanMenu) {
            clickClan(player, clanMenu, slot, click);
        } else if (menu instanceof ClanMenus.MemberMenu memberMenu) {
            clickMember(player, memberMenu, slot, click);
        }
    }

    private void clickList(Player player, ClanMenus.ListMenu menu, int slot) {
        String clanId = menu.clanSlots.get(slot);
        if (clanId != null) {
            Clan clan = manager.getById(clanId);
            if (clan != null) ClanMenus.openClan(player, manager, clan, 0);
            return;
        }
        switch (slot) {
            case ClanMenus.SLOT_PREV -> ClanMenus.openList(player, manager, menu.page - 1);
            case ClanMenus.SLOT_NEXT -> ClanMenus.openList(player, manager, menu.page + 1);
            case ClanMenus.SLOT_CLOSE -> player.closeInventory();
            case ClanMenus.SLOT_BACK -> {
                player.closeInventory();
                ClanText.sendTop(player, manager, actions.config(), 1);
            }
            case ClanMenus.SLOT_MY_CLAN -> {
                Clan mine = manager.getClan(player);
                if (mine != null) ClanMenus.openClan(player, manager, mine, 0);
                else askCreate(player);
            }
            default -> {
            }
        }
    }

    private void clickClan(Player player, ClanMenus.ClanMenu menu, int slot, ClickType click) {
        Clan clan = manager.getById(menu.clanId);
        if (clan == null) {
            player.closeInventory();
            actions.error(player, "Этого клана больше нет.");
            return;
        }
        UUID memberId = menu.memberSlots.get(slot);
        if (memberId != null) {
            ClanMember me = clan.member(player.getUniqueId());
            ClanMember target = clan.member(memberId);
            if (target == null) return;
            boolean self = me != null && me.uuid().equals(memberId) && me.role().canManageClan();
            if (ClanActions.canManage(me, target) || self) ClanMenus.openMember(player, clan, target);
            return;
        }

        boolean inThisClan = clan.member(player.getUniqueId()) != null;
        switch (slot) {
            case ClanMenus.SLOT_DESC -> {
                if (!inThisClan) return;
                if (click.isRightClick()) {
                    actions.setDescription(player, null);
                    reopen(player, clan, menu.page);
                } else {
                    askInput(player, InputType.DESCRIPTION, clan, null,
                            "<gray>Напиши новое описание клана в чат. <white>отмена</white> - отменить.</gray>");
                }
            }
            case ClanMenus.SLOT_TYPE -> {
                if (!inThisClan) return;
                actions.toggleType(player);
                reopen(player, clan, menu.page);
            }
            case ClanMenus.SLOT_INVITE -> {
                if (!inThisClan) return;
                askInput(player, InputType.INVITE, clan, null,
                        "<gray>Напиши в чат ник игрока, которого хочешь пригласить. <white>отмена</white> - отменить.</gray>");
            }
            case ClanMenus.SLOT_ICON -> {
                if (!inThisClan) return;
                actions.setIcon(player);
                reopen(player, clan, menu.page);
            }
            case ClanMenus.SLOT_MEMBERSHIP -> {
                if (inThisClan) {
                    if (click != ClickType.SHIFT_RIGHT) return;
                    player.closeInventory();
                    if (clan.owner().equals(player.getUniqueId())) actions.disband(player);
                    else actions.leave(player);
                } else {
                    actions.join(player, clan, false);
                    if (clan.member(player.getUniqueId()) != null) reopen(player, clan, menu.page);
                }
            }
            case ClanMenus.SLOT_TOP, ClanMenus.SLOT_BACK -> ClanMenus.openList(player, manager, 0);
            case ClanMenus.SLOT_PREV -> ClanMenus.openClan(player, manager, clan, menu.page - 1);
            case ClanMenus.SLOT_NEXT -> ClanMenus.openClan(player, manager, clan, menu.page + 1);
            case ClanMenus.SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void clickMember(Player player, ClanMenus.MemberMenu menu, int slot, ClickType click) {
        Clan clan = manager.getById(menu.clanId);
        ClanMember target = clan == null ? null : clan.member(menu.target);
        if (target == null) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case ClanMenus.M_RANK -> {
                if (click.isRightClick()) {
                    actions.setRank(player, target.uuid(), null);
                    ClanMenus.openMember(player, clan, target);
                } else {
                    askInput(player, InputType.RANK, clan, target.uuid(),
                            "<gray>Напиши в чат звание для <white><name></white>. Можно с цветами. <white>отмена</white> - отменить.</gray>",
                            target.name());
                }
            }
            case ClanMenus.M_ADMIN -> {
                if (clan.owner().equals(target.uuid())) return;
                actions.toggleAdmin(player, target.uuid());
                ClanMenus.openMember(player, clan, target);
            }
            case ClanMenus.M_KICK -> {
                if (click != ClickType.SHIFT_RIGHT) return;
                actions.kick(player, target.uuid());
                ClanMenus.openClan(player, manager, clan, 0);
            }
            case ClanMenus.M_OWNER -> {
                if (click != ClickType.SHIFT_RIGHT) return;
                actions.transfer(player, target.uuid());
                ClanMenus.openClan(player, manager, clan, 0);
            }
            case ClanMenus.M_BACK -> ClanMenus.openClan(player, manager, clan, 0);
            default -> {
            }
        }
    }

    private void reopen(Player player, Clan clan, int page) {
        if (manager.getById(clan.id()) == null) {
            player.closeInventory();
            return;
        }
        ClanMenus.openClan(player, manager, clan, page);
    }

    // ---------------- ввод в чат ----------------

    /** "Создать клан": надпись на экране и ожидание названия в чате. */
    private void askCreate(Player player) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new PendingInput(InputType.CREATE, null, null,
                System.currentTimeMillis() + INPUT_TIMEOUT_MS));
        player.showTitle(Title.title(
                ColorUtil.rich(actions.config().clanCreateTitle()),
                ColorUtil.rich(actions.config().clanCreateSubtitle()),
                Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(INPUT_TIMEOUT_MS), Duration.ofMillis(500))));
    }

    private void askInput(Player player, InputType type, Clan clan, UUID target, String prompt, String... name) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new PendingInput(type, clan.id(), target,
                System.currentTimeMillis() + INPUT_TIMEOUT_MS));
        actions.msg(player, prompt, Placeholder.unparsed("name", name.length > 0 ? name[0] : ""));
    }

    /** Раньше всех остальных обработчиков чата: сообщение-ответ в чат не попадает. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInput(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingInput input = pending.remove(player.getUniqueId());
        if (input == null || input.expiresAt() < System.currentTimeMillis()) return;
        event.setCancelled(true);

        String text = ColorUtil.plain(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (input.type() == InputType.CREATE) player.clearTitle();
            if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
                actions.msg(player, "<gray>Отменено.</gray>");
                return;
            }
            if (input.type() == InputType.CREATE) {
                boolean hadClan = manager.getClan(player) != null;
                actions.create(player, text.split("\\s+")[0]);
                Clan created = manager.getClan(player);
                if (!hadClan && created != null) ClanMenus.openClan(player, manager, created, 0);
                return;
            }
            Clan clan = manager.getById(input.clanId());
            if (clan == null || clan.member(player.getUniqueId()) == null) {
                actions.error(player, "Ты больше не в этом клане.");
                return;
            }
            switch (input.type()) {
                case CREATE -> {
                }
                case DESCRIPTION -> actions.setDescription(player, text);
                case INVITE -> actions.invite(player, text.split("\\s+")[0]);
                case RANK -> actions.setRank(player, input.target(), text);
            }
        });
    }

    // ---------------- рейтинг за убийства ----------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!actions.config().clansEnabled()) return;
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        Clan killerClan = manager.getClan(killer);
        Clan victimClan = manager.getClan(victim);
        if (killerClan != null && killerClan == victimClan) return; // свои - не считается
        if (!manager.tryCountKill(killer.getUniqueId(), victim.getUniqueId())) return;

        if (killerClan != null) {
            int amount = actions.config().clanKillRating();
            killerClan.rating(killerClan.rating() + amount);
            killerClan.kills(killerClan.kills() + 1);
            ClanMember m = killerClan.member(killer.getUniqueId());
            if (m != null) m.addKill();
            String text = actions.config().clanKillMessage();
            if (text != null && !text.isEmpty() && amount > 0) {
                killer.sendMessage(ColorUtil.parse(text, Placeholder.unparsed("amount", String.valueOf(amount))));
            }
        }
        if (victimClan != null) {
            victimClan.deaths(victimClan.deaths() + 1);
            int loss = Math.min(actions.config().clanDeathRating(), victimClan.rating());
            if (loss > 0) {
                victimClan.rating(victimClan.rating() - loss);
                String text = actions.config().clanDeathMessage();
                if (text != null && !text.isEmpty()) {
                    victim.sendMessage(ColorUtil.parse(text, Placeholder.unparsed("amount", String.valueOf(loss))));
                }
            }
        }
        manager.markDirty();
    }

    // ---------------- вход / выход ----------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Clan clan = manager.getClan(player);
        if (clan == null) return;
        ClanMember m = clan.member(player.getUniqueId());
        if (m != null && !m.name().equals(player.getName())) {
            m.name(player.getName());
            manager.markDirty();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
