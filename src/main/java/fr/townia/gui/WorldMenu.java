package fr.townia.gui;

import fr.townia.TowniaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WorldMenu implements Listener {
    private static final String MAIN = ChatColor.DARK_GREEN + "Mondes";
    private static final String SETTINGS = ChatColor.DARK_GREEN + "Parametres du monde";
    private static final String DELETE_CONFIRM = ChatColor.DARK_RED + "Supprimer le monde";
    private static final String BACK = "Retour";
    private static final String HOME = "Menu principal";

    private final TowniaPlugin plugin;
    private final Set<UUID> waitingForWorldCreation = new HashSet<>();
    private final Map<UUID, World> waitingForWorldRename = new HashMap<>();
    private final Map<UUID, World> pendingWorldDeletion = new HashMap<>();

    public WorldMenu(TowniaPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, MAIN);
        inventory.setItem(10, item(Material.GRASS_BLOCK, "Creer un monde", "Cliquez pour definir un nom."));
        inventory.setItem(12, item(Material.REDSTONE_BLOCK, "Supprimer le /event", "Dissocie le monde d'event."));
        inventory.setItem(14, item(Material.ENDER_PEARL, "Renvoyer tout le monde", "Retourne les joueurs hors admins vers leur emplacement d'origine."));
        inventory.setItem(16, item(Material.BEACON, "Teleporter tout le monde au /event", "Tous les joueurs hors admins sont envoye vers le monde d'event."));

        int slot = 28;
        for (World world : Bukkit.getWorlds()) {
            if (slot >= 54) break;
            inventory.setItem(slot++, worldItem(world));
        }

        inventory.setItem(49, item(Material.BARRIER, HOME, "Fermer ce menu."));
        player.openInventory(inventory);
    }

    private void openSettings(Player player, World world) {
        Inventory inventory = Bukkit.createInventory(null, 27, SETTINGS);
        inventory.setItem(4, item(Material.MAP, world.getName(), "Monde : " + world.getName(), "Overworld / Nether / End gérés séparément", "Spawn : " + world.getSpawnLocation().getWorld().getName()));
        inventory.setItem(10, item(Material.NAME_TAG, "Renommer le monde", "Change le nom du dossier et du monde."));
        inventory.setItem(12, item(Material.REDSTONE, "Attribuer le /event", "Le monde choisi devient le point d'arrive de /event."));
        inventory.setItem(14, item(Material.ENDER_PEARL, "Rejoindre ce monde", "Se teleporter sur ce monde."));
        inventory.setItem(16, item(Material.TNT, "Supprimer le monde", "Suppression definitive du monde."));
        inventory.setItem(26, item(Material.ARROW, BACK, "Retour a la liste des mondes."));
        player.openInventory(inventory);
    }

    private void openDeleteConfirmation(Player player, World world) {
        pendingWorldDeletion.put(player.getUniqueId(), world);
        Inventory inventory = Bukkit.createInventory(null, 27, DELETE_CONFIRM);
        inventory.setItem(11, item(Material.RED_WOOL, "Confirmer la suppression", "Le monde " + world.getName() + " sera supprime."));
        inventory.setItem(15, item(Material.LIME_WOOL, "Annuler", "Retour aux parametres du monde."));
        player.openInventory(inventory);
    }

    private ItemStack worldItem(World world) {
        ItemStack stack = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(ChatColor.AQUA + world.getName());
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Clic gauche : parametres");
        lore.add(ChatColor.GRAY + "Clic droit : rejoindre");
        if (plugin.getEventWorld() != null && plugin.getEventWorld().getName().equals(world.getName())) {
            lore.add(ChatColor.GREEN + "Mise en place comme /event");
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(ChatColor.GREEN + name);
        meta.setLore(List.of(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(MAIN) && !title.equals(SETTINGS) && !title.equals(DELETE_CONFIRM)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;

        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (title.equals(MAIN)) {
            if (name.equals("Creer un monde")) {
                waitingForWorldCreation.add(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Saisissez le nom du monde dans le chat, ou 'annuler'.");
            } else if (name.equals("Supprimer le /event")) {
                plugin.setEventWorld(null);
                player.sendMessage(ChatColor.GREEN + "Le /event a ete supprime.");
                openMain(player);
            } else if (name.equals("Renvoyer tout le monde")) {
                plugin.returnEveryoneToOrigin();
                player.sendMessage(ChatColor.GREEN + "Tous les joueurs hors admins ont ete ramenes a leur monde d'origine.");
                openMain(player);
            } else if (name.equals("Teleporter tout le monde au /event")) {
                plugin.teleportAllNonAdminsToEvent();
                player.sendMessage(ChatColor.GREEN + "Tous les joueurs hors admins ont ete teleportes vers le /event.");
                openMain(player);
            } else if (event.getSlot() >= 28 && event.getSlot() < 54) {
                World world = worldAtSlot(event.getSlot());
                if (world != null) {
                    if (event.isRightClick()) {
                        plugin.rememberOriginalLocation(player);
                        player.teleport(world.getSpawnLocation());
                        player.sendMessage(ChatColor.GREEN + "Teleportation vers " + world.getName() + ".");
                    } else {
                        openSettings(player, world);
                    }
                }
            }
        } else if (title.equals(SETTINGS)) {
            World world = worldFromSettings(player);
            if (world == null) return;
            if (name.equals(BACK)) {
                openMain(player);
            } else if (name.equals("Renommer le monde")) {
                waitingForWorldRename.put(player.getUniqueId(), world);
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Saisissez le nouveau nom du monde dans le chat, ou 'annuler'.");
            } else if (name.equals("Attribuer le /event")) {
                plugin.setEventWorld(world);
                player.sendMessage(ChatColor.GREEN + "Le monde " + world.getName() + " est maintenant le /event.");
                openMain(player);
            } else if (name.equals("Rejoindre ce monde")) {
                plugin.rememberOriginalLocation(player);
                player.teleport(world.getSpawnLocation());
                player.sendMessage(ChatColor.GREEN + "Teleportation vers " + world.getName() + ".");
            } else if (name.equals("Supprimer le monde")) {
                openDeleteConfirmation(player, world);
            }
        } else if (title.equals(DELETE_CONFIRM)) {
            World world = pendingWorldDeletion.remove(player.getUniqueId());
            if (name.equals("Confirmer la suppression") && world != null) {
                if (plugin.deleteWorld(world)) {
                    player.sendMessage(ChatColor.GREEN + "Le monde " + world.getName() + " a ete supprime.");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de supprimer ce monde.");
                }
                openMain(player);
            } else if (name.equals("Annuler")) {
                openMain(player);
            }
        }
    }

    @EventHandler
    public void chat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (waitingForWorldCreation.remove(player.getUniqueId())) {
            event.setCancelled(true);
            String name = event.getMessage().trim();
            if (name.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.YELLOW + "Creation du monde annulee.");
                return;
            }
            if (!player.hasPermission("townia.admin.world")) return;
            if (plugin.createWorld(name)) {
                player.sendMessage(ChatColor.GREEN + "Monde cree : " + name + ".");
            } else {
                player.sendMessage(ChatColor.RED + "Impossible de creer ce monde. Nom invalide ou deja existant.");
            }
            Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
            return;
        }

        World targetWorld = waitingForWorldRename.remove(player.getUniqueId());
        if (targetWorld != null) {
            event.setCancelled(true);
            String name = event.getMessage().trim();
            if (name.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.YELLOW + "Renommage du monde annule.");
                return;
            }
            if (!player.hasPermission("townia.admin.world")) return;
            if (plugin.renameWorld(targetWorld, name)) {
                player.sendMessage(ChatColor.GREEN + "Monde renomme : " + targetWorld.getName() + " -> " + name + ".");
            } else {
                player.sendMessage(ChatColor.RED + "Impossible de renommer ce monde.");
            }
            Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
        }
    }

    private World worldAtSlot(int slot) {
        List<World> worlds = new ArrayList<>(Bukkit.getWorlds());
        int index = 0;
        for (World world : worlds) {
            if (28 + index == slot) return world;
            index++;
        }
        return null;
    }

    private World worldFromSettings(Player player) {
        for (Map.Entry<UUID, World> entry : waitingForWorldRename.entrySet()) {
            if (entry.getKey().equals(player.getUniqueId())) return entry.getValue();
        }
        return null;
    }
}
