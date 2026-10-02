package ru.example.vanisheffects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * VanishEffects — весь плагин в одном файле, чтобы не было путаницы с несколькими классами.
 *
 * /v                    — включить/выключить ваниш (со стилем, который использовался последним)
 * /v menu                — открыть меню выбора стиля (GUI)
 * /v fire|bats|lightning|starfall|frost|soul|ascension [игрок] — включить ваниш с конкретным стилем
 *
 * Кто может пользоваться (config.yml, groups): опы - всё (меню, любой эффект, по умолчанию молния);
 * группы из конфига - только /v со своим эффектом без выбора (none - без эффекта); остальным команды нет.
 */
public class VanishEffectsPlugin extends ru.dscraft.destroychat.module.Module implements CommandExecutor, TabCompleter, Listener {

    private static final Random RANDOM = new Random();
    private static final int BAT_COUNT = 38;
    private static final int BAT_CIRCLE_TICKS = 16;
    private static final int BAT_FLYAWAY_TICKS = 22;

    private enum Style {
        FIRE("Огонь"), BATS("Летучие мыши"), LIGHTNING("Молния"), STARFALL("Звездопад"),
        FROST("Иней"), SOUL("Душа"), ASCENSION("Вознесение"),
        /** Обычный ваниш без эффекта (не показывается в меню и подсказках). */
        NONE("Без эффекта");

        final String displayName;

        Style(String displayName) {
            this.displayName = displayName;
        }
    }

    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, Style> lastStyle = new HashMap<>();
    /** Группа LuckPerms -> её эффект; порядок как в конфиге, берётся первая подходящая. */
    private final Map<String, Style> groupStyles = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadGroups();
        getCommand("v").setExecutor(this);
        getCommand("v").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("========================================");
        getLogger().info(" VanishEffects включён! Команда: /v");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        for (UUID id : new HashSet<>(vanished)) {
            Player p = getServer().getPlayer(id);
            if (p != null) {
                showToOthers(p);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }
        vanished.clear();
    }

    private void loadGroups() {
        groupStyles.clear();
        org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection("groups");
        if (section == null) return;
        for (String group : section.getKeys(false)) {
            Style style = parseAnyStyle(section.getString(group, "none"));
            if (style == null) {
                getLogger().warning("Неизвестный эффект у группы " + group + ": " + section.getString(group));
                continue;
            }
            groupStyles.put(group.toLowerCase(Locale.ROOT), style);
        }
    }

    /** Эффект, закреплённый за группой игрока; null - группы с ванишем нет. */
    private Style groupStyle(Player player) {
        for (Map.Entry<String, Style> e : groupStyles.entrySet()) {
            if (player.hasPermission("group." + e.getKey())) return e.getValue();
        }
        return null;
    }

    /** Есть ли у отправителя ваниш вообще: оп или группа из конфига. */
    private boolean canVanish(CommandSender sender) {
        if (sender.isOp()) return true;
        return sender instanceof Player && groupStyle((Player) sender) != null;
    }

