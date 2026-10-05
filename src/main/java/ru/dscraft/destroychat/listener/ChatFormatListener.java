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
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.hook.LuckPermsHook;
import ru.dscraft.destroychat.hook.ClanHook;
import ru.dscraft.destroychat.hook.StatHook;
import ru.dscraft.destroychat.util.ChatColors;
import ru.dscraft.destroychat.util.ColorUtil;
import ru.dscraft.destroychat.util.NameStyler;
import ru.dscraft.destroychat.util.Perms;

/**
 * Чат сервера.
 * <p>
 * Формат (как на скрине): {@code [Ⓛ/Ⓖ] [Клан] ⌜Игрок⌟ ник → сообщение}
 * <ul>
 *   <li>[Клан] - тег клана из MediaClans (chat-tag в его конфиге), если игрок в клане;</li>
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
    private final NameStyler nameStyler;

    public ChatFormatListener(ChatConfig config, LuckPermsHook luckPermsHook,
                              NameStyler nameStyler) {
        this.config = config;
        this.luckPermsHook = luckPermsHook;
        this.nameStyler = nameStyler;
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
        ChatConfig.GroupFormat group = nameStyler.group(sender);
        // хвост префикса (коды после текста) - цвет ника: /prefix chat &6КОРОЛЬ &2&l
        NameStyler.Split split = NameStyler.split(resolvePrefix(sender, group));
        Component prefix = split.prefix() == null || split.prefix().isBlank()
                ? Component.empty() : ColorUtil.rich(split.prefix());

        Component name = nameStyler.chatName(sender, split.nickStyle(), group);
        var menu = config.playerMenu();
        if (menu != null && menu.getBoolean("enabled", true)) {
            java.util.Map<String, String> ph = java.util.Map.of("name", sender.getName(),
                    "world", menu.getString("worlds." + sender.getWorld().getName(), sender.getWorld().getName()));
            name = name
                    .clickEvent(ClickEvent.runCommand("/" + ru.dscraft.destroychat.command.PlayerMenuCommand.NAME + " " + sender.getName()))
                    .hoverEvent(HoverEvent.showText(ColorUtil.parse(menu.getString("name-hover", ""), ph)));
        } else if (config.nameClickMsg()) {
            name = name
                    .clickEvent(ClickEvent.suggestCommand("/msg " + sender.getName() + " "))
                    .hoverEvent(HoverEvent.showText(Component.text("Написать в личные сообщения", NamedTextColor.GRAY)));
        }

        return ColorUtil.parse(config.layout(),
                Placeholder.component("icon", icon),
                Placeholder.component("rank", resolveRank(sender)),
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

    /** Ранг из DsRanks (например "☠ Лич "), пусто - нет плагина или /rank off. */
    private Component resolveRank(Player sender) {
        String rank = StatHook.chatRank(sender);
        if (rank == null || rank.isBlank()) return Component.empty();
        Component c = ColorUtil.rich(rank);
        // наведение на ранг - карточка ранга (ник, ранг, бустер, убийства, умения)
        String hover = StatHook.rankHover(sender);
        if (hover != null && !hover.isBlank()) c = c.hoverEvent(HoverEvent.showText(ColorUtil.rich(hover)));
        return c;
    }

    /** [Клан] из MediaClans с карточкой клана при наведении, пусто - если игрок не в клане. */
    private Component resolveClanTag(Player sender) {
        Component tag = ClanHook.chatTag(sender);
        return tag == null ? Component.empty() : tag;
    }

    /** Личный чат-префикс -> префикс группы (group-formats) -> префикс LuckPerms -> "⌜Игрок⌟" из конфига. */
    private String resolvePrefix(Player sender, ChatConfig.GroupFormat group) {
        String raw = null;

        // свой /prefix chat главнее всего, в том числе у команды проекта
        if (sender.hasPermission(Perms.PREFIX_CHAT)) {
            String own = luckPermsHook.getMetaValue(sender, Perms.META_CHAT_PREFIX);
            if (own != null && !own.isBlank()) raw = own;
        }
        if (raw == null && group != null && group.chatPrefix() != null && !group.chatPrefix().isBlank()) {
            raw = group.chatPrefix();
        }
        if (raw == null) {
            String lp = luckPermsHook.getPrefix(sender);
            if (!lp.isBlank()) raw = lp;
        }
        if (raw == null) {
            raw = config.defaultPrefix();
        }
        if (raw == null || raw.isBlank()) return null;
        if (!raw.endsWith(" ")) raw = raw + " ";
        return raw;
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
