package fr.townia;

import fr.townia.command.TowniaCommand;
import fr.townia.gui.VillageMenu;
import fr.townia.gui.WorldMenu;
import fr.townia.listener.TowniaListener;
import fr.townia.service.ActivityService;
import fr.townia.service.SkinService;
import fr.townia.service.VillageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class TowniaPlugin extends JavaPlugin {
    private VillageManager villages;
    private ActivityService activity;
    private SkinService skins;
    private VillageMenu villageMenu;
    private WorldMenu worldMenu;
    private final Map<UUID, Location> originalLocations = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        villages = new VillageManager(new File(getDataFolder(), "data.yml"));
        villages.load();
        activity = new ActivityService(getConfig().getLong("afk-timeout-minutes", 15));
        skins = SkinService.disabled();
        TowniaCommand command = new TowniaCommand(this);
        getCommand("village").setExecutor(command);
        getCommand("claim").setExecutor(command);
        getCommand("unclaim").setExecutor(command);
        getCommand("world").setExecutor(command);
        getCommand("event").setExecutor(command);
        getCommand("townia").setExecutor(command);
        villageMenu = new VillageMenu(this);
        worldMenu = new WorldMenu(this);
        getServer().getPluginManager().registerEvents(villageMenu, this);
        getServer().getPluginManager().registerEvents(worldMenu, this);
        getServer().getPluginManager().registerEvents(new TowniaListener(this), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> getServer().getOnlinePlayers().forEach(activity::flush), 20L * 60, 20L * 60);
        printStartupBanner();
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

    @Override public void onDisable() { if (villages != null) villages.save(); }
    public VillageManager villages() { return villages; }
    public ActivityService activity() { return activity; }
    public SkinService skins() { return skins; }
    public VillageMenu villageMenu() { return villageMenu; }
    public WorldMenu worldMenu() { return worldMenu; }

    public void rememberOriginalLocation(Player player) {
        if (player == null || player.hasPermission("townia.admin")) return;
        originalLocations.put(player.getUniqueId(), player.getLocation().clone());
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

    public boolean createWorld(String name) {
        String safeName = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (safeName.isBlank() || Bukkit.getWorld(safeName) != null) return false;
        return Bukkit.createWorld(new org.bukkit.WorldCreator(safeName)) != null;
    }

    public boolean renameWorld(World world, String newName) {
        if (world == null) return false;
        String safeName = newName == null ? "" : newName.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (safeName.isBlank() || safeName.equalsIgnoreCase(world.getName()) || Bukkit.getWorld(safeName) != null) return false;
        File from = new File(Bukkit.getWorldContainer(), world.getName());
        File to = new File(Bukkit.getWorldContainer(), safeName);
        if (to.exists()) return false;
        Bukkit.unloadWorld(world, false);
        try {
            java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception exception) {
            return false;
        }
        World renamed = Bukkit.createWorld(new org.bukkit.WorldCreator(safeName));
        if (renamed == null) return false;
        if (Objects.equals(getConfig().getString("event-world"), world.getName())) setEventWorld(renamed);
        return true;
    }

    public boolean deleteWorld(World world) {
        if (world == null || world.equals(Bukkit.getWorlds().getFirst())) return false;
        if (Objects.equals(getConfig().getString("event-world"), world.getName())) setEventWorld(null);
        Bukkit.unloadWorld(world, false);
        return deleteDirectory(new File(Bukkit.getWorldContainer(), world.getName()));
    }

    private boolean deleteDirectory(File directory) {
        if (!directory.exists()) return false;
        File[] entries = directory.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory() && !deleteDirectory(entry)) return false;
                else if (!entry.delete()) return false;
            }
        }
        return directory.delete();
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
