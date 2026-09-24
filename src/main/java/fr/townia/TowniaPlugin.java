package fr.townia;

import fr.townia.command.TowniaCommand;
import fr.townia.gui.CommerceMenu;
import fr.townia.gui.VillageMenu;
import fr.townia.gui.WorldMenu;
import fr.townia.listener.TowniaListener;
import fr.townia.service.ActivityService;
import fr.townia.service.SkinService;
import fr.townia.service.VillageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;

public final class TowniaPlugin extends JavaPlugin {
    private static final Set<String> DEFAULT_WORLD_NAMES = Set.of("world", "world_nether", "world_the_end");

    private VillageManager villages;
    private ActivityService activity;
    private SkinService skins;
    private VillageMenu villageMenu;
    private CommerceMenu commerceMenu;
    private WorldMenu worldMenu;
    private final Map<UUID, Location> originalLocations = new HashMap<>();
    private final Map<UUID, Long> lastCombatAt = new HashMap<>();
    private final Map<UUID, Map<String, InventorySnapshot>> inventorySnapshots = new HashMap<>();
    private File inventorySaveFile;

    public static boolean defaultWorldInventorySync() {
        return false;
    }

    public static boolean isDefaultWorldName(String worldName) {
        return worldName != null && DEFAULT_WORLD_NAMES.contains(worldName);
    }

    public static boolean isProtectedWorldForDeletion(String worldName) {
        return "world".equalsIgnoreCase(worldName);
    }

