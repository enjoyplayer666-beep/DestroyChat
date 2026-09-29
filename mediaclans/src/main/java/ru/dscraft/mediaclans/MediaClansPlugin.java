package ru.dscraft.mediaclans;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** MediaClans: кланы DestroyCraft - меню, роли, клановый чат, рейтинг и топ. */
public class MediaClansPlugin extends JavaPlugin {

    private ClanManager manager;

    @Override
    public void onEnable() {
        boolean freshConfig = !new File(getDataFolder(), "config.yml").exists();
        saveDefaultConfig();
        if (freshConfig) importConfig();

        Settings settings = new Settings(this);
        manager = new ClanManager(this, settings);
        if (manager.hasData()) {
            manager.load();
        } else {
            int moved = manager.importFromDestroyChat();
            if (moved > 0) getLogger().info("Перенесено кланов из DestroyChat: " + moved);
        }
        ClanApi.init(manager);

        ClanActions actions = new ClanActions(manager);
        ChatInput input = new ChatInput(this, actions);
        ClanMenus menus = new ClanMenus(manager, actions, input);
        getServer().getPluginManager().registerEvents(input, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new ClanListener(actions, input), this);
        ClanCommand command = new ClanCommand(actions, menus);
        getCommand("clan").setExecutor(command);
        getCommand("clan").setTabCompleter(command);

        getServer().getScheduler().runTaskTimer(this, manager::saveIfDirty, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
        ClanApi.init(null);
    }

    /** Первый запуск: настройки кланов из раздела clans: в plugins/DestroyChat/config.yml. */
    private void importConfig() {
        File old = new File(getDataFolder().getParentFile(), "DestroyChat/config.yml");
        if (!old.exists()) return;
        ConfigurationSection clans = YamlConfiguration.loadConfiguration(old).getConfigurationSection("clans");
        if (clans == null) return;
        FileConfiguration cfg = getConfig();
        String[] keys = {"chat-tag", "kill-rating", "death-rating", "kill-cooldown-seconds", "kill-message", "death-message",
                "name-min-length", "name-max-length", "max-members", "invite-seconds", "top-page-size", "description-max-length"};
        for (String key : keys) {
            if (clans.contains(key)) cfg.set(key, clans.get(key));
        }
        ConfigurationSection statuses = clans.getConfigurationSection("statuses");
        if (statuses != null) {
            cfg.set("statuses", null);
            for (String k : statuses.getKeys(false)) cfg.set("statuses." + k, statuses.get(k));
        }
        saveConfig();
        getLogger().info("Настройки кланов перенесены из DestroyChat/config.yml.");
    }
}