    /** Без ваниша команда /v не видна в подсказках. */
    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (canVanish(event.getPlayer())) return;
        event.getCommands().removeIf(c -> c.equalsIgnoreCase("v") || c.equalsIgnoreCase("vanisheffects:v"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        if (joined.hasPermission("vanisheffects.see")) {
            return;
        }
        for (UUID id : vanished) {
            Player vp = getServer().getPlayer(id);
            if (vp != null && !vp.equals(joined)) {
                joined.hidePlayer(this, vp);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && sender.isOp()) {
            reloadConfig();
            loadGroups();
            sender.sendMessage("§aVanishEffects: конфиг перезагружен.");
            return true;
        }
        if (!canVanish(sender)) {
            sender.sendMessage(getConfig().getString("no-command-message", "§fНет такой команды :/"));
            return true;
        }
        // группы без выбора: /v включает/выключает ваниш только их эффектом
        if (!sender.isOp()) {
            Player player = (Player) sender;
            if (args.length > 0) {
                player.sendMessage("§cТебе доступен только §f/v§c - выбор эффекта есть только у опов.");
                return true;
            }
            Style fixed = groupStyle(player);
            boolean nowVanished;
            if (vanished.contains(player.getUniqueId())) {
                disable(player);
                nowVanished = false;
            } else {
                enable(player, fixed);
                nowVanished = true;
            }
            player.sendMessage(vanishStatusMessage(nowVanished, player.getName()));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cМеню доступно только игрокам.");
                return true;
            }
            Player menuPlayer = (Player) sender;
            openStyleMenu(menuPlayer);
            return true;
        }

        Style style = args.length >= 1 ? parseStyle(args[0]) : null;
        String targetName = args.length >= 2 ? args[1] : null;

        Player target;
        if (targetName != null) {
            if (!sender.isOp()) {
                sender.sendMessage("§cУ вас нет прав применять эту команду к другим игрокам.");
                return true;
            }
            target = getServer().getPlayerExact(targetName);
            if (target == null) {
                sender.sendMessage("§cИгрок не найден: " + targetName);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cУкажите игрока: /v <fire|bats|lightning> <игрок>");
                return true;
            }
            target = (Player) sender;
        }

        if (args.length >= 1 && style == null) {
            sender.sendMessage("§cНеизвестный эффект. Доступно: menu, fire, bats, lightning, starfall, frost, soul, ascension");
            return true;
        }

        if (style != null) {
            enable(target, style);
            sender.sendMessage(vanishStatusMessage(true, target.getName()));
        } else {
            boolean nowVanished = toggle(target);
            sender.sendMessage(vanishStatusMessage(nowVanished, target.getName()));
        }

        return true;
    }

    /**
     * Сообщение о статусе невидимости в формате:
     * "⚑ Невидимость · Невидимость Включено/Отключено для <игрок>"
     * (без пояснительной средней строки).
     */
    private String vanishStatusMessage(boolean enabled, String playerName) {
        org.bukkit.ChatColor statusColor = enabled ? org.bukkit.ChatColor.GREEN : org.bukkit.ChatColor.RED;
        String status = enabled ? "Включено" : "Отключено";

        return "" + org.bukkit.ChatColor.LIGHT_PURPLE + "⚑ " +
                org.bukkit.ChatColor.LIGHT_PURPLE + org.bukkit.ChatColor.BOLD + "Невидимость " +
                org.bukkit.ChatColor.GRAY + "· " +
                statusColor + status + " " +
                org.bukkit.ChatColor.GRAY + "для " +
                org.bukkit.ChatColor.GRAY + playerName;
    }

    // ================= Меню выбора стиля =================

    /** Маркер-холдер, чтобы отличать инвентарь меню ваниша от любых других открытых инвентарей. */
    private static class VanishMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    private void openStyleMenu(Player player) {
        VanishMenuHolder holder = new VanishMenuHolder();
        Inventory menu = Bukkit.createInventory(holder, 9, gradient("Стиль ваниша", "B784FF", "5A2EDB", true, true));
        holder.setInventory(menu);

        Style[] styles = menuStyles();
        for (int slot = 0; slot < styles.length; slot++) {
            Style style = styles[slot];
            ItemStack icon = new ItemStack(styleIcon(style));
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(gradient(style.displayName, styleGradientStart(style), styleGradientEnd(style), true, true));
                meta.setLore(Arrays.asList(org.bukkit.ChatColor.GRAY + "Нажми, чтобы включить"));
                icon.setItemMeta(meta);
            }
            menu.setItem(slot, icon);
        }

        player.openInventory(menu);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof VanishMenuHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        if (!player.isOp()) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        Style[] styles = menuStyles();
        if (slot < 0 || slot >= styles.length) {
            return;
        }

        Style style = styles[slot];
        player.closeInventory();
        enable(player, style);
        player.sendMessage(vanishStatusMessage(true, player.getName()));
    }

    /** Эффекты в меню и в /v <эффект> - все, кроме "без эффекта". */
    private static Style[] menuStyles() {
        return Arrays.stream(Style.values()).filter(s -> s != Style.NONE).toArray(Style[]::new);
    }