    public static boolean resolveWorldInventorySync(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    public static boolean shouldKeepInventory(boolean syncInventory) {
        return syncInventory;
    }

    public static boolean shouldSaveInventoryOnExit(boolean previousWorldSyncEnabled) {
        return true;
    }

    public static boolean shouldRestoreInventoryOnEnter(boolean nextWorldSyncEnabled) {
        return nextWorldSyncEnabled;
    }

    public static boolean shouldInitializeSavedWorldInventory(boolean worldSyncEnabled, boolean hasSavedSnapshot) {
        return worldSyncEnabled && !hasSavedSnapshot;
    }

    @Override public void onEnable() {
        this.inventorySaveFile = new File(getDataFolder(), "world-inventories.yml");
        saveDefaultConfig();
        ensureCustomWorldConfig();
        cleanupStaleWorldFolders();
        loadPersistedWorlds();
        loadSavedWorldInventories();
        villages = new VillageManager(new File(getDataFolder(), "data.yml"));
        villages.load();
        activity = new ActivityService(getConfig().getLong("afk-timeout-minutes", 15));
        skins = SkinService.disabled();
        TowniaCommand command = new TowniaCommand(this);
        getCommand("village").setExecutor(command);
        getCommand("claim").setExecutor(command);
        getCommand("unclaim").setExecutor(command);
        getCommand("sethome").setExecutor(command);
        getCommand("home").setExecutor(command);
        getCommand("delhome").setExecutor(command);
        getCommand("world").setExecutor(command);
        getCommand("event").setExecutor(command);
        getCommand("leave").setExecutor(command);
        getCommand("townia").setExecutor(command);
        getCommand("aide").setExecutor(command);
        getCommand("regles").setExecutor(command);
        getCommand("commerce").setExecutor(command);
        villageMenu = new VillageMenu(this);
        commerceMenu = new CommerceMenu(this);
        worldMenu = new WorldMenu(this);
        getServer().getPluginManager().registerEvents(villageMenu, this);
        getServer().getPluginManager().registerEvents(commerceMenu, this);
        getServer().getPluginManager().registerEvents(worldMenu, this);
        getServer().getPluginManager().registerEvents(new TowniaListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> getServer().getOnlinePlayers().forEach(activity::flush), 20L * 60, 20L * 60);
        printStartupBanner();
    }

    public void sendWelcomeMessage(Player player) {
        if (player == null || !player.hasPlayedBefore()) {
            return;
        }
    }

    public void sendFirstJoinGuide(Player player) {
        if (player == null) return;
        player.sendMessage(ChatColor.GOLD + "══════════════════════════════════════");
        player.sendMessage(ChatColor.GOLD + "Bienvenue sur Townia !");
        player.sendMessage(ChatColor.WHITE + "Townia est le système du serveur qui organise la vie sociale, la protection des terrains et la gestion des mondes.");
        player.sendMessage(ChatColor.WHITE + "L'idée est simple : crée ton village, protège tes zones, attribue des droits à tes membres et profite d'un serveur plus structuré et plus sûr.");
        player.sendMessage(ChatColor.YELLOW + "Pourquoi utiliser Townia ?");
        player.sendMessage(ChatColor.GRAY + "• les joueurs peuvent créer un village et le gérer ensemble");
        player.sendMessage(ChatColor.GRAY + "• chaque claim protège un territoire contre les constructions et interractions non autorisées");
        player.sendMessage(ChatColor.GRAY + "• les homes de village permettent de revenir facilement chez soi");
        player.sendMessage(ChatColor.GRAY + "• les mondes peuvent avoir des règles distinctes : PvP, PvE, inventaire, permissions et mode de jeu");
        player.sendMessage(ChatColor.YELLOW + "Comment ça marche en pratique ?");
        player.sendMessage(ChatColor.GRAY + "1. Ouvre le menu /village pour créer ton village ou rejoindre un village ouvert.");
        player.sendMessage(ChatColor.GRAY + "2. Utilise /claim pour revendiquer le chunk sur lequel tu te trouves.");
        player.sendMessage(ChatColor.GRAY + "3. Place un home dans un claim de ton village avec /sethome, puis retourne avec /home.");
        player.sendMessage(ChatColor.GRAY + "4. Gère les membres, les grades et les permissions depuis le menu village.");
        player.sendMessage(ChatColor.YELLOW + "Commandes utiles");
        player.sendMessage(ChatColor.YELLOW + "• /village" + ChatColor.WHITE + " : ouvre le menu principal du village");
        player.sendMessage(ChatColor.YELLOW + "• /claim" + ChatColor.WHITE + " : revendique le chunk actuel");
        player.sendMessage(ChatColor.YELLOW + "• /unclaim" + ChatColor.WHITE + " : retire le claim actuel si tu as le droit");
        player.sendMessage(ChatColor.YELLOW + "• /sethome" + ChatColor.WHITE + " : défini le home du village dans un claim protégé");
        player.sendMessage(ChatColor.YELLOW + "• /home" + ChatColor.WHITE + " : te téléporte au home du village");
        player.sendMessage(ChatColor.YELLOW + "• /event" + ChatColor.WHITE + " : te ramène vers le monde d'événement");
        player.sendMessage(ChatColor.YELLOW + "• /leave" + ChatColor.WHITE + " : retourne vers le monde principal quand le monde le permet");
        player.sendMessage(ChatColor.YELLOW + "• /aide" + ChatColor.WHITE + " : relance ce guide à tout moment");
        player.sendMessage(ChatColor.GRAY + "L'objectif global du plugin est de rendre le serveur plus vivant, plus organisé et plus facile à gérer pour les joueurs comme pour les admins.");
        player.sendMessage(ChatColor.GOLD + "══════════════════════════════════════");
    }

    public void sendAdminGuide(Player player) {
        if (player == null) return;
        player.sendMessage(ChatColor.DARK_RED + "══════════════════════════════════════");
        player.sendMessage(ChatColor.DARK_RED + "Mode administrateur Townia activé");
        player.sendMessage(ChatColor.WHITE + "Tu es maintenant en charge de la direction du serveur. Townia ne sert pas seulement aux villages : il organise aussi les mondes, leurs règles, leurs inventaires et leur sécurité.");
        player.sendMessage(ChatColor.WHITE + "Tu peux créer des mondes spécifiques, choisir si les joueurs gardent leur inventaire ou non, gérer les permissions par monde, et contrôler le comportement PvP/PvE, le mode de jeu, la protection et les accès.");
        player.sendMessage(ChatColor.RED + "Ce que tu peux gérer");
        player.sendMessage(ChatColor.GRAY + "• créer, modifier, sécuriser et supprimer des mondes via /world");
        player.sendMessage(ChatColor.GRAY + "• activer ou désactiver le PvP, le PvE et les modes de jeu selon le monde");
        player.sendMessage(ChatColor.GRAY + "• paramétrer les permissions de joueurs et le comportement de chaque dimension");
        player.sendMessage(ChatColor.GRAY + "• gérer les inventaires synchronisés entre mondes pour un jeu plus fluide ou plus strict");
        player.sendMessage(ChatColor.GRAY + "• contrôler le monde d'événement et le retour au monde principal");
        player.sendMessage(ChatColor.RED + "Commandes clés");
        player.sendMessage(ChatColor.RED + "• /world" + ChatColor.WHITE + " : ouvre l'interface de gestion des mondes");
        player.sendMessage(ChatColor.RED + "• /event" + ChatColor.WHITE + " : téléporte vers le monde d'événement");
        player.sendMessage(ChatColor.RED + "• /leave" + ChatColor.WHITE + " : permet de revenir au monde principal dans les conditions autorisées");
        player.sendMessage(ChatColor.RED + "• /aide admin" + ChatColor.WHITE + " : relance ce guide admin");
        player.sendMessage(ChatColor.RED + "• /townia reload" + ChatColor.WHITE + " : recharge la configuration du plugin");
        player.sendMessage(ChatColor.GRAY + "En résumé : Townia sert à faire fonctionner le serveur comme un environnement structuré, avec villages, territoires, règles de monde et organisation claire pour les joueurs.");
        player.sendMessage(ChatColor.DARK_RED + "══════════════════════════════════════");
    }

    public void openServerRuleBook(Player player) {
        if (player == null) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        meta.setTitle("Townia - Règlement");
        meta.setAuthor("Staff Townia");

        List<String> pages = new ArrayList<>();
        String intro = "TOWNIA\nRèglement officiel\n\n"
            + "Ce plugin a été développé par un deuxième année. Merci de ne pas prendre personnellement les bugs et de les signaler au staff.\n\n"
            + "Le serveur est avant tout une survie. Le but est de construire, d’échanger, de vivre ensemble et de jouer proprement.";
        pages.addAll(buildSafePages(intro));

        String rules = "Règles générales\n\n"
            + "1. Pas de grief : aucune destruction, sabotage ou vandalisme des constructions d’autrui.\n"
            + "2. Pas d’insultes, harcèlement, provocation ou discours toxique.\n"
            + "3. Pas de triche, hacks, dupes, abus de bug ou mod d’avantage.\n"
            + "4. Respectez les villages, claims et bases de chacun.\n"
            + "5. Les constructions doivent rester propres, lisibles et sans blocage injuste.\n"
            + "6. Le staff est là pour faire respecter le cadre et maintenir un environnement sain.";
        pages.addAll(buildSafePages(rules));

        String gameplay = "PvP, commerce et bon sens\n\n"
            + "7. Le PvP est autorisé uniquement dans les zones prévues à cet effet.\n"
            + "8. Le commerce est autorisé, mais pas l’arnaque, les faux échanges ni les abus.\n"
            + "9. Les fermes et automatisations doivent rester raisonnables pour éviter le lag.\n"
            + "10. Le chat doit rester propre, clair et respectueux.\n"
            + "11. Si vous trouvez un bug, signalez-le au staff.\n\n"
            + "Le serveur fonctionne grâce au respect collectif. Merci de jouer proprement pour le bien de tous.";
        pages.addAll(buildSafePages(gameplay));

        meta.setPages(pages);
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private List<String> buildSafePages(String text) {
        List<String> pages = new ArrayList<>();
        if (text == null || text.isBlank()) return pages;

        String[] paragraphs = text.split("\\n\\n");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String[] lines = paragraph.split("\\n");
            for (String line : lines) {
                if (current.length() + line.length() + 1 > 220) {
                    pages.add(current.toString());
                    current = new StringBuilder();
                }
                if (current.length() > 0) current.append("\n");
                current.append(line);
            }
            if (current.length() > 180) {
                pages.add(current.toString());
                current = new StringBuilder();
            }
        }

        if (current.length() > 0) {
            pages.add(current.toString());
        }

        return pages;
    }

    public void openFirstJoinRuleBook(Player player) {
        openServerRuleBook(player);
    }

    private void printStartupBanner() {
        String name = getDescription().getName();
        String version = getDescription().getVersion();
        String separator = "════════════════════════════════════════════════════════════════════";

        getLogger().info("");
        getLogger().info(separator);
        getLogger().info("  ████████╗ █████╗ ███╗   ██╗██╗██╗   ██╗███╗   ██╗");
        getLogger().info("  ╚══██╔══╝██╔══██╗████╗  ██║██║██║   ██║████╗  ██║");
        getLogger().info("     ██║   ███████║██╔██╗ ██║██║██║   ██║██╔██╗ ██║");
        getLogger().info("     ██║   ██╔══██║██║╚██╗██║██║██║   ██║██║╚██╗██║");
        getLogger().info("     ██║   ██║  ██║██║ ╚████║██║╚██████╔╝██║ ╚████║");
        getLogger().info("     ╚═╝   ╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝ ╚═════╝ ╚═╝  ╚═══╝");
        getLogger().info("                                                         ");
        getLogger().info("  " + name + " v" + version + " - enabled successfully");
        getLogger().info("  Villages, claims, permissions, roles and activity tracking are ready.");
        getLogger().info(separator);
        getLogger().info("");
    }

    @Override public void onDisable() {
        if (villages != null) villages.save();
        saveWorldInventories();
    }
    public VillageManager villages() { return villages; }
    public ActivityService activity() { return activity; }
    public SkinService skins() { return skins; }
    public VillageMenu villageMenu() { return villageMenu; }
    public CommerceMenu commerceMenu() { return commerceMenu; }
    public WorldMenu worldMenu() { return worldMenu; }

    public void rememberOriginalLocation(Player player) {
        if (player == null || player.hasPermission("townia.admin")) return;
        originalLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    private void cleanupStaleWorldFolders() {
        File root = Bukkit.getWorldContainer();
        if (root == null || !root.exists()) return;
        File[] entries = root.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory() && entry.getName().startsWith("_townia_stale_")) {
                deleteDirectory(entry);
            }
        }
    }

