package ru.dscraft.destroychat.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.hook.LuckPermsHook;

/**
 * Цвет и наклон ника - одинаково для чата и таба.
 * <ol>
 *   <li>свой цвет из /nickcolor (право destroychat.nickcolor);</li>
 *   <li>команда проекта (group-formats): в чате name-style, в табе tab.staff-name-color (белый);</li>
 *   <li>остальные: format.name-color / tab.name-color (серый &amp;7).</li>
 * </ol>
 * Курсив из /nickcolor italic добавляется поверх.
 */
public class NameStyler {

    private final ChatConfig config;
    private final LuckPermsHook luckPermsHook;

    public NameStyler(ChatConfig config, LuckPermsHook luckPermsHook) {
        this.config = config;
        this.luckPermsHook = luckPermsHook;
    }

    public ChatConfig.GroupFormat group(Player player) {
        return config.groupFormat(luckPermsHook.getPrimaryGroup(player), g -> player.hasPermission("group." + g));
    }

    public Component chatName(Player player) {
        return style(player, group(player), false);
    }

    public Component tabName(Player player) {
        return style(player, group(player), true);
    }

    private Component style(Player player, ChatConfig.GroupFormat group, boolean tab) {
        String name = player.getName();
        Component result;
        String own = player.hasPermission(Perms.NICK_COLOR)
                ? luckPermsHook.getMetaValue(player, Perms.META_NAME_COLOR) : null;
        if (ChatColors.isValidTag(own)) {
            result = ColorUtil.safe(own + name);
        } else if (group != null) {
            String style = tab ? config.tabStaffNameColor() : group.nameStyle();
            result = style == null || style.isBlank()
                    ? Component.text(name, NamedTextColor.WHITE)
                    : ColorUtil.rich(style + name);
        } else {
            String color = tab ? config.tabNameColor() : config.nameColor();
            result = ColorUtil.rich(color + name);
        }
        result = Component.empty().append(result);
        if (player.hasPermission(Perms.NICK_COLOR)
                && "true".equalsIgnoreCase(luckPermsHook.getMetaValue(player, Perms.META_NAME_ITALIC))) {
            result = result.decoration(TextDecoration.ITALIC, true);
        }
        return result;
    }
}
