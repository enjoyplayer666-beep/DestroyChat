package ru.dscraft.mediabans;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MediaBans - баны, муты и кики DestroyCraft в своём оформлении.
 * Донатеры (ultra и выше) - только временные муты/баны в пределах своей группы,
 * команда проекта - без ограничений, наказать команду проекта нельзя.
 */
public final class MediaBansPlugin extends ru.dscraft.destroychat.module.Module {

    static final List<String> COMMANDS = List.of(
            "ban", "tempban", "mute", "tempmute", "kick", "unban", "unmute", "checkban", "checkmute", "banlist", "mediabans");

    private PunishStore store;
    private final Access access = new Access();
    private final Style style = new Style();
    private PunishCommand command;
    private final Set<String> mutedCommands = new HashSet<>();
    /** Имя или алиас команды -> главное имя. */
    private final Map<String, String> labels = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        store = new PunishStore(new File(getDataFolder(), "punishments.yml"), getLogger());
        store.load();
        reload();

        command = new PunishCommand(this);
        for (String name : COMMANDS) {
            PluginCommand pc = getCommand(name);
            if (pc == null) continue;
            pc.setExecutor(command);
            pc.setTabCompleter(command);
            labels.put(name, name);
            for (String alias : pc.getAliases()) labels.put(alias.toLowerCase(Locale.ROOT), name);
        }
        getServer().getPluginManager().registerEvents(new PunishListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, store::cleanup, 20L * 60, 20L * 60);
        // когда все плагины загрузились - забрать /ban, /mute, /kick ... у Essentials и других
        Bukkit.getScheduler().runTask(this, this::takeOverCommands);
        getLogger().info("MediaBans включен: банов " + store.all(Punishment.Type.BAN).size()
                + ", мутов " + store.all(Punishment.Type.MUTE).size() + ".");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
    }

    public void reload() {
        reloadConfig();
        access.load(getConfig(), getLogger());
        style.load(getConfig());
        mutedCommands.clear();
        for (String c : getConfig().getStringList("muted-commands")) {
            String label = c.trim().toLowerCase(Locale.ROOT);
            mutedCommands.add(label.startsWith("/") ? label.substring(1) : label);
        }
    }

    /** Наши команды важнее одноимённых команд других плагинов (Essentials, ваниль и т.п.). */
    private void takeOverCommands() {
        Map<String, Command> known = Bukkit.getCommandMap().getKnownCommands();
        int taken = 0;
        for (Map.Entry<String, String> e : labels.entrySet()) {
            PluginCommand ours = getCommand(e.getValue());
            if (ours == null) continue;
            Command current = known.get(e.getKey());
            if (current != ours) {
                known.put(e.getKey(), ours);
                taken++;
            }
        }
        if (taken > 0) {
            getLogger().info("Команды других плагинов заменены на MediaBans: " + taken + ".");
            for (Player p : Bukkit.getOnlinePlayers()) p.updateCommands();
        }
    }

    // ---------------- для остальных классов ----------------

    public PunishStore store() {
        return store;
    }

    public Access access() {
        return access;
    }

    public Style style() {
        return style;
    }

    public PunishCommand command() {
        return command;
    }

    public Set<String> mutedCommands() {
        return mutedCommands;
    }

    /** Главное имя нашей команды по имени/алиасу, null - не наша. */
    public String mainCommand(String label) {
        return labels.get(label);
    }

    public Component noCommand() {
        return MiniMessage.miniMessage().deserialize(getConfig().getString("no-command-message", "<white>Нет такой команды :/</white>"));
    }

    /** Выполнить в основном потоке (ответ LuckPerms приходит из другого). */
    public void sync(Runnable task) {
        if (!isEnabled()) return;
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(this, task);
    }

    /** Наказание видят все (broadcast: all) или только команда проекта (broadcast: staff); консоль - всегда. */
    public void broadcast(Component message) {
        if (getConfig().getString("broadcast", "all").equalsIgnoreCase("staff")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (access.isStaff(p)) p.sendMessage(message);
            }
            Bukkit.getConsoleSender().sendMessage(message);
        } else {
            Bukkit.getServer().sendMessage(message);
        }
    }
}