    private void ensureCustomWorldConfig() {
        ConfigurationSection section = getConfig().getConfigurationSection("worlds.custom");
        if (section == null) {
            getConfig().createSection("worlds.custom");
            saveConfig();
        }
    }

    private void loadPersistedWorlds() {
        ConfigurationSection worlds = getConfig().getConfigurationSection("worlds.custom");
        if (worlds == null) return;
        Object worldManager = getMultiverseWorldManager();
        for (String worldName : worlds.getKeys(false)) {
            if (Bukkit.getWorld(worldName) != null) continue;
            if (worldManager != null) {
                boolean loaded = invokeWorldManagerBoolean(worldManager, "loadWorld", worldName);
                if (!loaded) {
                    boolean created = invokeWorldManagerBoolean(worldManager, "addWorld", worldName,
                            org.bukkit.World.Environment.NORMAL, null, org.bukkit.WorldType.NORMAL, true, null);
                    if (!created) {
                        getLogger().warning("Multiverse failed to restore world '" + worldName + "' from config.");
                    }
                }
                continue;
            }
            WorldCreator creator = new WorldCreator(worldName)
                    .environment(org.bukkit.World.Environment.NORMAL)
                    .type(org.bukkit.WorldType.NORMAL);
            Bukkit.createWorld(creator);
        }
    }

