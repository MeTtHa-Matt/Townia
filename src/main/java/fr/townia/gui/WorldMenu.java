package fr.townia.gui;

import fr.townia.TowniaPlugin;
import fr.townia.model.VillageAction;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
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
    private static final String ACTIONS = ChatColor.DARK_GREEN + "Permissions du monde";
    private static final String DELETE_CONFIRM = ChatColor.DARK_RED + "Supprimer le monde";
    private static final String BACK = "Retour";
    private static final String HOME = "Menu principal";

    private final TowniaPlugin plugin;
    private final Set<UUID> waitingForWorldCreation = new HashSet<>();
    private final Map<UUID, World> waitingForWorldRename = new HashMap<>();
    private final Map<UUID, World> currentSettingsWorld = new HashMap<>();
    private final Map<UUID, World> pendingWorldDeletion = new HashMap<>();
    private final Map<UUID, Integer> creationProgressTasks = new HashMap<>();

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
        for (World world : plugin.managedWorlds()) {
            if (slot >= 54) break;
            inventory.setItem(slot++, worldItem(world));
        }

        inventory.setItem(49, item(Material.BARRIER, HOME, "Fermer ce menu."));
        player.openInventory(inventory);
    }

    private void openSettings(Player player, World world) {
        currentSettingsWorld.put(player.getUniqueId(), world);
        Inventory inventory = Bukkit.createInventory(null, 54, SETTINGS);
        boolean protectedWorld = isProtectedWorld(world);
        inventory.setItem(4, item(protectedWorld ? Material.BEDROCK : Material.MAP, protectedWorld ? "Monde principal protégé" : world.getName(),
                "Monde : " + world.getName(),
                protectedWorld ? "Aucune action possible sur ce monde." : "Overworld / Nether / End gérés séparément",
                "Spawn : " + world.getSpawnLocation().getWorld().getName()));

        if (protectedWorld) {
            inventory.setItem(13, item(Material.BARRIER, "Aucune action disponible", "Le monde principal 'world' est protégé."));
        } else {
            inventory.setItem(10, item(Material.NAME_TAG, "Renommer le monde", "Change le nom du dossier et du monde."));
            inventory.setItem(12, item(Material.REDSTONE, "Attribuer le /event", "Le monde choisi devient le point d'arrive de /event."));
            inventory.setItem(14, item(Material.ENDER_PEARL, "Rejoindre ce monde", "Se teleporter sur ce monde."));
            inventory.setItem(16, item(Material.TNT, "Supprimer le monde", "Suppression definitive du monde."));
            inventory.setItem(19, item(Material.OAK_SIGN, "Village actif : " + (plugin.isWorldVillageEnabled(world.getName()) ? "Oui" : "Non"), "Cliquez pour basculer."));
            inventory.setItem(21, item(Material.CHEST, "Inventaire synchronise : " + (plugin.isWorldInventorySyncEnabled(world.getName()) ? "Oui" : "Non"), "Cliquez pour basculer."));
            inventory.setItem(23, item(Material.ENDER_EYE, "Retour /leave : " + (plugin.isWorldLeaveEnabled(world.getName()) ? "Oui" : "Non"), "Cliquez pour basculer."));
            inventory.setItem(25, item(Material.COMPASS, "Permissions", "Ouvre la liste des permissions du monde."));
            inventory.setItem(27, item(Material.DIAMOND_SWORD, "Mode de jeu : " + plugin.getWorldGameMode(world.getName()).name(), "Cliquez pour changer le mode de jeu."));
        }
        inventory.setItem(53, item(Material.ARROW, BACK, "Retour a la liste des mondes."));
        player.openInventory(inventory);
    }

    private void openActionSettings(Player player, World world) {
        currentSettingsWorld.put(player.getUniqueId(), world);
        Inventory inventory = Bukkit.createInventory(null, 54, ACTIONS);
        int slot = 0;
        for (VillageAction action : VillageAction.values()) {
            if (action == VillageAction.CLAIM || action == VillageAction.HOME || action == VillageAction.CREATE_ROLES) {
                continue;
            }
            if (slot >= 54) break;
            boolean allowed = plugin.isWorldDefaultAction(world.getName(), action.name());
            Material material = allowed ? Material.LIME_WOOL : Material.RED_WOOL;
            String label = switch (action) {
                case PVP -> "PVP";
                case PVE -> "PVE";
                case BUILD -> "Casser";
                case PLACE -> "Poser";
                case PICKUP -> "Ramasser";
                case DROP -> "Jeter";
                case THROW_POTIONS -> "Potions";
                case FIRE -> "Feu";
                case OPEN_CHEST -> "Coffres";
                case USE_DOOR -> "Portes";
                case USE_BUTTON -> "Boutons";
                case USE_LEVER -> "Leviers";
                default -> action.name();
            };
            inventory.setItem(slot++, item(material, label + " : " + (allowed ? "Autorisé" : "Bloqué"),
                    "Cliquez pour changer cette permission.",
                    "État actuel : " + (allowed ? "Autorisé" : "Bloqué")));
        }
        inventory.setItem(53, item(Material.ARROW, BACK, "Retour aux paramètres du monde."));
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
        boolean protectedWorld = isProtectedWorld(world);
        ItemStack stack = new ItemStack(protectedWorld ? Material.BEDROCK : Material.GRASS_BLOCK);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(ChatColor.RED + (protectedWorld ? "Monde principal protégé" : world.getName()));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Statut : " + (protectedWorld ? ChatColor.RED + "Protégé - aucune action possible" : ChatColor.GREEN + "Modifiable"));
        lore.add(ChatColor.GRAY + "Inventaire : " + (plugin.isWorldInventorySyncEnabled(world.getName()) ? ChatColor.GREEN + "Synchrone" : ChatColor.RED + "Reset a l'entree"));
        lore.add(ChatColor.GRAY + "Clic gauche : rejoindre");
        lore.add(ChatColor.GRAY + "Clic droit : parametres");
        if (protectedWorld) {
            lore.add(ChatColor.YELLOW + "Le monde 'world' ne peut pas etre supprime ni modifie.");
        }
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
        if (!title.equals(MAIN) && !title.equals(SETTINGS) && !title.equals(ACTIONS) && !title.equals(DELETE_CONFIRM)) return;
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
                    if (event.isLeftClick()) {
                        plugin.rememberOriginalLocation(player);
                        player.teleport(world.getSpawnLocation());
                        player.sendMessage(ChatColor.GREEN + "Teleportation vers " + world.getName() + ".");
                    } else if (!isProtectedWorld(world)) {
                        openSettings(player, world);
                    } else {
                        player.sendMessage(ChatColor.RED + "Le monde 'world' est protégé : aucune modification possible.");
                    }
                }
            }
        } else if (title.equals(SETTINGS)) {
            World world = currentSettingsWorld.get(player.getUniqueId());
            if (world == null) return;
            if (isProtectedWorld(world)) {
                if (name.equals(BACK)) {
                    currentSettingsWorld.remove(player.getUniqueId());
                    openMain(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Le monde principal 'world' est protégé : aucune modification possible.");
                }
                return;
            }
            if (name.equals(BACK)) {
                currentSettingsWorld.remove(player.getUniqueId());
                openMain(player);
            } else if (name.equals("Renommer le monde")) {
                waitingForWorldRename.put(player.getUniqueId(), world);
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Saisissez le nouveau nom du monde dans le chat, ou 'annuler'.");
            } else if (name.equals("Attribuer le /event")) {
                plugin.setEventWorld(world);
                player.sendMessage(ChatColor.GREEN + "Le monde " + world.getName() + " est maintenant le /event.");
                currentSettingsWorld.remove(player.getUniqueId());
                openMain(player);
            } else if (name.startsWith("Village actif")) {
                boolean current = plugin.isWorldVillageEnabled(world.getName());
                plugin.setWorldVillageEnabled(world.getName(), !current);
                player.sendMessage(ChatColor.GREEN + "Village actif pour " + world.getName() + " : " + (!current ? "Oui" : "Non") + ".");
                openSettings(player, world);
            } else if (name.startsWith("Inventaire synchronise")) {
                boolean current = plugin.isWorldInventorySyncEnabled(world.getName());
                plugin.setWorldInventorySync(world.getName(), !current);
                player.sendMessage(ChatColor.GREEN + "Inventaire synchronise pour " + world.getName() + " : " + (!current ? "Oui" : "Non") + ".");
                openSettings(player, world);
            } else if (name.startsWith("Retour /leave")) {
                boolean current = plugin.isWorldLeaveEnabled(world.getName());
                plugin.setWorldLeaveEnabled(world.getName(), !current);
                player.sendMessage(ChatColor.GREEN + "Retour /leave pour " + world.getName() + " : " + (!current ? "Oui" : "Non") + ".");
                openSettings(player, world);
            } else if (name.equals("Permissions")) {
                openActionSettings(player, world);
            } else if (name.startsWith("Mode de jeu :")) {
                GameMode current = plugin.getWorldGameMode(world.getName());
                GameMode next = switch (current) {
                    case SURVIVAL -> GameMode.CREATIVE;
                    case CREATIVE -> GameMode.ADVENTURE;
                    case ADVENTURE -> GameMode.SPECTATOR;
                    case SPECTATOR -> GameMode.SURVIVAL;
                    default -> GameMode.SURVIVAL;
                };
                plugin.setWorldGameMode(world.getName(), next);
                player.sendMessage(ChatColor.GREEN + "Mode de jeu pour " + world.getName() + " : " + next.name() + ".");
                openSettings(player, world);
            } else if (name.equals("Rejoindre ce monde")) {
                plugin.rememberOriginalLocation(player);
                player.teleport(world.getSpawnLocation());
                player.sendMessage(ChatColor.GREEN + "Teleportation vers " + world.getName() + ".");
            } else if (name.equals("Supprimer le monde")) {
                openDeleteConfirmation(player, world);
            }
        } else if (title.equals(ACTIONS)) {
            World world = currentSettingsWorld.get(player.getUniqueId());
            if (world == null) return;
            if (name.equals(BACK)) {
                openSettings(player, world);
                return;
            }
            if (name.contains(" : ")) {
                String label = name.substring(0, name.lastIndexOf(" : "));
                String normalized = switch (label) {
                    case "PVP" -> "PVP";
                    case "PVE" -> "PVE";
                    case "Casser" -> "BUILD";
                    case "Poser" -> "PLACE";
                    case "Ramasser" -> "PICKUP";
                    case "Jeter" -> "DROP";
                    case "Potions" -> "THROW_POTIONS";
                    case "Feu" -> "FIRE";
                    case "Coffres" -> "OPEN_CHEST";
                    case "Portes" -> "USE_DOOR";
                    case "Boutons" -> "USE_BUTTON";
                    case "Leviers" -> "USE_LEVER";
                    default -> null;
                };
                if (normalized == null) return;
                boolean current = plugin.isWorldDefaultAction(world.getName(), normalized);
                plugin.setWorldDefaultAction(world.getName(), normalized, !current);
                player.sendMessage(ChatColor.GREEN + "Permission " + label + " pour " + world.getName() + " : " + (!current ? "Autorisé" : "Bloqué") + ".");
                openActionSettings(player, world);
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
                stopCreationProgress(player);
                player.sendMessage(ChatColor.YELLOW + "Creation du monde annulee.");
                return;
            }
            if (!player.hasPermission("townia.admin.world")) return;
            startCreationProgress(player, name);
            player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Creation du monde '" + name + "' en cours...");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean created = plugin.createWorld(name);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    stopCreationProgress(player);
                    if (created) {
                        player.sendMessage(ChatColor.GREEN + "Monde cree : " + name + ".");
                    } else {
                        player.sendMessage(ChatColor.RED + "Impossible de creer ce monde. Nom invalide ou deja existant.");
                    }
                    openMain(player);
                });
            });
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
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.renameWorld(targetWorld, name)) {
                    player.sendMessage(ChatColor.GREEN + "Monde renomme : " + targetWorld.getName() + " -> " + name + ".");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de renommer ce monde.");
                }
                Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
            });
        }
    }

    private void startCreationProgress(Player player, String worldName) {
        stopCreationProgress(player);
        final String[] frames = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
        final int[] index = {0};
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopCreationProgress(player);
                return;
            }
            player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Creation du monde '" + worldName + "' " + frames[index[0] % frames.length]);
            index[0]++;
        }, 0L, 20L).getTaskId();
        creationProgressTasks.put(player.getUniqueId(), taskId);
    }

    private void stopCreationProgress(Player player) {
        Integer taskId = creationProgressTasks.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private boolean isProtectedWorld(World world) {
        return world != null && "world".equalsIgnoreCase(world.getName());
    }

    private World worldAtSlot(int slot) {
        List<World> worlds = plugin.managedWorlds();
        int index = 0;
        for (World world : worlds) {
            if (28 + index == slot) return world;
            index++;
        }
        return null;
    }

    private World worldFromSettings(Player player) {
        return currentSettingsWorld.get(player.getUniqueId());
    }
}
