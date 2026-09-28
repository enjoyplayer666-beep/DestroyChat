package ru.dscraft.destroychat.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import ru.dscraft.destroychat.clan.Clan;
import ru.dscraft.destroychat.clan.ClanManager;
import ru.dscraft.destroychat.clan.ClanText;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.hook.LuckPermsHook;
import ru.dscraft.destroychat.util.ChatColors;
import ru.dscraft.destroychat.util.ColorUtil;
import ru.dscraft.destroychat.util.Perms;

/**
 * Чат сервера.
 * <p>
 * Формат (как на скрине): {@code [Ⓛ/Ⓖ] [Клан] ⌜Игрок⌟ ник → сообщение}
 * <ul>
 *   <li>[Клан] - тег клана сразу после значка канала (clans.chat-tag), если игрок в клане;</li>
 *   <li>Ⓛ - локальный чат (радиус {@code chat.local-radius}), обычное сообщение;</li>
 *   <li>Ⓖ - глобальный чат, сообщение начинается с {@code !};</li>
 *   <li>префикс: личный чат-префикс (/prefix chat, Ultra+) -&gt; префикс из LuckPerms
 *       (привилегия или /prefix set) -&gt; {@code chat.format.default-prefix} (⌜Игрок⌟);</li>
 *   <li>ник всегда &7, префикс его не красит;</li>
 *   <li>цвет сообщения: /color (Elite SP) -&gt; стандартный цвет канала;
 *       &-коды в тексте - с правом destroylobby.chat.colors (Legend+).</li>
 * </ul>
 * Работа в паре с DestroyLobby: DestroyLobby запрещает чат в лобби (отменяет сообщение
 * раньше, на приоритете LOW - этот обработчик отменённые не трогает) и убирает получателей
 * из другой группы миров (HIGH - лобби не слышит SkyPvP). Здесь только оформление и радиус.
 */
public class ChatFormatListener implements Listener {

    private final ChatConfig config;
    private final LuckPermsHook luckPermsHook;
    private final ClanManager clanManager;

    public ChatFormatListener(ChatConfig config, LuckPermsHook luckPermsHook, ClanManager clanManager) {
        this.config = config;
        this.luckPermsHook = luckPermsHook;
        this.clanManager = clanManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String raw = ColorUtil.plain(event.message());

        // ---- канал ----
        int radius = config.localRadius();
        String globalSymbol = config.globalSymbol();
        boolean global;
        if (radius <= 0) {
            global = true; // локальный чат выключен - всё идёт в глобальный
        } else if (!globalSymbol.isEmpty() && raw.startsWith(globalSymbol)) {
            global = true;
            raw = raw.substring(globalSymbol.length()).stripLeading();
        } else {
            global = false;
        }
        if (raw.isBlank()) {
            event.setCancelled(true);
            return;
        }

        // ---- сообщение и "шапка" ----
        Component message = buildMessage(sender, raw, global);
        Component head = buildHead(sender, global);

        event.message(message);
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, msg) -> head.append(msg)));

        // ---- локальный радиус ----
        if (!global) {
            World world = sender.getWorld();
            Location loc = sender.getLocation();
            double r2 = (double) radius * radius;
            String spy = config.spyPermission();

            event.viewers().removeIf(audience -> audience instanceof Player viewer
                    && !viewer.equals(sender)
                    && !(spy != null && !spy.isEmpty() && viewer.hasPermission(spy))
                    && (!viewer.getWorld().equals(world) || viewer.getLocation().distanceSquared(loc) > r2));

            boolean heard = event.viewers().stream().anyMatch(audience -> audience instanceof Player viewer
                    && !viewer.equals(sender)
                    && sender.canSee(viewer)
                    && viewer.getWorld().equals(world)
                    && viewer.getLocation().distanceSquared(loc) <= r2);
            String nobody = config.nobodyHeardMessage();
            if (!heard && nobody != null && !nobody.isEmpty()) {
                sender.sendMessage(ColorUtil.parse(nobody));
            }
        }
    }

    private Component buildHead(Player sender, boolean global) {
        Component icon = ColorUtil.parse(global ? config.globalIcon() : config.localIcon());
        Component prefix = resolvePrefix(sender);

        Component name = Component.text(sender.getName(),
                ColorUtil.parseColor(config.nameColor(), NamedTextColor.GRAY));
        if (config.nameClickMsg()) {
            name = name
                    .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "))
                    .hoverEvent(HoverEvent.showText(Component.text("Написать в личные сообщения", NamedTextColor.GRAY)));
        }

        return ColorUtil.parse(config.layout(),
                Placeholder.component("icon", icon),
                Placeholder.component("clan", resolveClanTag(sender)),
                Placeholder.component("prefix", prefix),
                Placeholder.component("name", name),
                Placeholder.component("stars", resolveStars(sender)));
    }

    /** Звёзды персонала после ника (staff-stars в config.yml), с пробелом перед ними. */
    private Component resolveStars(Player sender) {
        String stars = config.staffStars(group -> sender.hasPermission("group." + group));
        if (stars == null || stars.isBlank()) return Component.empty();
        return Component.space().append(ColorUtil.parse(stars));
    }

    /** [Клан] с карточкой клана при наведении, пусто - если игрок не в клане. */
    private Component resolveClanTag(Player sender) {
        if (!config.clansEnabled()) return Component.empty();
        Clan clan = clanManager.getClan(sender.getUniqueId());
        return clan == null ? Component.empty() : ClanText.chatTag(clan, clanManager, config);
    }

    /** Личный чат-префикс -> префикс LuckPerms -> "⌜Игрок⌟" из конфига. */
    private Component resolvePrefix(Player sender) {
        String raw = null;

        if (sender.hasPermission(Perms.PREFIX_CHAT)) {
            String own = luckPermsHook.getMetaValue(sender, Perms.META_CHAT_PREFIX);
            if (own != null && !own.isBlank()) raw = own;
        }
        if (raw == null) {
            String lp = luckPermsHook.getPrefix(sender);
            if (!lp.isBlank()) raw = lp;
        }
        if (raw == null) {
            raw = config.defaultPrefix();
        }
        if (raw == null || raw.isBlank()) return Component.empty();
        if (!raw.endsWith(" ")) raw = raw + " ";
        return ColorUtil.rich(raw);
    }

    private Component buildMessage(Player sender, String raw, boolean global) {
        String colorTag = global ? config.globalMessageColor() : config.localMessageColor();

        if (sender.hasPermission(Perms.CHAT_COLOR)) {
            String own = luckPermsHook.getMetaValue(sender, Perms.META_CHAT_COLOR);
            if (ChatColors.isValidTag(own)) colorTag = own;
        }

        // текст игрока экранируем, чтобы он не мог писать MiniMessage-теги
        String body = ColorUtil.escape(raw);
        if (sender.hasPermission(Perms.CHAT_CODES) && (raw.indexOf('&') >= 0)) {
            body = ColorUtil.legacyToTags(body, false); // &-коды разрешены, &k (мигание) - нет
        }
        return ColorUtil.safe((colorTag == null ? "" : colorTag) + body);
    }
}
