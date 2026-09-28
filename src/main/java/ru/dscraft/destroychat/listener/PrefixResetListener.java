package ru.dscraft.destroychat.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import ru.dscraft.destroychat.hook.LuckPermsHook;
import ru.dscraft.destroychat.util.Perms;

import java.util.Locale;
import java.util.Set;

/**
 * /prefix reset - команда DestroyLobby, она сбрасывает только префикс в LuckPerms (таб).
 * Здесь заодно сбрасывается и отдельный чат-префикс (/prefix chat), чтобы сброс был полным.
 */
public class PrefixResetListener implements Listener {

    private static final Set<String> RESET_WORDS = Set.of("reset", "clear", "off", "сброс");

    private final LuckPermsHook luckPermsHook;

    public PrefixResetListener(LuckPermsHook luckPermsHook) {
        this.luckPermsHook = luckPermsHook;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length != 2) return; // только "/prefix reset", не "/prefix chat reset"
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = "/" + cmd.substring(colon + 1); // /destroylobby:prefix
        if (!cmd.equals("/prefix") || !RESET_WORDS.contains(parts[1].toLowerCase(Locale.ROOT))) return;

        Player player = event.getPlayer();
        luckPermsHook.clearMetaValue(player, Perms.META_CHAT_PREFIX);
    }
}
