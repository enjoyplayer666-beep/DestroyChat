package ru.dscraft.destroychat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dscraft.destroychat.announce.Announcer;
import ru.dscraft.destroychat.command.ChatPrefixCommand;
import ru.dscraft.destroychat.command.ColorCommand;
import ru.dscraft.destroychat.command.ContactCommand;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.hook.LuckPermsHook;
import ru.dscraft.destroychat.listener.ChatFormatListener;
import ru.dscraft.destroychat.listener.PrefixResetListener;
import ru.dscraft.destroychat.util.NameStyler;

/**
 * DestroyChat - чат DestroyCraft: формат "Ⓛ ⌜Игрок⌟ ник → сообщение", локальный/глобальный
 * каналы, /color, отдельный чат-префикс. Кланы - в плагине MediaClans.
 * <p>
 * В паре с DestroyLobby: тот запрещает чат в лобби и не пускает сообщения между лобби и
 * игровыми мирами, а также перенаправляет свою команду /prefix chat сюда (/chatprefix).
 */
public final class DestroyChatPlugin extends JavaPlugin {

    private ChatConfig chatConfig;
    private Announcer announcer;
    private ContactCommand contactCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        migrateStaffConfig();
        saveConfig();
        this.chatConfig = new ChatConfig(this);

        LuckPermsHook luckPermsHook;
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            LuckPerms api = LuckPermsProvider.get();
            luckPermsHook = new LuckPermsHook(api);
            getLogger().info("LuckPerms найден: префиксы привилегий, /prefix chat и /color включены.");
        } else {
            luckPermsHook = new LuckPermsHook(null);
            getLogger().warning("LuckPerms не найден! Префиксы привилегий, /prefix chat и /color работать не будут.");
        }

        if (getServer().getPluginManager().getPlugin("DestroyLobby") != null) {
            getLogger().info("DestroyLobby найден: чат в лобби выключен, лобби и игровые миры разделены.");
        } else {
            getLogger().warning("DestroyLobby не найден: чат будет работать и в лобби, миры не разделены.");
        }

        // кланы (/clan) - в отдельном плагине MediaClans, тег клана в чат берётся оттуда
        if (getServer().getPluginManager().getPlugin("MediaClans") == null) {
            getLogger().warning("MediaClans не найден: тега клана в чате не будет.");
        }

        NameStyler nameStyler = new NameStyler(chatConfig, luckPermsHook);
        getServer().getPluginManager().registerEvents(new PrefixResetListener(luckPermsHook), this);
        getServer().getPluginManager().registerEvents(
                new ChatFormatListener(chatConfig, luckPermsHook, nameStyler), this);

        // таб (строки игроков, цвет ника, ✔) - в отдельном плагине MediaTab

        ColorCommand colorCommand = new ColorCommand(luckPermsHook);
        if (getCommand("color") != null) {
            getCommand("color").setExecutor(colorCommand);
            getCommand("color").setTabCompleter(colorCommand);
        }
        ChatPrefixCommand chatPrefixCommand = new ChatPrefixCommand(chatConfig, luckPermsHook);
        if (getCommand("chatprefix") != null) {
            getCommand("chatprefix").setExecutor(chatPrefixCommand);
            getCommand("chatprefix").setTabCompleter(chatPrefixCommand);
        }

        // /contact - контакты команды проекта (contacts.yml), доступна всем
        contactCommand = new ContactCommand(this);
        if (getCommand("contact") != null) getCommand("contact").setExecutor(contactCommand);

        this.announcer = new Announcer(this);
        announcer.start();

        getLogger().info("DestroyChat включен.");
    }

    /**
     * copyDefaults не перезаписывает то, что уже есть в config.yml, поэтому при смене оформления
     * персонала разделы staff-stars и group-formats заменяются на новые из плагина один раз
     * (по staff-config-version). Старое переливание ника в табе убирается.
     */
    private void migrateStaffConfig() {
        var cfg = getConfig();
        var defaults = cfg.getDefaults();
        if (defaults == null) return;
        int latest = defaults.getInt("staff-config-version", 1);
        // get(path, null) смотрит только в сам файл, без значений по умолчанию
        Object current = cfg.get("staff-config-version", null);
        if (current instanceof Number n && n.intValue() >= latest) return;
        for (String section : new String[]{"staff-stars", "group-formats"}) {
            var def = defaults.getConfigurationSection(section);
            if (def == null) continue;
            cfg.set(section, null);
            for (String key : def.getKeys(true)) {
                if (!def.isConfigurationSection(key)) cfg.set(section + "." + key, def.get(key));
            }
        }
        cfg.set("tab.animated-groups", null);
        cfg.set("tab.name-color", defaults.getString("tab.name-color"));
        cfg.set("tab.update-ticks", null);
        cfg.set("staff-config-version", latest);
        getLogger().info("Оформление персонала (staff-stars, group-formats) обновлено до версии " + latest + ".");
    }

    @Override
    public void onDisable() {
        if (announcer != null) announcer.stop();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("destroychat")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                chatConfig.reload();
                if (announcer != null) announcer.start();
                if (contactCommand != null) contactCommand.reload();
                sender.sendMessage("§a[DestroyChat] Конфиг перезагружен.");
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("announce")) {
                Integer number = null;
                if (args.length >= 2) {
                    try {
                        number = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cНомер должен быть числом.");
                        return true;
                    }
                }
                if (announcer == null || !announcer.announceNow(number)) {
                    int size = announcer == null ? 0 : announcer.size();
                    sender.sendMessage(size == 0
                            ? "§cАвтосообщения выключены или пустые (announcements в config.yml)."
                            : "§cНет сообщения с таким номером. Всего: " + size);
                }
                return true;
            }
            sender.sendMessage("§7Использование: /destroychat reload | /destroychat announce [номер]");
            return true;
        }
        return false;
    }
}
