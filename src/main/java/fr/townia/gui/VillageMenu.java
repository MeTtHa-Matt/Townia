package fr.townia.gui;

import fr.townia.TowniaPlugin;
import fr.townia.model.Claim;
import fr.townia.model.Village;
import fr.townia.model.VillageRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import fr.townia.model.VillageAction;
import fr.townia.model.VillagePermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

public final class VillageMenu implements Listener {
    private static final String MAIN = ChatColor.DARK_GREEN + "Village";
    private static final String MEMBERS = ChatColor.DARK_GREEN + "Gestion des membres";
    private static final String INVITES = ChatColor.DARK_GREEN + "Inviter un joueur";
    private static final String JOIN = ChatColor.DARK_GREEN + "Villages ouverts";
    private static final String PERMISSIONS = ChatColor.DARK_GREEN + "Permissions";
    private static final String CLAIMS = ChatColor.DARK_GREEN + "Claims du village";
    private static final String PROFILE = ChatColor.DARK_GREEN + "Profil du joueur";
    private static final String ROLE_CHOOSER = ChatColor.DARK_GREEN + "Choisir un role";
    private static final String DELETE_CONFIRM = ChatColor.DARK_RED + "Supprimer le village";
    private static final String LEAVE_CONFIRM = ChatColor.DARK_RED + "Quitter le village";
    private static final String CLAIM_DELETE_CONFIRM = ChatColor.DARK_RED + "Supprimer le claim";
    private static final String CREATE_ROLE = "Creer un role";
    private static final String BACK = "Retour";
    private static final String HOME = "Menu principal";
    private final TowniaPlugin plugin;
    private final Set<UUID> waitingForVillageName = new HashSet<>();
    private final Set<UUID> waitingForRoleName = new HashSet<>();
    private final Map<UUID, UUID> editingPlayer = new java.util.HashMap<>();
    private final Map<UUID, Claim> pendingClaimDeletion = new java.util.HashMap<>();

    public VillageMenu(TowniaPlugin plugin) { this.plugin = plugin; }

    public void openMain(Player player) {
        Village village = plugin.villages().byPlayer(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 27, MAIN);
        if (village == null) {
            inventory.setItem(11, item(Material.EMERALD, "Creer un village", "Cliquez puis saisissez son nom dans le chat."));
            inventory.setItem(15, item(Material.COMPASS, "Rejoindre un village", "Voir les villages ouverts."));
        } else {
            inventory.setItem(4, profileItem(player, village));
            inventory.setItem(10, item(Material.GOLDEN_SHOVEL, "Claim ce chunk", "Protection du chunk actuel."));
            inventory.setItem(12, item(Material.IRON_SHOVEL, "Unclaim ce chunk", "Retirer votre claim actuel."));
            inventory.setItem(14, item(Material.PLAYER_HEAD, "Membres et grades", "Ajouter un joueur ou modifier son grade."));
            inventory.setItem(16, item(village.open() ? Material.LIME_DYE : Material.RED_DYE, village.open() ? "Fermer les adhesions" : "Ouvrir les adhesions", "Maire ou vice-maire uniquement."));
            inventory.setItem(19, item(Material.MAP, "Claims du village", village.claims().size() + " / " + maximumClaims(village) + " claims utilises"));
            inventory.setItem(20, item(Material.ENDER_PEARL, "Aller au home", "Se teleporter vers le home du village."));
            if (canManage(player, village)) inventory.setItem(21, item(Material.COMPARATOR, "Permissions", "Regler les actions par grade."));
            if (village.allows(player.getUniqueId(), VillageAction.CREATE_ROLES)) inventory.setItem(23, item(Material.WRITABLE_BOOK, CREATE_ROLE, "Creer un role personnalise."));
            inventory.setItem(25, item(Material.BOOK, "Profil du joueur", "Temps de jeu, grade et activité."));
            if (village.mayor().equals(player.getUniqueId())) inventory.setItem(26, item(Material.TNT, "Supprimer le village", "Action irreversible."));
            else inventory.setItem(26, item(Material.OAK_DOOR, "Quitter le village", "Vous ne serez plus membre."));
        }
        player.openInventory(inventory);
    }

    private void openProfile(Player player, Village village) {
        Inventory inventory = Bukkit.createInventory(null, 27, PROFILE);
        inventory.setItem(4, profileItem(player, village));
        inventory.setItem(11, item(Material.CLOCK, "Temps de jeu", formatDuration(plugin.activity().playtime(player.getUniqueId())), "Suivi d'activite global."));
        inventory.setItem(13, item(Material.GOLD_INGOT, "Grade", displayRoleName(village, player.getUniqueId()), "Village : " + village.name()));
        inventory.setItem(15, item(Material.BOOK, "Village", "Membres : " + village.members().size(), "Claims : " + village.claims().size() + " / " + maximumClaims(village), "Ouvert : " + (village.open() ? "Oui" : "Non")));
        inventory.setItem(22, item(Material.DIAMOND_PICKAXE, "Claims", "Terrains possedes : " + village.claims().size(), "Limite : " + maximumClaims(village)));
        inventory.setItem(24, item(Material.EMERALD, "Gestion", canManage(player, village) ? "Vous pouvez gerer le village." : "Acces limite au village."));
        inventory.setItem(26, item(Material.ARROW, BACK, "Retour au menu principal."));
        player.openInventory(inventory);
    }