    private Material styleIcon(Style style) {
        switch (style) {
            case FIRE:
                return Material.FIRE_CHARGE;
            case BATS:
                return Material.BAT_SPAWN_EGG;
            case LIGHTNING:
                return Material.LIGHTNING_ROD;
            case STARFALL:
                return Material.FIREWORK_ROCKET;
            case FROST:
                return Material.BLUE_ICE;
            case SOUL:
                return Material.SOUL_LANTERN;
            case ASCENSION:
                return Material.DRAGON_HEAD;
            default:
                return Material.NETHER_STAR;
        }
    }

    private String styleGradientStart(Style style) {
        switch (style) {
            case FIRE:
                return "FFB000";
            case BATS:
                return "6A11CB";
            case LIGHTNING:
                return "FDE910";
            case STARFALL:
                return "FFD700";
            case FROST:
                return "00E5FF";
            case SOUL:
                return "1CD3D3";
            case ASCENSION:
                return "8E2DE2";
            default:
                return "FFFFFF";
        }
    }

    private String styleGradientEnd(Style style) {
        switch (style) {
            case FIRE:
                return "FF1E1E";
            case BATS:
                return "120024";
            case LIGHTNING:
                return "00B4FF";
            case STARFALL:
                return "FFFFFF";
            case FROST:
                return "FFFFFF";
            case SOUL:
                return "072B3D";
            case ASCENSION:
                return "FF00C8";
            default:
                return "AAAAAA";
        }
    }

