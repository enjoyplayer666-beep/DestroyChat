package ru.dscraft.mediabans;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.query.QueryOptions;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Группы игрока не в сети. Класс трогается только если LuckPerms установлен. */
final class LuckPermsHook {

    private LuckPermsHook() {
    }

    static CompletableFuture<Boolean> inAnyGroup(UUID uuid, Collection<String> groups) {
        return LuckPermsProvider.get().getUserManager().loadUser(uuid).thenApply(user -> {
            for (Group group : user.getInheritedGroups(QueryOptions.nonContextual())) {
                if (groups.contains(group.getName().toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        });
    }
}
