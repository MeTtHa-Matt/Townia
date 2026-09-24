package fr.townia;

import fr.townia.command.TowniaCommand;
import fr.townia.gui.VillageMenu;
import fr.townia.listener.TowniaListener;
import fr.townia.service.ActivityService;
import fr.townia.service.SkinService;
import fr.townia.service.VillageManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class TowniaPlugin extends JavaPlugin {
    private VillageManager villages;
    private ActivityService activity;
    private SkinService skins;
    private VillageMenu villageMenu;

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
        getCommand("townia").setExecutor(command);
        villageMenu = new VillageMenu(this);
        getServer().getPluginManager().registerEvents(villageMenu, this);
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
}
