package ru.dscraft.destroychat.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import ru.dscraft.destroychat.DestroyChatPlugin;
import ru.dscraft.destroychat.util.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Команды по привилегиям (commands.yml).
 * Игрок видит в Tab и может вводить только команды своей привилегии и всех младших,
 * им же выдаются права из списка. Остальное - "Нет такой команды :/".
 * Опы и команда проекта (staff-groups) не ограничиваются.
 */
public class CommandAccess implements Listener {

    public static final String DEFAULT_MESSAGE = "<#C9C9FB>Нет такой команды :/</#C9C9FB>";
    public static final String DEFAULT_SPAM_MESSAGE = "<#E53232>◆</#E53232> <#C7C4B7>Не используйте так часто!</#C7C4B7>";

    private record Rank(String group, List<String> commands, List<String> permissions) {
    }

    private final DestroyChatPlugin plugin;
    private final File file;
    private final List<Rank> ranks = new ArrayList<>();
    private final Set<String> always = new HashSet<>();
    private final List<String> staffGroups = new ArrayList<>();
    private boolean enabled = true;
    private String message = DEFAULT_MESSAGE;
    private long spamInterval = 1000;
    private String spamMessage = DEFAULT_SPAM_MESSAGE;
    /** Когда игрок последний раз ввёл команду. */
    private final Map<UUID, Long> lastCommand = new HashMap<>();

    /** Выданные нами права и на какой привилегии они посчитаны. */
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, Integer> appliedRank = new HashMap<>();

    public CommandAccess(DestroyChatPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "commands.yml");
        reload();
        // группу могли сменить через LuckPerms - раз в 5 секунд сверяем
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) refresh(p, false);
        }, 100L, 100L);
    }

    public void reload() {
        if (!file.exists()) plugin.saveResource("commands.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        enabled = cfg.getBoolean("enabled", true);
        message = cfg.getString("message", DEFAULT_MESSAGE);
        spamInterval = Math.max(0, cfg.getLong("anti-spam.interval-ms", 1000));
        spamMessage = cfg.getString("anti-spam.message", DEFAULT_SPAM_MESSAGE);
        always.clear();
        for (String c : cfg.getStringList("always")) always.add(normalize(c));
        staffGroups.clear();
        for (String g : cfg.getStringList("staff-groups")) staffGroups.add(g.toLowerCase(Locale.ROOT));
        ranks.clear();
        ConfigurationSection section = cfg.getConfigurationSection("ranks");
        if (section != null) {
            for (String group : section.getKeys(false)) {
                List<String> commands = new ArrayList<>();
                for (String c : section.getStringList(group + ".commands")) commands.add(normalize(c));
                ranks.add(new Rank(group.toLowerCase(Locale.ROOT), commands, section.getStringList(group + ".permissions")));
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p, true);
    }

    private static String normalize(String command) {
        String c = command.trim().toLowerCase(Locale.ROOT);
        return c.startsWith("/") ? c.substring(1) : c;
    }

    public Component message() {
        return ColorUtil.parse(message);
    }

    // ---------------- кто кто ----------------

    /** Опы и команда проекта - без ограничений по командам. */
    private boolean unrestricted(Player player) {
        if (!enabled || player.isOp()) return true;
        for (String g : staffGroups) {
            if (player.hasPermission("group." + g)) return true;
        }
        return false;
    }

    /** Номер старшей привилегии игрока; первая (default) есть у всех. */
    private int rankIndex(Player player) {
        int index = 0;
        for (int i = 1; i < ranks.size(); i++) {
            if (player.hasPermission("group." + ranks.get(i).group())) index = i;
        }
        return index;
    }

    private Set<String> allowed(Player player) {
        Set<String> out = new HashSet<>(always);
        int top = rankIndex(player);
        for (int i = 0; i <= top && i < ranks.size(); i++) out.addAll(ranks.get(i).commands());
        return out;
    }

    /** Можно ли ввести: по имени или по алиасу той же команды. */
    private boolean canRun(Set<String> allowed, String label) {
        if (allowed.contains(label)) return true;
        Command command = Bukkit.getCommandMap().getCommand(label);
        if (command == null) return false;
        if (allowed.contains(command.getName().toLowerCase(Locale.ROOT))) return true;
        for (String alias : command.getAliases()) {
            if (allowed.contains(alias.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    // ---------------- права ----------------

    /** Выдать права привилегии (и младших), если привилегия сменилась или force. */
    private void refresh(Player player, boolean force) {
        // опам (-1) и команде проекта (-2) права отсюда не нужны - у них свои в LuckPerms
        int index = player.isOp() ? -1 : (unrestricted(player) ? -2 : rankIndex(player));
        if (!force && appliedRank.getOrDefault(player.getUniqueId(), Integer.MIN_VALUE) == index) return;
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
            }
        }
        appliedRank.put(player.getUniqueId(), index);
        if (index >= 0) {
            Set<String> perms = new LinkedHashSet<>();
            for (int i = 0; i <= index && i < ranks.size(); i++) perms.addAll(ranks.get(i).permissions());
            if (!perms.isEmpty()) {
                PermissionAttachment attachment = player.addAttachment(plugin);
                for (String perm : perms) {
                    String node = perm.trim();
                    if (node.isEmpty()) continue;
                    if (node.startsWith("-")) attachment.setPermission(node.substring(1), false);
                    else attachment.setPermission(node, true);
                }
                attachments.put(player.getUniqueId(), attachment);
            }
        }
        player.updateCommands();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        attachments.remove(event.getPlayer().getUniqueId());
        lastCommand.remove(event.getPlayer().getUniqueId());
        appliedRank.remove(event.getPlayer().getUniqueId());
    }

    // ---------------- Tab и ввод ----------------

    /** В Tab - только то, что написано в commands.yml для привилегии игрока. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (unrestricted(player)) return;
        Set<String> allowed = allowed(player);
        event.getCommands().removeIf(c -> !allowed.contains(c.toLowerCase(Locale.ROOT)));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (unrestricted(player)) return;
        String text = event.getMessage();
        if (text.length() < 2) return;
        String label = text.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (label.indexOf(':') >= 0 || !canRun(allowed(player), label)) {
            event.setCancelled(true);
            player.sendMessage(message());
            return;
        }
        // антиспам: команды чаще раза в interval-ms (авторизацию не трогаем)
        if (spamInterval > 0 && !always.contains(label)) {
            long now = System.currentTimeMillis();
            Long last = lastCommand.get(player.getUniqueId());
            if (last != null && now - last < spamInterval) {
                event.setCancelled(true);
                player.sendMessage(ColorUtil.parse(spamMessage));
                return;
            }
            lastCommand.put(player.getUniqueId(), now);
        }
    }
}
