package ru.dscraft.mediaclans;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Клановый чат (%), огонь по своим, клановый рейтинг за убийства, ник и последний вход. */
public final class ClanListener implements Listener {

    private final ClanManager manager;
    private final ClanActions actions;
    private final ChatInput input;
    /** Когда игроку последний раз писали "PvP между своими выключено!" - чтобы не спамить при каждом ударе. */
    private final java.util.Map<java.util.UUID, Long> pvpWarned = new java.util.concurrent.ConcurrentHashMap<>();

    public ClanListener(ClanActions actions, ChatInput input) {
        this.actions = actions;
        this.manager = actions.manager();
        this.input = input;
    }

    // ---------------- клановый чат ----------------

    /** Сразу после ChatInput (LOWEST): сообщение с % уходит только клану и в общий чат не попадает. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String symbol = actions.settings().chatSymbol();
        if (symbol == null || symbol.isEmpty()) return;
        String text = ColorUtil.plain(event.message());
        if (!text.startsWith(symbol)) return;
        Player player = event.getPlayer();
        if (input.waiting(player)) return;
        event.setCancelled(true);
        String message = text.substring(symbol.length()).trim();
        Clan clan = manager.getClan(player);
        if (clan == null) {
            actions.error(player, "Ты не состоишь в клане.");
            return;
        }
        if (!clan.has(player.getUniqueId(), Perm.CHAT)) {
            actions.error(player, "У твоей роли нет права писать в клановый чат.");
            return;
        }
        if (message.isEmpty()) return;
        actions.clanChat(player, clan, message);
    }

    // ---------------- огонь по своим ----------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Tameable t && t.getOwner() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;
        Clan clan = manager.getClan(attacker);
        if (clan != null && !clan.pvp() && clan == manager.getClan(victim)) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = pvpWarned.get(attacker.getUniqueId());
            if (last == null || now - last > 2000L) {
                pvpWarned.put(attacker.getUniqueId(), now);
                actions.msg(attacker, "PvP между своими выключено!");
            }
        }
    }

    // ---------------- рейтинг за убийства ----------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        Clan killerClan = manager.getClan(killer);
        Clan victimClan = manager.getClan(victim);
        if (killerClan != null && killerClan == victimClan) return; // свои - не считается
        if (!manager.tryCountKill(killer.getUniqueId(), victim.getUniqueId())) return;
        Settings s = actions.settings();

        if (killerClan != null) {
            int amount = s.killRating();
            String booster = s.boosterPermission();
            if (booster != null && !booster.isBlank() && killer.hasPermission(booster)) amount *= 2;
            killerClan.rating(killerClan.rating() + amount);
            killerClan.kills(killerClan.kills() + 1);
            ClanMember m = killerClan.member(killer.getUniqueId());
            if (m != null) {
                m.kills(m.kills() + 1);
                m.rating(m.rating() + amount);
            }
            String text = s.killMessage();
            if (text != null && !text.isEmpty() && amount > 0) {
                killer.sendMessage(ColorUtil.parse(text, Placeholder.unparsed("amount", String.valueOf(amount))));
            }
        }
        if (victimClan != null) {
            victimClan.deaths(victimClan.deaths() + 1);
            ClanMember m = victimClan.member(victim.getUniqueId());
            if (m != null) m.deaths(m.deaths() + 1);
            int loss = Math.min(s.deathRating(), victimClan.rating());
            if (loss > 0) {
                victimClan.rating(victimClan.rating() - loss);
                String text = s.deathMessage();
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
        touch(event.getPlayer());
        notifyClan(event.getPlayer(), "Игрок <#59FF3B><name></#59FF3B> вошел на сервер.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        touch(event.getPlayer());
        pvpWarned.remove(event.getPlayer().getUniqueId());
        notifyClan(event.getPlayer(), "Игрок <#FF3B3B><name></#FF3B3B> покинул сервер.");
    }

    /** Сообщение о входе/выходе участникам клана онлайн, у которых включены такие сообщения. */
    private void notifyClan(Player player, String text) {
        Clan clan = manager.getClan(player);
        if (clan == null) return;
        for (ClanMember m : clan.membersMap().values()) {
            if (m.uuid().equals(player.getUniqueId()) || !m.notifyJoins()) continue;
            Player online = org.bukkit.Bukkit.getPlayer(m.uuid());
            if (online != null) actions.msg(online, text, Placeholder.unparsed("name", player.getName()));
        }
    }

    private void touch(Player player) {
        Clan clan = manager.getClan(player);
        ClanMember m = clan == null ? null : clan.member(player.getUniqueId());
        if (m == null) return;
        m.name(player.getName());
        m.lastSeen(System.currentTimeMillis());
        manager.markDirty();
    }
}
