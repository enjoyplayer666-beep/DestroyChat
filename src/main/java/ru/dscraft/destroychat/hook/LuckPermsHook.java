package ru.dscraft.destroychat.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Работа с LuckPerms: префикс привилегии и личные мета-значения игрока
 * (чат-префикс, цвет сообщений). Всё хранится в LuckPerms, своих файлов у плагина нет.
 */
public class LuckPermsHook {

    private final LuckPerms api; // null, если LuckPerms не установлен

    public LuckPermsHook(LuckPerms api) {
        this.api = api;
    }

    public boolean isEnabled() {
        return api != null;
    }

    private User getUser(Player player) {
        if (api == null) return null;
        return api.getUserManager().getUser(player.getUniqueId());
    }

    /** Префикс привилегии (или личный из /prefix set), пустая строка если нет. */
    public String getPrefix(Player player) {
        User user = getUser(player);
        if (user == null) return "";
        String prefix = user.getCachedData().getMetaData().getPrefix();
        return prefix == null ? "" : prefix;
    }

    /** Есть ли у игрока свой префикс (/prefix set) - он главнее оформления привилегии. */
    public boolean hasOwnPrefix(Player player) {
        User user = getUser(player);
        return user != null && !user.getNodes(NodeType.PREFIX).isEmpty();
    }

    /** Суффикс (например " &a&l✔" у персонала), пустая строка если нет. */
    public String getSuffix(Player player) {
        User user = getUser(player);
        if (user == null) return "";
        String suffix = user.getCachedData().getMetaData().getSuffix();
        return suffix == null ? "" : suffix;
    }

    /** Основная группа игрока в LuckPerms, null если LuckPerms нет. */
    public String getPrimaryGroup(Player player) {
        User user = getUser(player);
        return user == null ? null : user.getPrimaryGroup();
    }

    public String getMetaValue(Player player, String key) {
        User user = getUser(player);
        if (user == null) return null;
        return user.getCachedData().getMetaData().getMetaValue(key);
    }

    public boolean setMetaValue(Player player, String key, String value) {
        User user = getUser(player);
        if (user == null) return false;
        removeMetaNodes(user, key);
        DataMutateResult result = user.data().add(MetaNode.builder(key, value).build());
        api.getUserManager().saveUser(user);
        return result.wasSuccessful();
    }

    public boolean clearMetaValue(Player player, String key) {
        User user = getUser(player);
        if (user == null) return false;
        boolean removed = removeMetaNodes(user, key);
        if (removed) api.getUserManager().saveUser(user);
        return removed;
    }

    private boolean removeMetaNodes(User user, String key) {
        Set<MetaNode> toRemove = user.getNodes(NodeType.META).stream()
                .filter(n -> n.getMetaKey().equalsIgnoreCase(key))
                .collect(Collectors.toSet());
        for (MetaNode node : toRemove) {
            user.data().remove(node);
        }
        return !toRemove.isEmpty();
    }
}