    private Object getMultiverseWorldManager() {
        Plugin plugin = getServer().getPluginManager().getPlugin("Multiverse-Core");
        if (plugin == null) {
            return null;
        }
        return invokeMethod(plugin, "getMVWorldManager");
    }

    private Object invokeMethod(Object target, String methodName, Object... args) {
        if (target == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < args.length; index++) {
                Object arg = args[index];
                Class<?> parameterType = parameterTypes[index];
                if (arg == null) {
                    continue;
                }
                if (parameterType.isPrimitive()) {
                    if (arg instanceof Number number) {
                        compatible = true;
                        continue;
                    }
                    compatible = false;
                    break;
                }
                if (!parameterType.isInstance(arg) && !(arg instanceof Number && Number.class.isAssignableFrom(parameterType))) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) continue;
            try {
                return method.invoke(target, args);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                getLogger().log(Level.FINE, "Failed invoking Multiverse method " + methodName + ".", exception);
                return null;
            }
        }
        return null;
    }

    private boolean invokeWorldManagerBoolean(Object target, String methodName, Object... args) {
        Object result = invokeMethod(target, methodName, args);
        return result instanceof Boolean booleanResult && booleanResult;
    }

    public boolean isDefaultWorld(String worldName) {
        return isProtectedWorldForDeletion(worldName);
    }

    public boolean isCustomManagedWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return false;
        if (isDefaultWorldName(worldName)) return false;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;
        if (DEFAULT_WORLD_NAMES.contains(world.getName())) return false;
        return managedWorlds().contains(world) || customWorldNames().contains(world.getName());
    }

    public void registerCombat(Player player) {
        if (player == null) return;
        lastCombatAt.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public boolean canLeaveWorld(Player player) {
        if (player == null) return false;
        long lastCombat = lastCombatAt.getOrDefault(player.getUniqueId(), 0L);
        long now = System.currentTimeMillis();
        return now - lastCombat >= 30_000L;
    }

    public void returnPlayerToOrigin(Player player) {
        if (player == null) return;
        Location origin = originalLocations.get(player.getUniqueId());
        if (origin == null) {
            World fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
            if (fallback != null) player.teleport(fallback.getSpawnLocation());
            return;
        }
        if (origin.getWorld() != null) player.teleport(origin);
    }

    public void applyWorldInventoryPolicy(Player player, World previousWorld, World nextWorld) {
        if (player == null) return;
        if (previousWorld != null && previousWorld.equals(nextWorld)) return;

        if (previousWorld != null) {
            savePlayerWorldInventory(player, previousWorld);
        }
        if (nextWorld == null) return;

        boolean syncEnabled = isWorldInventorySyncEnabled(nextWorld.getName());
        InventorySnapshot snapshot = getSavedInventory(player.getUniqueId(), nextWorld.getName());
        if (syncEnabled) {
            if (snapshot != null) {
                snapshot.apply(player);
            } else {
                savePlayerWorldInventory(player, nextWorld);
            }
            return;
        }

        clearPlayerInventory(player);
        savePlayerWorldInventory(player, nextWorld);
    }

    public void savePlayerWorldInventory(Player player, World world) {
        if (player == null || world == null) return;
        inventorySnapshots.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(world.getName(), InventorySnapshot.capture(player));
        saveWorldInventories();
    }

    public InventorySnapshot getSavedInventory(UUID playerId, String worldName) {
        if (playerId == null || worldName == null || worldName.isBlank()) return null;
        Map<String, InventorySnapshot> worldSnapshots = inventorySnapshots.get(playerId);
        return worldSnapshots == null ? null : worldSnapshots.get(worldName);
    }

    public void clearPlayerInventory(Player player) {
        if (player == null) return;
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[0]);
        inventory.setExtraContents(new ItemStack[0]);
        player.setExp(0f);
        player.setLevel(0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setHealth(20d);
        player.setFireTicks(0);
        player.getEnderChest().clear();
        player.setArrowsInBody(0);
        player.setRemainingAir(player.getMaximumAir());
    }

    private void saveWorldInventories() {
        if (inventorySaveFile == null) return;
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, InventorySnapshot>> playerEntry : inventorySnapshots.entrySet()) {
            String playerKey = playerEntry.getKey().toString();
            for (Map.Entry<String, InventorySnapshot> worldEntry : playerEntry.getValue().entrySet()) {
                String worldKey = worldEntry.getKey();
                config.set(playerKey + "." + worldKey, worldEntry.getValue().serialize());
            }
        }
        try {
            config.save(inventorySaveFile);
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Could not save world inventories.", exception);
        }
    }

    private void loadSavedWorldInventories() {
        if (inventorySaveFile == null || !inventorySaveFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(inventorySaveFile);
        for (String playerKey : config.getKeys(false)) {
            UUID playerId = UUID.fromString(playerKey);
            ConfigurationSection playerSection = config.getConfigurationSection(playerKey);
            if (playerSection == null) continue;
            Map<String, InventorySnapshot> worldSnapshots = new HashMap<>();
            for (String worldName : playerSection.getKeys(false)) {
                Object value = playerSection.get(worldName);
                if (!(value instanceof Map<?, ?> map)) continue;
                InventorySnapshot snapshot = InventorySnapshot.deserialize(map);
                if (snapshot != null) {
                    worldSnapshots.put(worldName, snapshot);
                }
            }
            if (!worldSnapshots.isEmpty()) {
                inventorySnapshots.put(playerId, worldSnapshots);
            }
        }
    }

    public static final class InventorySnapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack[] extra;
        private final int level;
        private final float xp;
        private final int foodLevel;
        private final float saturation;
        private final double health;
        private final int fireTicks;
        private final int air;
        private final int arrowsInBody;

        private InventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack[] extra,
                                 int level, float xp, int foodLevel, float saturation,
                                 double health, int fireTicks, int air, int arrowsInBody) {
            this.contents = contents;
            this.armor = armor;
            this.extra = extra;
            this.level = level;
            this.xp = xp;
            this.foodLevel = foodLevel;
            this.saturation = saturation;
            this.health = health;
            this.fireTicks = fireTicks;
            this.air = air;
            this.arrowsInBody = arrowsInBody;
        }

        public static InventorySnapshot capture(Player player) {
            PlayerInventory inventory = player.getInventory();
            return new InventorySnapshot(
                    inventory.getContents().clone(),
                    inventory.getArmorContents().clone(),
                    inventory.getExtraContents().clone(),
                    player.getLevel(),
                    player.getExp(),
                    player.getFoodLevel(),
                    player.getSaturation(),
                    player.getHealth(),
                    player.getFireTicks(),
                    player.getRemainingAir(),
                    player.getArrowsInBody()
            );
        }

        public void apply(Player player) {
            if (player == null) return;
            PlayerInventory inventory = player.getInventory();
            inventory.setContents(contents != null ? contents.clone() : new ItemStack[0]);
            inventory.setArmorContents(armor != null ? armor.clone() : new ItemStack[0]);
            inventory.setExtraContents(extra != null ? extra.clone() : new ItemStack[0]);
            player.setLevel(level);
            player.setExp(xp);
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            player.setHealth(Math.min(health, player.getMaxHealth()));
            player.setFireTicks(fireTicks);
            player.setRemainingAir(air);
            player.setArrowsInBody(arrowsInBody);
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();
            map.put("contents", serializeItems(contents));
            map.put("armor", serializeItems(armor));
            map.put("extra", serializeItems(extra));
            map.put("level", level);
            map.put("xp", xp);
            map.put("foodLevel", foodLevel);
            map.put("saturation", saturation);
            map.put("health", health);
            map.put("fireTicks", fireTicks);
            map.put("air", air);
            map.put("arrowsInBody", arrowsInBody);
            return map;
        }

        public static InventorySnapshot deserialize(Map<?, ?> map) {
            if (map == null) return null;
            ItemStack[] contents = deserializeItems((List<?>) map.get("contents"));
            ItemStack[] armor = deserializeItems((List<?>) map.get("armor"));
            ItemStack[] extra = deserializeItems((List<?>) map.get("extra"));
            return new InventorySnapshot(
                    contents,
                    armor,
                    extra,
                    map.get("level") instanceof Number number ? number.intValue() : 0,
                    map.get("xp") instanceof Number number ? number.floatValue() : 0f,
                    map.get("foodLevel") instanceof Number number ? number.intValue() : 20,
                    map.get("saturation") instanceof Number number ? number.floatValue() : 20f,
                    map.get("health") instanceof Number number ? number.doubleValue() : 20d,
                    map.get("fireTicks") instanceof Number number ? number.intValue() : 0,
                    map.get("air") instanceof Number number ? number.intValue() : 300,
                    map.get("arrowsInBody") instanceof Number number ? number.intValue() : 0
            );
        }

        private static List<Map<String, Object>> serializeItems(ItemStack[] items) {
            if (items == null) return Collections.emptyList();
            List<Map<String, Object>> list = new ArrayList<>();
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    list.add(Collections.emptyMap());
                    continue;
                }
                list.add(item.serialize());
            }
            return list;
        }

        private static ItemStack[] deserializeItems(List<?> items) {
            if (items == null) return new ItemStack[0];
            ItemStack[] result = new ItemStack[items.size()];
            for (int index = 0; index < items.size(); index++) {
                Object value = items.get(index);
                if (value instanceof Map<?, ?> map) {
                    result[index] = ItemStack.deserialize((Map<String, Object>) map);
                }
            }
            return result;
        }
    }

    public void returnEveryoneToOrigin() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("townia.admin")) continue;
            returnPlayerToOrigin(player);
        }
    }

    public World getEventWorld() {
        String name = getConfig().getString("event-world");
        if (name == null || name.isBlank()) return null;
        return Bukkit.getWorld(name);
    }

    public void setEventWorld(World world) {
        getConfig().set("event-world", world == null ? null : world.getName());
        saveConfig();
    }

    public boolean isInventorySyncEnabled() {
        return resolveWorldInventorySync(getConfig().getBoolean("worlds.sync-inventory", defaultWorldInventorySync()));
    }

    public boolean isWorldInventorySyncEnabled(String worldName) {
        if (worldName == null || worldName.isBlank()) return defaultWorldInventorySync();
        if (isDefaultWorldName(worldName)) return true;
        ConfigurationSection section = getConfig().getConfigurationSection("worlds.custom");
        if (section == null || !section.contains(worldName)) return defaultWorldInventorySync();
        ConfigurationSection worldSection = section.getConfigurationSection(worldName);
        if (worldSection == null) return defaultWorldInventorySync();
        return resolveWorldInventorySync(worldSection.getBoolean("sync-inventory", defaultWorldInventorySync()));
    }

    public void setWorldInventorySync(String worldName, boolean syncInventory) {
        if (worldName == null || worldName.isBlank()) return;
        ConfigurationSection customSection = getConfig().getConfigurationSection("worlds.custom");
        if (customSection == null) customSection = getConfig().createSection("worlds.custom");
        ConfigurationSection worldSection = customSection.getConfigurationSection(worldName);
        if (worldSection == null) worldSection = customSection.createSection(worldName);
        worldSection.set("sync-inventory", syncInventory);
        saveConfig();
    }

    public boolean isWorldLeaveEnabled(String worldName) {
        if (worldName == null || worldName.isBlank()) return true;
        ConfigurationSection section = getConfig().getConfigurationSection("worlds.custom");
        if (section == null || !section.contains(worldName)) return true;
        ConfigurationSection worldSection = section.getConfigurationSection(worldName);
        if (worldSection == null) return true;
        return worldSection.getBoolean("settings.leave-enabled", true);
    }

    public void setWorldLeaveEnabled(String worldName, boolean enabled) {
        if (worldName == null || worldName.isBlank()) return;
        ConfigurationSection customSection = getConfig().getConfigurationSection("worlds.custom");
        if (customSection == null) customSection = getConfig().createSection("worlds.custom");
        ConfigurationSection worldSection = customSection.getConfigurationSection(worldName);
        if (worldSection == null) worldSection = customSection.createSection(worldName);
        worldSection.set("settings.leave-enabled", enabled);
        saveConfig();
    }

    public boolean isWorldVillageEnabled(String worldName) {
        if (worldName == null || worldName.isBlank()) return true;
        ConfigurationSection section = getConfig().getConfigurationSection("worlds.custom");
        if (section == null || !section.contains(worldName)) return true;
        ConfigurationSection worldSection = section.getConfigurationSection(worldName);
        if (worldSection == null) return true;
        return worldSection.getBoolean("settings.village-enabled", true);
    }

    public void setWorldVillageEnabled(String worldName, boolean enabled) {
        if (worldName == null || worldName.isBlank()) return;
        ConfigurationSection customSection = getConfig().getConfigurationSection("worlds.custom");
        if (customSection == null) customSection = getConfig().createSection("worlds.custom");
        ConfigurationSection worldSection = customSection.getConfigurationSection(worldName);
        if (worldSection == null) worldSection = customSection.createSection(worldName);
        worldSection.set("settings.village-enabled", enabled);
        saveConfig();
    }

    public void registerWorld(String worldName, boolean syncInventory) {
        if (worldName == null || worldName.isBlank()) return;
        ConfigurationSection customSection = getConfig().getConfigurationSection("worlds.custom");
        if (customSection == null) customSection = getConfig().createSection("worlds.custom");
        ConfigurationSection worldSection = customSection.getConfigurationSection(worldName);
        if (worldSection == null) worldSection = customSection.createSection(worldName);
        worldSection.set("sync-inventory", syncInventory);
        if (!worldSection.contains("settings.leave-enabled")) {
            worldSection.set("settings.leave-enabled", true);
        }
        if (!worldSection.contains("settings.village-enabled")) {
            worldSection.set("settings.village-enabled", true);
        }
        saveConfig();
    }

    public List<String> customWorldNames() {
        List<String> names = new ArrayList<>();
        ConfigurationSection section = getConfig().getConfigurationSection("worlds.custom");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (key != null && !key.isBlank() && !DEFAULT_WORLD_NAMES.contains(key)) {
                    names.add(key);
                }
            }
        }
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if (!DEFAULT_WORLD_NAMES.contains(name) && !names.contains(name) && isCustomWorldFolder(name)) {
                names.add(name);
            }
        }
        return names;
    }

    public void setWorldGameMode(String worldName, GameMode gameMode) {
        if (worldName == null || worldName.isBlank() || gameMode == null) return;
        ConfigurationSection worldSection = getOrCreateWorldSection(worldName);
        worldSection.set("settings.gamemode", gameMode.name());
        saveConfig();
    }

    public GameMode getWorldGameMode(String worldName) {
        if (worldName == null || worldName.isBlank()) return GameMode.SURVIVAL;
        ConfigurationSection worldSection = getOrCreateWorldSection(worldName);
        String mode = worldSection.getString("settings.gamemode", "SURVIVAL");
        try {
            return GameMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return GameMode.SURVIVAL;
        }
    }

    public boolean isWorldActionAllowed(String worldName, Player player, String action) {
        if (worldName == null || worldName.isBlank() || player == null || action == null || action.isBlank()) {
            return true;
        }
        ConfigurationSection worldSection = getOrCreateWorldSection(worldName);
        ConfigurationSection defaults = worldSection.getConfigurationSection("settings.default");
        ConfigurationSection players = worldSection.getConfigurationSection("players");
        boolean defaultValue = defaults == null || !defaults.contains(action) || defaults.getBoolean(action, true);
        if (players == null) return defaultValue;
        ConfigurationSection playerSection = players.getConfigurationSection(player.getUniqueId().toString());
        if (playerSection == null) return defaultValue;
        return playerSection.getBoolean(action, defaultValue);
    }

    public void setWorldActionForPlayer(String worldName, UUID playerId, String action, boolean allowed) {
        if (worldName == null || worldName.isBlank() || playerId == null || action == null || action.isBlank()) return;
        ConfigurationSection worldSection = getOrCreateWorldSection(worldName);
        ConfigurationSection players = worldSection.getConfigurationSection("players");
        if (players == null) players = worldSection.createSection("players");
        ConfigurationSection playerSection = players.getConfigurationSection(playerId.toString());
        if (playerSection == null) playerSection = players.createSection(playerId.toString());
        playerSection.set(action, allowed);
        saveConfig();
    }

    public void setWorldDefaultAction(String worldName, String action, boolean allowed) {
        if (worldName == null || worldName.isBlank() || action == null || action.isBlank()) return;
        ConfigurationSection worldSection = getOrCreateWorldSection(worldName);
        ConfigurationSection defaults = worldSection.getConfigurationSection("settings.default");
        if (defaults == null) defaults = worldSection.createSection("settings.default");
        defaults.set(action, allowed);
        saveConfig();
    }

    public boolean isWorldDefaultAction(String worldName, String action) {
        if (worldName == null || worldName.isBlank() || action == null || action.isBlank()) return true;
        ConfigurationSection worldSection = getOrCreateWorldSection(worldName);
        ConfigurationSection defaults = worldSection.getConfigurationSection("settings.default");
        return defaults == null || defaults.getBoolean(action, true);
    }

    private ConfigurationSection getOrCreateWorldSection(String worldName) {
        if (worldName == null || worldName.isBlank()) return getConfig().createSection("worlds.custom");
        ConfigurationSection custom = getConfig().getConfigurationSection("worlds.custom");
        if (custom == null) custom = getConfig().createSection("worlds.custom");
        ConfigurationSection worldSection = custom.getConfigurationSection(worldName);
        if (worldSection == null) worldSection = custom.createSection(worldName);
        return worldSection;
    }

    private File resolveWorldFolder(String worldName) {
        if (worldName == null || worldName.isBlank()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world != null) return world.getWorldFolder();

        String normalizedName = worldName;
        if (normalizedName.contains(":")) {
            normalizedName = normalizedName.substring(normalizedName.lastIndexOf(':') + 1);
        }

        File root = Bukkit.getWorldContainer();
        File direct = new File(root, worldName);
        if (direct.exists() && direct.isDirectory()) return direct;

        File dimension = new File(root, "world/dimensions/minecraft/" + normalizedName);
        if (dimension.exists() && dimension.isDirectory()) return dimension;

        File legacyDimension = new File(root, "dimensions/minecraft/" + normalizedName);
        if (legacyDimension.exists() && legacyDimension.isDirectory()) return legacyDimension;

        return direct;
    }

    private boolean isCustomWorldFolder(String worldName) {
        if (worldName == null || DEFAULT_WORLD_NAMES.contains(worldName)) return false;
        File worldFolder = resolveWorldFolder(worldName);
        return worldFolder != null && worldFolder.exists() && worldFolder.isDirectory();
    }

    public List<World> managedWorlds() {
        List<World> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            if ("world_nether".equals(name) || "world_the_end".equals(name) || worlds.contains(world)) {
                continue;
            }
            worlds.add(world);
        }
        return worlds;
    }

    public void renameWorldConfig(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        ConfigurationSection section = getConfig().getConfigurationSection("worlds.custom");
        if (section == null || !section.contains(oldName)) return;
        ConfigurationSection oldSection = section.getConfigurationSection(oldName);
        boolean sync = oldSection != null && oldSection.getBoolean("sync-inventory", defaultWorldInventorySync());
        section.set(oldName, null);
        ConfigurationSection newSection = section.createSection(newName);
        newSection.set("sync-inventory", sync);
        saveConfig();
    }

    public void removeWorldConfig(String worldName) {
        ConfigurationSection section = getConfig().getConfigurationSection("worlds.custom");
        if (section == null) return;
        section.set(worldName, null);
        saveConfig();
    }

    public boolean createWorld(String name) {
        if (!Bukkit.isPrimaryThread()) {
            Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(this, () -> createWorldInternal(name));
            try {
                return future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                getLogger().log(Level.SEVERE, "Interrupted while creating world asynchronously.", exception);
                return false;
            } catch (ExecutionException exception) {
                getLogger().log(Level.SEVERE, "Could not create world on the main thread.", exception);
                return false;
            }
        }
        return createWorldInternal(name);
    }

    private boolean createWorldInternal(String name) {
        String safeName = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (safeName.isBlank()) return false;
        if (Bukkit.getWorld(safeName) != null) return false;

        ConfigurationSection customWorlds = getConfig().getConfigurationSection("worlds.custom");
        if (customWorlds != null && customWorlds.contains(safeName)) return false;

        Object worldManager = getMultiverseWorldManager();
        if (worldManager != null) {
            boolean created = invokeWorldManagerBoolean(worldManager, "addWorld", safeName,
                    org.bukkit.World.Environment.NORMAL, null, org.bukkit.WorldType.NORMAL, true, null);
            if (created) {
                registerWorld(safeName, defaultWorldInventorySync());
                return true;
            }
            return false;
        }

        World created = Bukkit.createWorld(new WorldCreator(safeName)
                .environment(org.bukkit.World.Environment.NORMAL)
                .type(org.bukkit.WorldType.NORMAL));
        if (created == null) return false;

        registerWorld(created.getName(), defaultWorldInventorySync());
        return true;
    }

    public boolean renameWorld(World world, String newName) {
        if (!Bukkit.isPrimaryThread()) {
            Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(this, () -> renameWorldInternal(world, newName));
            try {
                return future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                getLogger().log(Level.SEVERE, "Interrupted while renaming world asynchronously.", exception);
                return false;
            } catch (ExecutionException exception) {
                getLogger().log(Level.SEVERE, "Could not rename world on the main thread.", exception);
                return false;
            }
        }
        return renameWorldInternal(world, newName);
    }

    private boolean renameWorldInternal(World world, String newName) {
        if (world == null) return false;
        String safeName = newName == null ? "" : newName.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (safeName.isBlank() || safeName.equalsIgnoreCase(world.getName())) return false;

        Object worldManager = getMultiverseWorldManager();
        if (worldManager != null) {
            boolean renamed = invokeWorldManagerBoolean(worldManager, "renameWorld", world.getName(), safeName);
            if (renamed) {
                renameWorldConfig(world.getName(), safeName);
                if (Objects.equals(getConfig().getString("event-world"), world.getName())) {
                    setEventWorld(Bukkit.getWorld(safeName));
                }
                return true;
            }
            return false;
        }

        if (Bukkit.getWorld(safeName) != null) return false;
        File from = world.getWorldFolder();
        File to = new File(from.getParentFile() == null ? Bukkit.getWorldContainer() : from.getParentFile(), safeName);
        if (to.exists()) return false;
        Bukkit.unloadWorld(world, false);
        try {
            java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            return false;
        }
        World renamed = Bukkit.createWorld(new WorldCreator(safeName));
        if (renamed == null) return false;
        renameWorldConfig(world.getName(), safeName);
        if (Objects.equals(getConfig().getString("event-world"), world.getName())) setEventWorld(renamed);
        return true;
    }

    public boolean deleteWorld(World world) {
        if (!Bukkit.isPrimaryThread()) {
            Future<Boolean> future = Bukkit.getScheduler().callSyncMethod(this, () -> deleteWorldInternal(world));
            try {
                return future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                getLogger().log(Level.SEVERE, "Interrupted while deleting world asynchronously.", exception);
                return false;
            } catch (ExecutionException exception) {
                getLogger().log(Level.SEVERE, "Could not delete world on the main thread.", exception);
                return false;
            }
        }
        return deleteWorldInternal(world);
    }

    private boolean deleteWorldInternal(World world) {
        if (world == null || isProtectedWorldForDeletion(world.getName())) return false;
        if (!world.getPlayers().isEmpty()) {
            String message = ChatColor.RED + "[Townia] Impossible de supprimer le monde " + world.getName() + " : des joueurs sont encore dedans.";
            for (Player player : world.getPlayers()) {
                player.sendMessage(message);
            }
            return false;
        }

        Object worldManager = getMultiverseWorldManager();
        if (worldManager != null) {
            boolean deleted = invokeWorldManagerBoolean(worldManager, "deleteWorld", world.getName(), true, true)
                    || invokeWorldManagerBoolean(worldManager, "deleteWorld", world.getName(), true)
                    || invokeWorldManagerBoolean(worldManager, "deleteWorld", world.getName());
            if (deleted) {
                if (Objects.equals(getConfig().getString("event-world"), world.getName())) setEventWorld(null);
                removeWorldConfig(world.getName());
                return true;
            }
        }

        Bukkit.unloadWorld(world, true);
        File folder = resolveWorldFolder(world.getName());
        if (folder == null) return false;
        boolean deleted = deleteDirectory(folder);
        if (deleted) {
            if (Objects.equals(getConfig().getString("event-world"), world.getName())) setEventWorld(null);
            removeWorldConfig(world.getName());
        }
        return deleted;
    }

    private boolean deleteDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return directory != null && directory.delete();
        }
        try {
            Path root = directory.toPath();
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(file -> {
                        if (!file.delete() && file.exists()) {
                            throw new IllegalStateException("Could not delete " + file.getAbsolutePath());
                        }
                    });
            return !directory.exists();
        } catch (Exception exception) {
            getLogger().log(Level.WARNING, "Unable to delete world folder: " + directory.getAbsolutePath(), exception);
            return false;
        }
    }

    public void teleportAllNonAdminsToEvent() {
        World eventWorld = getEventWorld();
        if (eventWorld == null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("townia.admin")) player.sendMessage("§cAucun event pour le moment.");
            }
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("townia.admin")) continue;
            rememberOriginalLocation(player);
            player.teleport(eventWorld.getSpawnLocation());
        }
    }

    public void returnAllNonAdminsToOrigin() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("townia.admin")) continue;
            returnPlayerToOrigin(player);
        }
    }
}
