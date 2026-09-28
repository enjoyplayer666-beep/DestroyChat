package ru.dscraft.destroychat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dscraft.destroychat.clan.ClanActions;
import ru.dscraft.destroychat.clan.ClanCommand;
import ru.dscraft.destroychat.clan.ClanListener;
import ru.dscraft.destroychat.clan.ClanManager;
import ru.dscraft.destroychat.command.ChatPrefixCommand;
import ru.dscraft.destroychat.command.ColorCommand;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.hook.LuckPermsHook;
import ru.dscraft.destroychat.listener.ChatFormatListener;
import ru.dscraft.destroychat.tab.TabListManager;

/**
 * DestroyChat - чат DestroyCraft: формат "Ⓛ ⌜Игрок⌟ ник → сообщение", локальный/глобальный
 * каналы, /color, отдельный чат-префикс и кланы (/clan, /c).
 * <p>
 * В паре с DestroyLobby: тот запрещает чат в лобби и не пускает сообщения между лобби и
 * игровыми мирами, а также перенаправляет свою команду /prefix chat сюда (/chatprefix).
 */
public final class DestroyChatPlugin extends JavaPlugin {

    private ChatConfig chatConfig;
    private ClanManager clanManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
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

        this.clanManager = new ClanManager(this, chatConfig);
        clanManager.load();
        ClanActions clanActions = new ClanActions(clanManager, chatConfig);
        getServer().getPluginManager().registerEvents(new ClanListener(this, clanActions), this);
        ClanCommand clanCommand = new ClanCommand(clanActions);
        if (getCommand("clan") != null) {
            getCommand("clan").setExecutor(clanCommand);
            getCommand("clan").setTabCompleter(clanCommand);
        }
        // кланы сохраняются раз в минуту, если что-то поменялось
        getServer().getScheduler().runTaskTimer(this, clanManager::saveIfDirty, 1200L, 1200L);

        getServer().getPluginManager().registerEvents(new ChatFormatListener(chatConfig, luckPermsHook, clanManager), this);

        if (getServer().getPluginManager().getPlugin("TAB") != null) {
            getLogger().info("Найден плагин TAB: таб оформляет он, модуль таба DestroyChat выключен.");
        } else if (luckPermsHook.isEnabled()) {
            TabListManager tab = new TabListManager(chatConfig, luckPermsHook);
            getServer().getPluginManager().registerEvents(tab, this);
            getServer().getScheduler().runTaskTimer(this, tab, 20L, chatConfig.tabUpdateTicks());
        }

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

        getLogger().info("DestroyChat включен.");
    }

    @Override
    public void onDisable() {
        if (clanManager != null) clanManager.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("destroychat")) {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                chatConfig.reload();
                sender.sendMessage("§a[DestroyChat] Конфиг перезагружен.");
                return true;
            }
            sender.sendMessage("§7Использование: /destroychat reload");
            return true;
        }
        return false;
    }
}
