package ru.dscraft.destroychat;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dscraft.destroychat.command.ChatPrefixCommand;
import ru.dscraft.destroychat.command.ColorCommand;
import ru.dscraft.destroychat.config.ChatConfig;
import ru.dscraft.destroychat.hook.LuckPermsHook;
import ru.dscraft.destroychat.listener.ChatFormatListener;

/**
 * DestroyChat - чат DestroyCraft: формат "Ⓛ ⌜Игрок⌟ ник → сообщение", локальный/глобальный
 * каналы, /color и отдельный чат-префикс.
 * <p>
 * В паре с DestroyLobby: тот запрещает чат в лобби и не пускает сообщения между лобби и
 * игровыми мирами, а также перенаправляет свою команду /prefix chat сюда (/chatprefix).
 */
public final class DestroyChatPlugin extends JavaPlugin {

    private ChatConfig chatConfig;

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

        getServer().getPluginManager().registerEvents(new ChatFormatListener(chatConfig, luckPermsHook), this);

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
