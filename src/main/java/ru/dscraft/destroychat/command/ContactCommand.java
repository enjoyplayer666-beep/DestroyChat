package ru.dscraft.destroychat.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dscraft.destroychat.util.ColorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /contact - контакты команды проекта. Всё (ники, ссылки, строки) - в contacts.yml,
 * перезагрузка вместе с /destroychat reload. Доступна всем игрокам.
 */
public final class ContactCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private List<Component> message = List.of();

    public ContactCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "contacts.yml");
        if (!f.exists()) plugin.saveResource("contacts.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        String entry = y.getString("entry-format",
                "<white>{name} <#888888>» <click:open_url:'{url}'><#E5A15C>{link}</click>");
        List<Component> out = new ArrayList<>();
        for (String line : y.getStringList("lines")) {
            if (line.trim().equals("{contacts}")) {
                for (Map<?, ?> c : y.getMapList("contacts")) {
                    String name = String.valueOf(c.get("name"));
                    String link = String.valueOf(c.get("link"));
                    Object u = c.get("url");
                    String url = u != null ? String.valueOf(u) : (link.startsWith("http") ? link : "https://" + link);
                    out.add(ColorUtil.parse(entry.replace("{name}", name).replace("{link}", link).replace("{url}", url)));
                }
            } else {
                out.add(ColorUtil.parse(line));
            }
        }
        message = out;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        for (Component c : message) sender.sendMessage(c);
        return true;
    }
}