    /** Красит текст по буквам плавным градиентом между двумя hex-цветами, с жирным и курсивом на каждой букве. */
    private static String gradient(String text, String startHex, String endHex, boolean bold, boolean italic) {
        int length = text.length();
        int r1 = Integer.parseInt(startHex.substring(0, 2), 16);
        int g1 = Integer.parseInt(startHex.substring(2, 4), 16);
        int b1 = Integer.parseInt(startHex.substring(4, 6), 16);
        int r2 = Integer.parseInt(endHex.substring(0, 2), 16);
        int g2 = Integer.parseInt(endHex.substring(2, 4), 16);
        int b2 = Integer.parseInt(endHex.substring(4, 6), 16);

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            double t = length == 1 ? 0 : (double) i / (length - 1);
            int r = (int) Math.round(r1 + (r2 - r1) * t);
            int g = (int) Math.round(g1 + (g2 - g1) * t);
            int b = (int) Math.round(b1 + (b2 - b1) * t);

            result.append(net.md_5.bungee.api.ChatColor.of(new java.awt.Color(r, g, b)));
            if (bold) {
                result.append(org.bukkit.ChatColor.BOLD);
            }
            if (italic) {
                result.append(org.bukkit.ChatColor.ITALIC);
            }
            result.append(text.charAt(i));
        }
        return result.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp()) {
            return new ArrayList<>();
        }
        if (args.length == 1) {
            return Arrays.asList("menu", "fire", "bats", "lightning", "starfall", "frost", "soul", "ascension");
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (Player p : getServer().getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }
        return new ArrayList<>();
    }

    private Style parseStyle(String arg) {
        for (Style s : menuStyles()) {
            if (s.name().equalsIgnoreCase(arg)) {
                return s;
            }
        }
        return null;
    }

    /** Эффект из конфига: fire, bats, ..., none. */
    private static Style parseAnyStyle(String arg) {
        for (Style s : Style.values()) {
            if (s.name().equalsIgnoreCase(arg.trim())) {
                return s;
            }
        }
        return null;
    }

    // ================= Ваниш-логика =================

    private boolean toggle(Player player) {
        if (vanished.contains(player.getUniqueId())) {
            disable(player);
            return false;
        } else {
            enable(player, lastStyle.getOrDefault(player.getUniqueId(), Style.LIGHTNING));
            return true;
        }
    }

    private void enable(Player player, Style style) {
        UUID id = player.getUniqueId();
        lastStyle.put(id, style);
        boolean wasVanished = vanished.contains(id);
        vanished.add(id);

        playVanishEffect(player, style);

        if (!wasVanished) {
            hideFromOthers(player);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
    }

    private void disable(Player player) {
        UUID id = player.getUniqueId();
        if (vanished.remove(id)) {
            showToOthers(player);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);

            Style style = lastStyle.get(id);
            if (style == Style.FIRE) {
                playFireEffect(player);
            } else if (style == Style.LIGHTNING) {
                playLightningEffect(player);
            } else if (style == Style.BATS) {
                playBatsEffect(player);
            } else if (style == Style.STARFALL) {
                playStarfallEffect(player);
            } else if (style == Style.FROST) {
                playFrostEffect(player);
            } else if (style == Style.SOUL) {
                playSoulEffect(player);
            } else if (style == Style.ASCENSION) {
                playAscensionEffect(player);
            }
        }
    }

    private void hideFromOthers(Player player) {
        for (Player online : getServer().getOnlinePlayers()) {
            if (!online.equals(player) && !online.hasPermission("vanisheffects.see")) {
                online.hidePlayer(this, player);
            }
        }
    }

    private void showToOthers(Player player) {
        for (Player online : getServer().getOnlinePlayers()) {
            online.showPlayer(this, player);
        }
    }

    // ================= Визуальные эффекты =================

    private void playVanishEffect(Player player, Style style) {
        switch (style) {
            case FIRE:
                playFireEffect(player);
                break;
            case BATS:
                playBatsEffect(player);
                break;
            case LIGHTNING:
                playLightningEffect(player);
                break;
            case STARFALL:
                playStarfallEffect(player);
                break;
            case FROST:
                playFrostEffect(player);
                break;
            case SOUL:
                playSoulEffect(player);
                break;
            case ASCENSION:
                playAscensionEffect(player);
                break;
        }
    }

    private void playFireEffect(Player player) {
        World world = player.getWorld();
        Location initialLoc = player.getLocation().add(0, 1, 0);
        world.playSound(initialLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        org.bukkit.plugin.Plugin plugin = this;
        new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = 6;

            @Override
            public void run() {
                if (tick >= totalTicks || !player.isOnline()) {
                    cancel();
                    return;
                }
                Location loc = player.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.FLAME, loc, 15, 0.5, 0.9, 0.5, 0.04);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playLightningEffect(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        int range = Math.max(16, getConfig().getInt("lightning-radius", 100));
        world.strikeLightningEffect(loc);
        Particle.ELECTRIC_SPARK.builder().location(loc.clone().add(0, 1, 0)).count(200)
                .offset(1.2, 1.7, 1.2).extra(0.12).receivers(range).force(true).spawn();
        Particle.FLASH.builder().location(loc.clone().add(0, 1, 0)).count(3)
                .receivers(range).force(true).spawn();
        double rangeSq = (double) range * range;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) > rangeSq) continue;
            p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, range / 16f, 1.0f);
            p.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, range / 16f, 1.0f);
        }

        int extraStrikes = 8;
        org.bukkit.plugin.Plugin plugin = this;
        new BukkitRunnable() {
            int i = 0;

            @Override
            public void run() {
                if (i >= extraStrikes) {
                    cancel();
                    return;
                }
                double angle = 2 * Math.PI * i / extraStrikes;
                double radius = 1.8 + RANDOM.nextDouble() * 2.2;
                double x = loc.getX() + radius * Math.cos(angle);
                double z = loc.getZ() + radius * Math.sin(angle);
                Location strikeLoc = new Location(world, x, loc.getY(), z);
                world.strikeLightningEffect(strikeLoc);
                Particle.ELECTRIC_SPARK.builder().location(strikeLoc.clone().add(0, 1, 0)).count(50)
                        .offset(0.5, 0.7, 0.5).extra(0.05).receivers(range).force(true).spawn();
                i++;
            }
        }.runTaskTimer(plugin, 3L, 3L);
    }

    /** Стая летучих мышей поднимается вокруг игрока, кружит, а затем красиво разлетается в разные стороны. */
    private void playBatsEffect(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1, 0);

        world.playSound(center, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 0.8f);
        world.playSound(center, Sound.ENTITY_BAT_AMBIENT, 1.0f, 1.0f);

        List<Bat> bats = new ArrayList<>();
        List<Vector> flyDirections = new ArrayList<>();
        for (int i = 0; i < BAT_COUNT; i++) {
            double angle = 2 * Math.PI * i / BAT_COUNT;
            double radius = 0.8 + RANDOM.nextDouble() * 0.6;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            double y = center.getY() + (RANDOM.nextDouble() - 0.5) * 0.4;

            Bat bat = world.spawn(new Location(world, x, y, z), Bat.class);
            bat.setAwake(true);
            bat.setCustomNameVisible(false);
            bats.add(bat);

            double flyAngle = angle + (RANDOM.nextDouble() - 0.5) * 0.6;
            flyDirections.add(new Vector(Math.cos(flyAngle), 0.3 + RANDOM.nextDouble() * 0.3, Math.sin(flyAngle)));
        }

        org.bukkit.plugin.Plugin plugin = this;
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                tick++;
                if (tick <= BAT_CIRCLE_TICKS) {
                    for (Bat bat : bats) {
                        if (!bat.isValid()) continue;
                        Vector outward = bat.getLocation().toVector().subtract(center.toVector());
                        if (outward.lengthSquared() < 1.0E-4) {
                            outward = new Vector(RANDOM.nextDouble() - 0.5, 0, RANDOM.nextDouble() - 0.5);
                        }
                        bat.setVelocity(outward.normalize().multiply(0.15).add(new Vector(0, 0.08, 0)));
                    }
                } else {
                    double progress = (double) (tick - BAT_CIRCLE_TICKS) / BAT_FLYAWAY_TICKS;
                    double speed = 0.25 + progress * 0.55;
                    for (int i = 0; i < bats.size(); i++) {
                        Bat bat = bats.get(i);
                        if (!bat.isValid()) continue;
                        bat.setVelocity(flyDirections.get(i).clone().multiply(speed));
                        bat.getWorld().spawnParticle(Particle.PORTAL, bat.getLocation(), 2, 0.05, 0.05, 0.05, 0.01);
                    }
                }
                if (tick >= BAT_CIRCLE_TICKS + BAT_FLYAWAY_TICKS) {
                    for (Bat bat : bats) {
                        if (bat.isValid()) {
                            bat.getWorld().spawnParticle(Particle.PORTAL, bat.getLocation(), 12, 0.15, 0.15, 0.15, 0.05);
                            bat.remove();
                        }
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Светящийся метеор падает с неба на игрока и в момент удара взрывается фейерверком из искр. */
    private void playStarfallEffect(Player player) {
        World world = player.getWorld();
        Location target = player.getLocation().add(0, 1, 0);
        Location start = target.clone().add(0, 14, 0);
        Vector path = target.clone().subtract(start).toVector();

        world.playSound(target, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.8f);

        org.bukkit.plugin.Plugin plugin = this;
        new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = 12;

            @Override
            public void run() {
                if (tick > totalTicks) {
                    cancel();
                    return;
                }
                double progress = (double) tick / totalTicks;
                Location current = start.clone().add(path.clone().multiply(progress));
                world.spawnParticle(Particle.END_ROD, current, 4, 0.08, 0.08, 0.08, 0.01);
                world.spawnParticle(Particle.FLAME, current, 2, 0.05, 0.05, 0.05, 0.01);

                if (tick == totalTicks) {
                    world.spawnParticle(Particle.FIREWORK, target, 90, 0.6, 0.8, 0.6, 0.18);
                    world.spawnParticle(Particle.END_ROD, target, 40, 0.5, 0.6, 0.5, 0.05);
                    world.playSound(target, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.1f);
                    world.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.4f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Игрока за долю секунды затягивает инеем, после чего ледяная корка со звоном разлетается вдребезги. */
    private void playFrostEffect(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation().add(0, 1, 0);
        world.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.8f);

        org.bukkit.plugin.Plugin plugin = this;
        new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = 5;

            @Override
            public void run() {
                if (tick > totalTicks) {
                    world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.9f);
                    world.spawnParticle(Particle.SNOWFLAKE, center, 100, 0.6, 1.0, 0.6, 0.15);
                    world.spawnParticle(Particle.ITEM_SNOWBALL, center, 30, 0.5, 0.9, 0.5, 0.08);
                    cancel();
                    return;
                }
                double radius = 0.3 + tick * 0.25;
                for (int i = 0; i < 10; i++) {
                    double angle = 2 * Math.PI * i / 10;
                    double x = center.getX() + radius * Math.cos(angle);
                    double z = center.getZ() + radius * Math.sin(angle);
                    world.spawnParticle(Particle.SNOWFLAKE, new Location(world, x, center.getY(), z), 2, 0.02, 0.05, 0.02, 0.01);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Тёмная спираль из частиц душ поднимается вокруг игрока и завершается тихим потусторонним всплеском. */
    private void playSoulEffect(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation();
        world.playSound(base, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.6f);

        org.bukkit.plugin.Plugin plugin = this;
        new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = 14;

            @Override
            public void run() {
                if (tick > totalTicks) {
                    world.spawnParticle(Particle.SOUL, base.clone().add(0, 1, 0), 60, 0.4, 0.6, 0.4, 0.1);
                    world.playSound(base, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.7f);
                    cancel();
                    return;
                }
                double angle = tick * 0.9;
                double height = tick * 0.15;
                double radius = 0.5;
                double x1 = base.getX() + radius * Math.cos(angle);
                double z1 = base.getZ() + radius * Math.sin(angle);
                double x2 = base.getX() - radius * Math.cos(angle);
                double z2 = base.getZ() - radius * Math.sin(angle);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, new Location(world, x1, base.getY() + height, z1), 2, 0.02, 0.02, 0.02, 0.01);
                world.spawnParticle(Particle.SOUL, new Location(world, x2, base.getY() + height, z2), 2, 0.02, 0.02, 0.02, 0.01);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Кинематографичный эффект "Вознесение" в три акта:
     * 1) вокруг игрока по спирали поднимается драконье дыхание,
     * 2) рык дракона + взрыв, ударная кольцевая волна по земле и столб света в небо,
     * 3) финальный залп искр фейерверка.
     */
    private void playAscensionEffect(Player player) {
        World world = player.getWorld();
        Location base = player.getLocation().add(0, 1, 0);
        world.playSound(base, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.8f);

        org.bukkit.plugin.Plugin plugin = this;
        new BukkitRunnable() {
            int tick = 0;
            final int riseTicks = 20;

            @Override
            public void run() {
                if (tick > riseTicks) {
                    world.playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    world.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.1f);

                    for (int i = 0; i < 36; i++) {
                        double angle = 2 * Math.PI * i / 36;
                        double x = base.getX() + 3.0 * Math.cos(angle);
                        double z = base.getZ() + 3.0 * Math.sin(angle);
                        world.spawnParticle(Particle.ENCHANTED_HIT, new Location(world, x, base.getY() - 1, z), 3, 0.1, 0.1, 0.1, 0.02);
                    }

                    for (int h = 0; h < 12; h++) {
                        world.spawnParticle(Particle.END_ROD, base.clone().add(0, h * 0.6, 0), 4, 0.1, 0.1, 0.1, 0.01);
                    }

                    world.spawnParticle(Particle.FIREWORK, base, 120, 0.7, 1.0, 0.7, 0.25);
                    world.playSound(base, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
                    cancel();
                    return;
                }

                double progress = (double) tick / riseTicks;
                double height = progress * 2.2;
                double radius = 0.6;
                for (int arm = 0; arm < 3; arm++) {
                    double angle = tick * 0.5 + arm * (2 * Math.PI / 3);
                    double x = base.getX() + radius * Math.cos(angle);
                    double z = base.getZ() + radius * Math.sin(angle);
                    world.spawnParticle(Particle.DRAGON_BREATH, new Location(world, x, base.getY() + height, z), 3, 0.03, 0.03, 0.03, 0.01);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