    private void openMembers(Player player, Village village) {
        Inventory inventory = Bukkit.createInventory(null, 54, MEMBERS);
        int slot = 0;
        Set<UUID> targets = new LinkedHashSet<>(village.members().keySet());
        targets.removeAll(village.excluded());
        for (UUID targetId : targets) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            String targetName = target.getName() == null ? targetId.toString().substring(0, 8) : target.getName();
            meta.setDisplayName(ChatColor.YELLOW + targetName);
            List<String> lore = new ArrayList<>();
            VillageRole role = village.role(targetId);
            lore.add(ChatColor.GRAY + (role == null ? "Pas membre" : "Grade : " + displayRoleName(village, targetId)));
            if (role == null) lore.add(ChatColor.GREEN + "Clic gauche : inviter");
            else lore.add(ChatColor.GREEN + "Clic gauche : choisir le grade");
            lore.add(ChatColor.RED + "Clic droit : exclure");
            meta.setLore(lore);
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
            if (slot == 45) break;
        }
        inventory.setItem(45, item(Material.ARROW, BACK, "Retour au menu principal."));
        inventory.setItem(47, item(Material.NAME_TAG, "Inviter un joueur", "Choisir un joueur en ligne."));
        inventory.setItem(49, item(Material.BARRIER, HOME, "Fermer ce menu et revenir a l'accueil."));
        player.openInventory(inventory);
    }

    private void openInvites(Player player, Village village) {
        Inventory inventory = Bukkit.createInventory(null, 54, INVITES);
        int slot = 0;
        List<Player> candidates = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(player)) continue;
            if (village.excluded().contains(target.getUniqueId())) continue;
            if (plugin.villages().byPlayer(target.getUniqueId()) != null) continue;
            candidates.add(target);
        }
        candidates.sort((first, second) -> first.getName().compareToIgnoreCase(second.getName()));
        for (Player target : candidates) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.YELLOW + target.getName());
            meta.setLore(List.of(ChatColor.GREEN + "Cliquez pour envoyer l'invitation."));
            head.setItemMeta(meta);
            inventory.setItem(slot++, head);
            if (slot == 45) break;
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, item(Material.BARRIER, "Aucun joueur disponible", "Aucun joueur hors du village n'est en ligne."));
        }
        inventory.setItem(45, item(Material.ARROW, BACK, "Retour a la gestion des membres."));
        inventory.setItem(49, item(Material.BARRIER, HOME, "Fermer ce menu et revenir a l'accueil."));
        player.openInventory(inventory);
    }

    static List<UUID> eligibleInviteTargets(UUID self, Iterable<UUID> onlinePlayers, Iterable<UUID> villageMembers, Iterable<UUID> excludedPlayers) {
        Set<UUID> memberSet = new HashSet<>();
        for (UUID member : villageMembers) memberSet.add(member);
        Set<UUID> excludedSet = new HashSet<>();
        for (UUID excluded : excludedPlayers) excludedSet.add(excluded);
        List<UUID> candidates = new ArrayList<>();
        for (UUID target : onlinePlayers) {
            if (self != null && self.equals(target)) continue;
            if (memberSet.contains(target)) continue;
            if (excludedSet.contains(target)) continue;
            candidates.add(target);
        }
        candidates.sort(UUID::compareTo);
        return candidates;
    }

    private void openClaims(Player player, Village village) {
        Inventory inventory = Bukkit.createInventory(null, 54, CLAIMS);
        int slot = 0;
        for (Claim claim : village.claims()) {
            UUID claimerId = plugin.villages().claimer(claim);
            OfflinePlayer claimer = claimerId == null ? null : Bukkit.getOfflinePlayer(claimerId);
            String claimerName = claimer == null || claimer.getName() == null ? "Inconnu" : claimer.getName();
            inventory.setItem(slot++, item(Material.FILLED_MAP, claim.world() + " : chunk " + claim.chunkX() + ", " + claim.chunkZ(), "Claim par : " + claimerName, "Position du joueur : " + plugin.villages().claimPosition(claim), "Blocs X : " + (claim.chunkX() << 4) + " a " + ((claim.chunkX() << 4) + 15), "Blocs Z : " + (claim.chunkZ() << 4) + " a " + ((claim.chunkZ() << 4) + 15)));
            if (slot == 45) break;
        }
        inventory.setItem(45, item(Material.COMPASS, "Voir les chunks claims", "Affiche les limites des chunks claims en jeu."));
        inventory.setItem(49, item(Material.ARROW, BACK, "Retour au menu principal."));
        player.openInventory(inventory);
    }

    private Claim claimAtSlot(Village village, int slot) {
        int index = 0;
        for (Claim claim : village.claims()) {
            if (index++ == slot) return claim;
            if (index == 45) break;
        }
        return null;
    }

    private void openClaimDeleteConfirmation(Player player, Claim claim) {
        pendingClaimDeletion.put(player.getUniqueId(), claim);
        Inventory inventory = Bukkit.createInventory(null, 27, CLAIM_DELETE_CONFIRM);
        inventory.setItem(11, item(Material.RED_WOOL, "Confirmer la suppression", "Chunk : " + claim.world() + " " + claim.chunkX() + ", " + claim.chunkZ()));
        inventory.setItem(15, item(Material.LIME_WOOL, "Annuler", "Retour a la liste des claims."));
        player.openInventory(inventory);
    }

    private void deleteClaim(Player player, Village village) {
        Claim claim = pendingClaimDeletion.remove(player.getUniqueId());
        if (claim == null || !canClaim(player, village)) { deny(player); return; }
        boolean deleted = plugin.villages().unclaim(village, claim);
        player.sendMessage(deleted ? ChatColor.GREEN + "Claim supprime." : ChatColor.RED + "Ce claim n'existe plus.");
        plugin.villages().save();
        openClaims(player, village);
    }

    private void openPermissions(Player player, Village village) {
        Inventory inventory = Bukkit.createInventory(null, 54, PERMISSIONS);
        inventory.setItem(10, item(Material.GOLD_BLOCK, "Maire", "Gerer les permissions du maire."));
        inventory.setItem(12, item(Material.GOLD_INGOT, "Vice-maire", "Gerer les permissions des vice-maires."));
        inventory.setItem(14, item(Material.IRON_INGOT, "Membre", "Gerer les permissions des membres."));
        inventory.setItem(16, item(Material.COAL, "Non membre", "Gerer les permissions des non-membres."));
        inventory.setItem(20, item(Material.NAME_TAG, "Roles personnalises", "Creer et configurer des roles."));
        int roleSlot = 21;
        for (String role : village.customRoles().keySet()) inventory.setItem(roleSlot++, item(Material.PAPER, role, "Configurer ce role."));
        inventory.setItem(31, item(Material.WRITABLE_BOOK, CREATE_ROLE, "Permission CREATE_ROLES requise."));
        inventory.setItem(49, item(Material.ARROW, BACK, "Retour au menu principal."));
        player.openInventory(inventory);
    }

    private void openCategoryPermissions(Player player, Village village, String category, VillagePermissions permissions) {
        Inventory inventory = Bukkit.createInventory(null, 54, PERMISSIONS + " - " + category);
        VillageAction[] actions = VillageAction.values();
        for (int index = 0; index < actions.length; index++) {
            VillageAction action = actions[index];
            inventory.setItem(index, item(permissions.allows(action) ? Material.LIME_WOOL : Material.RED_WOOL, action.label(), permissions.allows(action) ? "Autorise" : "Interdit", "Cliquez pour inverser."));
        }
        if (village.customRoles().containsKey(category)) inventory.setItem(51, item(Material.BARRIER, "Supprimer ce role", "Les joueurs seront reaffectes comme membres.", "Cliquez pour supprimer."));
        inventory.setItem(49, item(Material.ARROW, BACK, "Retour au menu principal."));
        player.openInventory(inventory);
    }

    private void openRoleChooser(Player player, Village village, UUID targetId, String targetName) {
        editingPlayer.put(player.getUniqueId(), targetId);
        Inventory inventory = Bukkit.createInventory(null, 36, ROLE_CHOOSER);
        inventory.setItem(12, item(Material.GOLD_INGOT, "Vice-maire", targetName));
        inventory.setItem(14, item(Material.IRON_INGOT, "Membre", targetName));
        int slot = 19;
        for (String role : village.customRoles().keySet()) inventory.setItem(slot++, item(Material.NAME_TAG, role, "Role personnalise", targetName));
        inventory.setItem(31, item(Material.ARROW, BACK, "Retour a la gestion des membres."));
        player.openInventory(inventory);
    }

    private void openJoin(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, JOIN);
        int slot = 0;
        for (Village village : plugin.villages().villages().values()) {
            if (!village.open()) continue;
            inventory.setItem(slot++, item(Material.OAK_DOOR, village.name(), "Cliquez pour rejoindre", "Membres : " + village.members().size()));
            if (slot == 45) break;
        }
        inventory.setItem(45, item(Material.ARROW, BACK, "Retour au menu principal."));
        inventory.setItem(49, item(Material.BARRIER, HOME, "Fermer ce menu et revenir a l'accueil."));
        player.openInventory(inventory);
    }

    private Village openVillageAtSlot(int slot) {
        int index = 0;
        for (Village village : plugin.villages().villages().values()) {
            if (!village.open()) continue;
            if (index++ == slot) return village;
            if (index == 45) break;
        }
        return null;
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(MAIN) && !title.equals(MEMBERS) && !title.equals(INVITES) && !title.equals(JOIN) && !title.equals(PERMISSIONS) && !title.equals(CLAIMS) && !title.equals(PROFILE) && !title.equals(CLAIM_DELETE_CONFIRM) && !title.equals(ROLE_CHOOSER) && !title.equals(DELETE_CONFIRM) && !title.equals(LEAVE_CONFIRM) && !title.startsWith(PERMISSIONS + " - ")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;
        Village village = plugin.villages().byPlayer(player.getUniqueId());
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (title.equals(MAIN)) {
            if (village == null && name.equals("Creer un village")) {
                if (!player.hasPermission("townia.village.create")) { deny(player); return; }
                waitingForVillageName.add(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Saisissez le nom du village dans le chat, ou 'annuler'.");
            } else if (village == null && name.equals("Rejoindre un village")) openJoin(player);
            else if (village != null && name.equals("Claim ce chunk")) claim(player, village);
            else if (village != null && name.equals("Unclaim ce chunk")) unclaim(player, village);
            else if (village != null && name.equals("Claims du village")) openClaims(player, village);
            else if (village != null && name.equals("Aller au home")) {
                if (!player.hasPermission("townia.village.home") || !village.allows(player.getUniqueId(), VillageAction.HOME)) { deny(player); return; }
                if (village.home() == null) { player.sendMessage(ChatColor.RED + "Aucun home n'a ete defini pour ce village."); return; }
                Location home = village.home();
                if (home.getWorld() == null || Bukkit.getWorld(home.getWorld().getName()) == null) {
                    player.sendMessage(ChatColor.RED + "Le monde du home n'est pas disponible pour le moment.");
                    return;
                }
                player.teleport(home);
                player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Teleportation vers le home du village \"" + village.name() + "\".");
                player.closeInventory();
            } else if (village != null && name.equals("Profil du joueur")) openProfile(player, village);
            else if (village != null && name.equals("Membres et grades")) {
                if (canManage(player, village)) openMembers(player, village); else deny(player);
            } else if (village != null && (name.equals("Ouvrir les adhesions") || name.equals("Fermer les adhesions"))) toggleOpen(player, village);
            else if (village != null && name.equals(CREATE_ROLE)) beginRoleCreation(player, village);
            else if (village != null && name.equals("Quitter le village") && !village.mayor().equals(player.getUniqueId())) openLeaveConfirmation(player);
            else if (village != null && name.equals("Supprimer le village") && village.mayor().equals(player.getUniqueId())) openDeleteConfirmation(player);
            else if (village != null && name.equals("Permissions") && canManage(player, village)) openPermissions(player, village);
        } else if (title.equals(PROFILE) && village != null) {
            if (name.equals(BACK)) { openMain(player); return; }
            if (name.equals(HOME)) { openMain(player); return; }
        } else if (title.equals(DELETE_CONFIRM) && village != null && village.mayor().equals(player.getUniqueId())) {
            if (name.equals("Confirmer la suppression")) deleteVillage(player, village);
            else if (name.equals("Annuler")) openMain(player);
        } else if (title.equals(LEAVE_CONFIRM) && village != null && !village.mayor().equals(player.getUniqueId())) {
            if (name.equals("Confirmer le depart")) leaveVillage(player, village);
            else if (name.equals("Annuler")) openMain(player);
        } else if (title.equals(CLAIMS)) {
            if (name.equals("Voir les chunks claims")) {
                showClaimOutlines(player, village);
                return;
            }
            if (name.equals(BACK) || name.equals(HOME)) openMain(player);
            else if (event.getSlot() < 45) {
                Claim selected = claimAtSlot(village, event.getSlot());
                if (selected != null && canClaim(player, village)) openClaimDeleteConfirmation(player, selected); else deny(player);
            }
        } else if (title.equals(CLAIM_DELETE_CONFIRM)) {
            if (name.equals("Confirmer la suppression")) deleteClaim(player, village);
            else if (name.equals("Annuler")) { pendingClaimDeletion.remove(player.getUniqueId()); openClaims(player, village); }
        } else if (title.equals(JOIN)) {
            if (name.equals(BACK) || name.equals(HOME)) { openMain(player); return; }
            Village selected = event.getSlot() < 45 ? openVillageAtSlot(event.getSlot()) : null;
            if (selected != null && selected.open() && !selected.excluded().contains(player.getUniqueId()) && village == null) {
                selected.addMember(player.getUniqueId());
                plugin.villages().save();
                player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Vous avez rejoint " + selected.name() + ".");
                openMain(player);
            } else if (selected != null) {
                player.sendMessage(ChatColor.RED + "Impossible de rejoindre ce village.");
            }
        } else if (title.equals(MEMBERS) && village != null && canManage(player, village) && (name.equals(BACK) || name.equals(HOME))) {
            openMain(player);
        } else if (title.equals(MEMBERS) && village != null && canManage(player, village) && name.equals("Inviter un joueur")) {
            openInvites(player, village);
        } else if (title.equals(MEMBERS) && village != null && canManage(player, village) && clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            OfflinePlayer target = meta.getOwningPlayer();
            if (target != null && event.getClick() == ClickType.RIGHT) toggleExcluded(player, village, target.getUniqueId(), target.getName());
            else if (target != null && village.role(target.getUniqueId()) == null) invite(player, village, target);
            else if (target != null) openRoleChooser(player, village, target.getUniqueId(), target.getName());
        } else if (title.equals(INVITES) && village != null && canManage(player, village) && (name.equals(BACK) || name.equals(HOME))) {
            if (name.equals(BACK)) openMembers(player, village); else openMain(player);
        } else if (title.equals(INVITES) && village != null && canManage(player, village) && clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            OfflinePlayer target = meta.getOwningPlayer();
            if (target != null) invite(player, village, target);
        } else if (title.equals(PERMISSIONS) && village != null && canManage(player, village)) {
            if (name.equals("Maire")) { openCategoryPermissions(player, village, name, village.permissions().get(VillageRole.MAYOR)); return; }
            if (name.equals("Vice-maire")) { openCategoryPermissions(player, village, name, village.permissions().get(VillageRole.VICE_MAYOR)); return; }
            if (name.equals("Membre")) { openCategoryPermissions(player, village, name, village.permissions().get(VillageRole.MEMBER)); return; }
            if (name.equals("Non membre")) { openCategoryPermissions(player, village, name, village.nonMemberPermissions()); return; }
            if (name.equals(CREATE_ROLE)) { beginRoleCreation(player, village); return; }
            if (village.customRoles().containsKey(name)) { openCategoryPermissions(player, village, name, village.customRoles().get(name)); return; }
            if (name.equals(BACK)) { openMembers(player, village); return; }
            if (name.equals(HOME)) { openMain(player); return; }
            int row = event.getSlot() / 9;
            int actionIndex = event.getSlot() % 9 - 1;
            if (row >= 0 && row < 4 && actionIndex >= 0 && actionIndex < VillageAction.values().length) togglePermission(player, village, row, VillageAction.values()[actionIndex]);
        } else if (title.startsWith(PERMISSIONS + " - ") && village != null && canManage(player, village)) {
            if (name.equals(BACK)) { openPermissions(player, village); return; }
            if (name.equals("Supprimer ce role")) { deleteCustomRole(player, village, title.substring((PERMISSIONS + " - ").length())); return; }
            if (event.getSlot() < VillageAction.values().length) toggleCategoryPermission(player, village, title.substring((PERMISSIONS + " - ").length()), VillageAction.values()[event.getSlot()]);
        } else if (title.equals(ROLE_CHOOSER) && village != null && canManage(player, village)) {
            if (name.equals(BACK)) { openMembers(player, village); return; }
            UUID targetId = editingPlayer.get(player.getUniqueId());
            if (targetId != null) assignRole(player, village, targetId, name);
        }
    }

    @EventHandler public void chat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        boolean roleCreation = waitingForRoleName.remove(player.getUniqueId());
        if (!roleCreation && !waitingForVillageName.remove(player.getUniqueId())) return;
        event.setCancelled(true);
        String name = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (name.equalsIgnoreCase("annuler")) { player.sendMessage(ChatColor.YELLOW + "Creation annulee."); return; }
            if (roleCreation) {
                Village own = plugin.villages().byPlayer(player.getUniqueId());
                if (own == null || !own.allows(player.getUniqueId(), VillageAction.CREATE_ROLES) || own.customRoles().containsKey(name) || name.isBlank()) { deny(player); return; }
                own.customRoles().put(name, VillagePermissions.defaultsForNonMembers());
                plugin.villages().save();
                player.sendMessage(ChatColor.GREEN + "Role cree : " + name);
                openCategoryPermissions(player, own, name, own.customRoles().get(name));
                return;
            }
            if (!player.hasPermission("townia.village.create")) { deny(player); return; }
            Village village = plugin.villages().create(name, player.getUniqueId());
            if (village == null) player.sendMessage(ChatColor.RED + "Nom deja utilise ou vous avez deja un village.");
            else player.sendMessage(ChatColor.GREEN + "Village cree : " + village.name());
            plugin.villages().save();
            openMain(player);
        });
    }

    @EventHandler public void close(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals(MAIN) || event.getView().getTitle().equals(MEMBERS) || event.getView().getTitle().equals(INVITES) || event.getView().getTitle().equals(JOIN) || event.getView().getTitle().equals(PERMISSIONS)) return;
    }

    private void claim(Player player, Village village) {
        if (!canClaim(player, village)) { deny(player); return; }
        int maximumClaims = village.members().size() * plugin.getConfig().getInt("claims-per-member", 8);
        if (village.claims().size() >= maximumClaims) {
            player.sendMessage(ChatColor.RED + "Limite de claims atteinte.");
            return;
        }
        Claim claim = Claim.at(player.getChunk());
        Village owner = plugin.villages().owner(claim);
        if (owner != null) {
            player.sendMessage(ChatColor.RED + "[Townia] Ce chunk appartient deja au village " + owner.name() + ".");
            player.sendActionBar(ChatColor.RED + "Zone deja claim par " + owner.name());
            if (!owner.id().equals(village.id())) {
                Player mayor = Bukkit.getPlayer(owner.mayor());
                if (mayor != null && !mayor.equals(player)) mayor.sendMessage(ChatColor.RED + "[Townia] Alerte : " + player.getName() + " a tente de claim un chunk de votre village.");
            }
            return;
        }
        plugin.villages().claim(village, claim, player.getUniqueId(), player.getLocation());
        player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Chunk claim.");
        plugin.villages().save();
        openMain(player);
    }

    private void unclaim(Player player, Village village) {
        if (!canClaim(player, village)) { deny(player); return; }
        Claim claim = Claim.at(player.getChunk());
        boolean removed = plugin.villages().unclaim(village, claim);
        player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + (removed ? "Chunk libere." : "Ce chunk ne vous appartient pas."));
        plugin.villages().save();
        openMain(player);
    }

    private void toggleOpen(Player player, Village village) {
        if (!canManage(player, village)) { deny(player); return; }
        boolean opening = !village.open();
        village.setOpen(opening);
        plugin.villages().save();
        notifyVillageMembers(village, player.getName() + " a " + (opening ? "ouvert" : "ferme") + " les adhesions du village.", player.getUniqueId());
        openMain(player);
    }

    private void leaveVillage(Player player, Village village) {
        if (village.mayor().equals(player.getUniqueId())) { deny(player); return; }
        int claimsPerMember = plugin.getConfig().getInt("claims-per-member", 8);
        int claimsRemoved = plugin.villages().removeClaimsBy(village, player.getUniqueId(), claimsPerMember);
        village.members().remove(player.getUniqueId());
        village.customRoleAssignments().remove(player.getUniqueId());
        claimsRemoved += plugin.villages().trimClaimsTo(village, village.members().size() * claimsPerMember);
        plugin.villages().save();
        notifyVillageMembers(village, player.getName() + " a quitte le village.", player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Vous avez quitte le village " + village.name() + ". " + claimsRemoved + " claim(s) ont ete libere(s).");
    }

    private void openDeleteConfirmation(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, DELETE_CONFIRM);
        inventory.setItem(11, item(Material.RED_WOOL, "Confirmer la suppression", "Tous les claims et roles seront supprimes."));
        inventory.setItem(15, item(Material.LIME_WOOL, "Annuler", "Retour au menu principal."));
        player.openInventory(inventory);
    }

    private void openLeaveConfirmation(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, LEAVE_CONFIRM);
        inventory.setItem(11, item(Material.RED_WOOL, "Confirmer le depart", "Vous ne serez plus membre du village."));
        inventory.setItem(15, item(Material.LIME_WOOL, "Annuler", "Retour au menu principal."));
        player.openInventory(inventory);
    }

    private void deleteVillage(Player player, Village village) {
        plugin.villages().delete(village);
        plugin.villages().save();
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Village supprime.");
    }

    private void beginRoleCreation(Player player, Village village) {
        if (!village.allows(player.getUniqueId(), VillageAction.CREATE_ROLES)) { deny(player); return; }
        waitingForRoleName.add(player.getUniqueId());
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Saisissez le nom du role dans le chat, ou 'annuler'.");
    }

    private void deleteCustomRole(Player player, Village village, String roleName) {
        if (!village.allows(player.getUniqueId(), VillageAction.CREATE_ROLES) || !village.customRoles().containsKey(roleName)) { deny(player); return; }
        village.customRoles().remove(roleName);
        village.customRoleAssignments().entrySet().removeIf(entry -> entry.getValue().equals(roleName));
        plugin.villages().save();
        player.sendMessage(ChatColor.GREEN + "Role personnalise supprime : " + roleName);
        openPermissions(player, village);
    }

    private void invite(Player actor, Village village, OfflinePlayer target) {
        if (!target.isOnline() || !actor.hasPermission("townia.village.manage")) { deny(actor); return; }
        village.invited().add(target.getUniqueId());
        plugin.villages().save();
        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            Component accept = Component.text("[ACCEPTER L'INVITATION]")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/village accept " + village.id()))
                    .hoverEvent(HoverEvent.showText(Component.text("Cliquer pour rejoindre " + village.name())));
            onlineTarget.sendMessage(Component.text("[Townia] ")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .append(Component.text(actor.getName() + " vous invite dans " + village.name() + ". "))
                    .append(accept));
        }
        actor.sendMessage(ChatColor.GREEN + "Invitation envoyee a " + target.getName() + ".");
        notifyVillageMembers(village, actor.getName() + " a invite " + target.getName() + " dans le village.", actor.getUniqueId(), target.getUniqueId());
        openMembers(actor, village);
    }

    private void setNextRole(Player actor, Village village, UUID targetId, String targetName) {
        if (!actor.hasPermission("townia.village.manage") || village.mayor().equals(targetId) || village.excluded().contains(targetId)) { deny(actor); return; }
        VillageRole current = village.role(targetId);
        VillageRole next = current == null ? VillageRole.MEMBER : current == VillageRole.MEMBER ? VillageRole.VICE_MAYOR : VillageRole.MEMBER;
        if (next == VillageRole.VICE_MAYOR) {
            long viceMayors = village.members().values().stream().filter(role -> role == VillageRole.VICE_MAYOR).count();
            int maximum = Math.max(1, village.members().size() / plugin.getConfig().getInt("vice-mayor-per-members", 10));
            if (viceMayors >= maximum) { actor.sendMessage(ChatColor.RED + "Limite de vice-maires atteinte."); return; }
        }
        village.setRole(targetId, next);
        plugin.villages().save();
        notifyVillageMembers(village, targetName + " est maintenant " + roleName(next) + ".", actor.getUniqueId(), targetId);
        actor.sendMessage(ChatColor.GREEN + targetName + " est maintenant " + roleName(next) + ".");
        openMembers(actor, village);
    }

    private void toggleExcluded(Player actor, Village village, UUID targetId, String targetName) {
        if (village.mayor().equals(targetId) || actor.getUniqueId().equals(targetId)) { deny(actor); return; }
        boolean reintegrated = village.excluded().remove(targetId);
        if (reintegrated) {
            actor.sendMessage(ChatColor.GREEN + targetName + " a ete reintegre comme membre.");
            village.setRole(targetId, VillageRole.MEMBER);
            notifyVillageMembers(village, targetName + " a ete reintegre dans le village.", actor.getUniqueId(), targetId);
        } else {
            village.members().remove(targetId);
            village.invited().remove(targetId);
            village.excluded().add(targetId);
            actor.sendMessage(ChatColor.RED + targetName + " a ete exclu du village.");
            notifyVillageMembers(village, targetName + " a ete exclu du village.", actor.getUniqueId(), targetId);
        }
        plugin.villages().save();
        openMembers(actor, village);
    }

    private void togglePermission(Player actor, Village village, int row, VillageAction action) {
        if (row == 3) {
            VillagePermissions updated = village.nonMemberPermissions().with(action, !village.nonMemberPermissions().allows(action));
            village.setNonMemberPermissions(updated);
        } else {
            VillageRole role = VillageRole.values()[row];
            VillagePermissions current = village.permissions().get(role);
            village.setPermissions(role, current.with(action, !current.allows(action)));
        }
        plugin.villages().save();
        openPermissions(actor, village);
    }

    private void toggleCategoryPermission(Player actor, Village village, String category, VillageAction action) {
        if (category.equals("Maire")) village.setPermissions(VillageRole.MAYOR, village.permissions().get(VillageRole.MAYOR).with(action, !village.permissions().get(VillageRole.MAYOR).allows(action)));
        else if (category.equals("Vice-maire")) village.setPermissions(VillageRole.VICE_MAYOR, village.permissions().get(VillageRole.VICE_MAYOR).with(action, !village.permissions().get(VillageRole.VICE_MAYOR).allows(action)));
        else if (category.equals("Membre")) village.setPermissions(VillageRole.MEMBER, village.permissions().get(VillageRole.MEMBER).with(action, !village.permissions().get(VillageRole.MEMBER).allows(action)));
        else if (category.equals("Non membre")) village.setNonMemberPermissions(village.nonMemberPermissions().with(action, !village.nonMemberPermissions().allows(action)));
        else if (village.customRoles().containsKey(category)) village.customRoles().put(category, village.customRoles().get(category).with(action, !village.customRoles().get(category).allows(action)));
        plugin.villages().save();
        VillagePermissions permissions = category.equals("Maire") ? village.permissions().get(VillageRole.MAYOR) : category.equals("Vice-maire") ? village.permissions().get(VillageRole.VICE_MAYOR) : category.equals("Membre") ? village.permissions().get(VillageRole.MEMBER) : category.equals("Non membre") ? village.nonMemberPermissions() : village.customRoles().get(category);
        openCategoryPermissions(actor, village, category, permissions);
    }

    private void assignRole(Player actor, Village village, UUID targetId, String roleName) {
        village.customRoleAssignments().remove(targetId);
        String oldRoleName = village.role(targetId) == null ? "Pas membre" : displayRoleName(village, targetId);
        if (roleName.equals("Maire")) { deny(actor); return; }
        else if (roleName.equals("Vice-maire")) {
            long viceMayors = village.members().values().stream().filter(role -> role == VillageRole.VICE_MAYOR).count();
            int maximum = Math.max(1, village.members().size() / plugin.getConfig().getInt("vice-mayor-per-members", 10));
            if (village.role(targetId) != VillageRole.VICE_MAYOR && viceMayors >= maximum) {
                actor.sendMessage(ChatColor.RED + "Limite de vice-maires atteinte.");
                return;
            }
            village.setRole(targetId, VillageRole.VICE_MAYOR);
        }
        else if (roleName.equals("Membre")) village.setRole(targetId, VillageRole.MEMBER);
        else if (village.customRoles().containsKey(roleName)) {
            village.addMember(targetId);
            village.customRoleAssignments().put(targetId, roleName);
        }
        plugin.villages().save();
        String newRoleName = roleName.equals("Maire") ? "Maire" : roleName.equals("Vice-maire") ? "Vice-maire" : roleName.equals("Membre") ? "Membre" : roleName;
        notifyVillageMembers(village, "Changement de role : " + oldRoleName + " -> " + newRoleName + ".", actor.getUniqueId(), targetId);
        editingPlayer.remove(actor.getUniqueId());
        openMembers(actor, village);
    }

    private boolean canManage(Player player, Village village) {
        return player.hasPermission("townia.village.manage") && (village.role(player.getUniqueId()) == VillageRole.MAYOR || village.role(player.getUniqueId()) == VillageRole.VICE_MAYOR);
    }
    private boolean canClaim(Player player, Village village) {
        return village != null && player.hasPermission("townia.village.claim") && village.isMember(player.getUniqueId()) && village.allows(player.getUniqueId(), VillageAction.CLAIM);
    }
    private int maximumClaims(Village village) { return village.members().size() * plugin.getConfig().getInt("claims-per-member", 8); }
    private String roleName(VillageRole role) { return role == VillageRole.MAYOR ? "Maire" : role == VillageRole.VICE_MAYOR ? "Vice-maire" : "Membre"; }
    private ItemStack item(Material material, String name, String... loreText) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + name);
        meta.setLore(List.of(loreText));
        stack.setItemMeta(meta);
        return stack;
    }
    private ItemStack profileItem(Player player, Village village) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(player.getUniqueId()));
            meta.setDisplayName(ChatColor.GOLD + village.name());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Grade : " + roleName(village.role(player.getUniqueId())));
            lore.add(ChatColor.GRAY + "Membres : " + village.members().size());
            lore.add(ChatColor.GRAY + "Claims : " + village.claims().size() + " / " + maximumClaims(village));
            lore.add(ChatColor.GRAY + "Temps de jeu : " + formatDuration(plugin.activity().playtime(player.getUniqueId())));
            lore.add(ChatColor.GRAY + "Acces au village : " + (village.open() ? "ouvert" : "ferme"));
            lore.add(ChatColor.GRAY + "Gestion : " + (canManage(player, village) ? "active" : "restreinte"));
            lore.add(ChatColor.DARK_GRAY + "Cliquez pour voir votre profil complet.");
            meta.setLore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " s";
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) return days + "j " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        return minutes + "m " + (seconds % 60) + "s";
    }

    private void notifyVillageMembers(Village village, String message, UUID... excluded) {
        if (village == null) return;
        Set<UUID> excludedIds = new HashSet<>(List.of(excluded));
        for (UUID memberId : village.members().keySet()) {
            if (excludedIds.contains(memberId)) continue;
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) member.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + message);
        }
    }

    private void showClaimOutlines(Player player, Village village) {
        if (village == null || village.claims().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Aucun chunk claim a afficher pour ce village.");
            return;
        }

        for (Claim claim : village.claims()) {
            for (int[] point : chunkOutlinePoints(claim)) {
                Location location = new Location(Bukkit.getWorld(claim.world()), point[0], 70, point[2]);
                player.spawnParticle(org.bukkit.Particle.FLAME, location, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }

        player.sendMessage(ChatColor.GREEN + "Contours des chunks claims affiches pendant quelques secondes.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> player.sendMessage(ChatColor.GRAY + "Les contours de claims ne sont plus visibles."), 80L);
    }

    public static List<int[]> chunkOutlinePoints(Claim claim) {
        int minX = (claim.chunkX() << 4);
        int minZ = (claim.chunkZ() << 4);
        int maxX = minX + 15;
        int maxZ = minZ + 15;
        List<int[]> points = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            points.add(new int[] { x, 64, minZ });
            points.add(new int[] { x, 64, maxZ });
        }
        for (int z = minZ; z <= maxZ; z++) {
            points.add(new int[] { minX, 64, z });
            points.add(new int[] { maxX, 64, z });
        }
        return points;
    }

    private String displayRoleName(Village village, UUID playerId) {
        String customRole = village.customRoleAssignments().get(playerId);
        if (customRole != null && !customRole.isBlank()) return customRole;
        VillageRole role = village.role(playerId);
        return roleName(role);
    }

    private void deny(Player player) { player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission pour cette action."); }
}
