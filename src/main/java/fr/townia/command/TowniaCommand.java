package fr.townia.command;

import fr.townia.TowniaPlugin;
import fr.townia.model.Claim;
import fr.townia.model.Village;
import fr.townia.model.VillageRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Objects;

public final class TowniaCommand implements CommandExecutor {
    private final TowniaPlugin plugin;
    public TowniaCommand(TowniaPlugin plugin) { this.plugin = plugin; }
    private void say(CommandSender sender, String message) { sender.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + message); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("village")) return village(sender, args);
        if (command.getName().equalsIgnoreCase("claim")) return village(sender, new String[]{"claim"});
        if (command.getName().equalsIgnoreCase("unclaim")) return village(sender, new String[]{"unclaim"});
        if (command.getName().equalsIgnoreCase("sethome")) return setHome(sender, args);
        if (command.getName().equalsIgnoreCase("home")) return home(sender, args);
        if (command.getName().equalsIgnoreCase("delhome")) return delHome(sender, args);
        if (command.getName().equalsIgnoreCase("world")) return world(sender, args);
        if (command.getName().equalsIgnoreCase("event")) return event(sender, args);
        if (command.getName().equalsIgnoreCase("leave")) return leave(sender, args);
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(sender); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) { plugin.reloadConfig(); say(sender, "Configuration reloaded."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("stats") && sender instanceof Player player) {
            say(sender, "Play time: " + plugin.activity().playtime(player.getUniqueId()) + " seconds"); return true;
        }
        help(sender); return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Townia =====");
        sender.sendMessage(ChatColor.YELLOW + "/village" + ChatColor.WHITE + " - Ouvrir l'interface graphique");
        sender.sendMessage(ChatColor.YELLOW + "/village create <nom>" + ChatColor.WHITE + " - Creer un village");
        sender.sendMessage(ChatColor.YELLOW + "/village invite <joueur>" + ChatColor.WHITE + " - Inviter un joueur");
        sender.sendMessage(ChatColor.YELLOW + "/village join <nom>" + ChatColor.WHITE + " - Rejoindre un village ouvert ou invite");
        sender.sendMessage(ChatColor.YELLOW + "/village open|close" + ChatColor.WHITE + " - Ouvrir ou fermer les adhesions");
        sender.sendMessage(ChatColor.YELLOW + "/village claim|unclaim" + ChatColor.WHITE + " - Gerer le chunk actuel");
        sender.sendMessage(ChatColor.YELLOW + "/claim" + ChatColor.WHITE + " - Claim le chunk actuel");
        sender.sendMessage(ChatColor.YELLOW + "/unclaim" + ChatColor.WHITE + " - Retirer le claim actuel");
        sender.sendMessage(ChatColor.YELLOW + "/village trust <joueur>" + ChatColor.WHITE + " - Ajouter un membre");
        sender.sendMessage(ChatColor.YELLOW + "/village setrole <joueur> <member|vice>" + ChatColor.WHITE + " - Gerer un grade");
        sender.sendMessage(ChatColor.YELLOW + "/townia stats" + ChatColor.WHITE + " - Voir son temps de jeu");
        if (sender.hasPermission("townia.admin")) {
            sender.sendMessage(ChatColor.RED + "--- Administration ---");
            sender.sendMessage(ChatColor.RED + "/world" + ChatColor.WHITE + " - Ouvrir la gestion des mondes");
            sender.sendMessage(ChatColor.RED + "/event" + ChatColor.WHITE + " - Se teleporter vers le monde d'event");
            sender.sendMessage(ChatColor.RED + "/townia reload" + ChatColor.WHITE + " - Recharger la configuration");
            sender.sendMessage(ChatColor.RED + "/townia help" + ChatColor.WHITE + " - Afficher cette aide");
        }
    }

    private boolean setHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { say(sender, "Player only."); return true; }
        if (!plugin.isWorldVillageEnabled(player.getWorld().getName())) {
            say(player, "Le système de village est désactivé dans ce monde.");
            return true;
        }
        Village village;
        if (args != null && args.length > 0) {
            if (!player.hasPermission("townia.admin")) return denied(player);
            village = plugin.villages().byName(String.join(" ", args));
            if (village == null) { say(player, "Aucun village trouve pour le nom donne."); return true; }
        } else {
            village = plugin.villages().byPlayer(player.getUniqueId());
            if (village == null || !canManage(village, player)) {
                say(player, "Vous devez etre maire ou vice-maire du village pour definir son home.");
                return true;
            }
        }

        Claim claim = Claim.at(player.getLocation().getChunk());
        Village claimOwner = plugin.villages().owner(claim);
        if (claimOwner == null || !claimOwner.id().equals(village.id())) {
            say(player, "Le home ne peut être défini que dans un claim du village.");
            return true;
        }

        village.setHome(player.getLocation().clone());
        plugin.villages().save();
        say(player, "Home du village \"" + village.name() + "\" enregistré.");
        return true;
    }

    private boolean home(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { say(sender, "Player only."); return true; }
        if (!plugin.isWorldVillageEnabled(player.getWorld().getName())) {
            say(player, "Le système de village est désactivé dans ce monde.");
            return true;
        }
        Village village = plugin.villages().byPlayer(player.getUniqueId());
        if (village == null) { say(player, "Vous n'etes dans aucun village."); return true; }
        if (!player.hasPermission("townia.village.home") || !village.allows(player.getUniqueId(), fr.townia.model.VillageAction.HOME)) {
            return denied(player);
        }
        if (village.home() == null) { say(player, "Aucun home n'a ete defini pour ce village."); return true; }
        if (village.home().getWorld() == null || Bukkit.getWorld(village.home().getWorld().getName()) == null) {
            say(player, "Le monde du home n'est pas disponible pour le moment.");
            return true;
        }
        player.teleport(village.home());
        say(player, "Teleportation vers le home du village \"" + village.name() + "\".");
        return true;
    }

    private boolean delHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { say(sender, "Player only."); return true; }
        if (!plugin.isWorldVillageEnabled(player.getWorld().getName())) {
            say(player, "Le système de village est désactivé dans ce monde.");
            return true;
        }
        Village village;
        if (args != null && args.length > 0) {
            if (!player.hasPermission("townia.admin")) return denied(player);
            village = plugin.villages().byName(String.join(" ", args));
            if (village == null) { say(player, "Aucun village trouve pour le nom donne."); return true; }
        } else {
            village = plugin.villages().byPlayer(player.getUniqueId());
            if (village == null || !canManage(village, player)) {
                say(player, "Vous devez etre maire ou vice-maire du village pour supprimer son home.");
                return true;
            }
        }
        if (village.home() == null) { say(player, "Aucun home n'est enregistre pour ce village."); return true; }
        village.setHome(null);
        plugin.villages().save();
        say(player, "Home du village \"" + village.name() + "\" supprime.");
        return true;
    }

    private boolean village(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { say(sender, "Player only."); return true; }
        if (!plugin.isWorldVillageEnabled(player.getWorld().getName())) {
            say(player, "Le système de village est désactivé dans ce monde.");
            return true;
        }
        if (args.length == 0) { plugin.villageMenu().openMain(player); return true; }
        Village own = plugin.villages().byPlayer(player.getUniqueId());
        switch (args[0].toLowerCase()) {
            case "create" -> { if (!player.hasPermission("townia.village.create")) return denied(player); if (args.length < 2) return usage(player, "create <name>"); Village created = plugin.villages().create(args[1], player.getUniqueId()); say(player, created == null ? "Name used or already in a village." : "Village created: " + created.name()); }
            case "accept" -> { if (args.length < 2) return usage(player, "accept <village-id>"); Village target; try { target = plugin.villages().byId(java.util.UUID.fromString(args[1])); } catch (IllegalArgumentException exception) { return usage(player, "invalid village invitation"); } if (target == null || target.excluded().contains(player.getUniqueId()) || !target.invited().contains(player.getUniqueId())) return usage(player, "invitation expired or invalid"); if (own != null) return usage(player, "leave your current village first"); target.addMember(player.getUniqueId()); target.invited().remove(player.getUniqueId()); say(player, "You joined " + target.name()); }
            case "invite" -> { if (own == null || !canManage(own, player)) return denied(player); if (args.length < 2) return usage(player, "invite <player>"); Player target = Bukkit.getPlayerExact(args[1]); if (target == null) return usage(player, "invite an online player"); own.invited().add(target.getUniqueId()); say(target, "You were invited to " + own.name() + ". Use /village join " + own.name()); }
            case "open" -> { if (own == null || !canManage(own, player)) return denied(player); own.setOpen(true); say(player, "Village is now open."); }
            case "close" -> { if (own == null || !canManage(own, player)) return denied(player); own.setOpen(false); say(player, "Village is now invite-only."); }
            case "join" -> { if (args.length < 2) return usage(player, "join <name>"); String villageName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)); Village target = plugin.villages().byName(villageName); if (target == null || target.excluded().contains(player.getUniqueId()) || (!target.open() && !target.invited().contains(player.getUniqueId()))) return usage(player, "village not found or invite required"); if (own != null) return usage(player, "leave your current village first"); target.addMember(player.getUniqueId()); target.invited().remove(player.getUniqueId()); say(player, "You joined " + target.name()); }
            case "claim" -> { if (!canClaim(own, player)) return denied(player); int max = maximumClaims(own); if (own.claims().size() >= max) return usage(player, "claim limit reached (" + max + ")"); Claim claim = Claim.at(player.getChunk()); Village owner = plugin.villages().owner(claim); if (owner != null) { claimConflict(player, own, owner); return true; } plugin.villages().claim(own, claim, player.getUniqueId(), player.getLocation()); say(player, "Chunk " + claim.chunkX() + ", " + claim.chunkZ() + " claimed (position bloc " + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockZ() + ")."); }
            case "unclaim" -> { if (!canClaim(own, player)) return denied(player); Claim claim = Claim.at(player.getChunk()); boolean ok = plugin.villages().unclaim(own, claim); say(player, ok ? "Chunk " + claim.chunkX() + ", " + claim.chunkZ() + " unclaimed." : "This chunk is not yours."); }
            case "trust" -> { if (own == null || !canManage(own, player) || !player.hasPermission("townia.village.manage") || args.length < 2) return denied(player); Player target = Bukkit.getPlayerExact(args[1]); if (target != null) { own.setRole(target.getUniqueId(), VillageRole.MEMBER); say(player, target.getName() + " is now a member."); } }
            case "setrole" -> { if (own == null || !player.getUniqueId().equals(own.mayor()) || !player.hasPermission("townia.village.manage") || args.length < 3) return denied(player); Player target = Bukkit.getPlayerExact(args[1]); if (target != null) { VillageRole role = args[2].equalsIgnoreCase("vice") ? VillageRole.VICE_MAYOR : VillageRole.MEMBER; long viceMayors = own.members().values().stream().filter(existing -> existing == VillageRole.VICE_MAYOR).count(); int maximum = Math.max(1, own.members().size() / plugin.getConfig().getInt("vice-mayor-per-members", 10)); if (role == VillageRole.VICE_MAYOR && own.role(target.getUniqueId()) != VillageRole.VICE_MAYOR && viceMayors >= maximum) return usage(player, "vice-mayor limit reached"); own.setRole(target.getUniqueId(), role); } }
            default -> say(player, "Unknown village action.");
        }
        plugin.villages().save(); return true;
    }

    private boolean world(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("townia.admin.world")) return denied(player);
        if (args.length == 0) {
            plugin.worldMenu().openMain(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                if (plugin.managedWorlds().isEmpty()) {
                    say(sender, "Aucun monde multiverse charge.");
                    return true;
                }
                say(sender, "Mondes disponibles :");
                for (World world : plugin.managedWorlds()) {
                    say(sender, "- " + world.getName() + " (sync: " + (plugin.isWorldInventorySyncEnabled(world.getName()) ? "oui" : "non") + ")");
                }
                return true;
            }
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.GOLD + "[Townia] " + ChatColor.WHITE + "Saisissez le nom du monde dans le chat, ou 'annuler'.");
                    return true;
                }
                String name = args[1].replaceAll("[^A-Za-z0-9_-]", "");
                if (plugin.createWorld(name)) say(sender, "Monde cree : " + name);
                else say(sender, "Impossible de creer ce monde.");
                return true;
            }
            case "join", "tp" -> {
                if (args.length < 2) return usage(sender, "join <nom>");
                World world = Bukkit.getWorld(args[1]);
                if (world == null) { say(sender, "Monde introuvable."); return true; }
                player.teleport(world.getSpawnLocation());
                say(sender, "Teleportation vers " + world.getName() + ".");
                return true;
            }
            case "delete" -> {
                if (args.length < 2) return usage(sender, "delete <nom>");
                World world = Bukkit.getWorld(args[1]);
                if (world == null) return usage(sender, "monde invalide");
                if (!world.getPlayers().isEmpty()) {
                    say(sender, "Impossible de supprimer le monde " + world.getName() + " : des joueurs sont encore dedans.");
                    for (Player online : world.getPlayers()) {
                        online.sendMessage(ChatColor.RED + "[Townia] Impossible de supprimer ce monde tant que vous y etes encore present.");
                    }
                    return true;
                }
                if (plugin.deleteWorld(world)) say(sender, "Monde supprime : " + world.getName());
                else say(sender, "Impossible de supprimer ce monde.");
                return true;
            }
            default -> {
                plugin.worldMenu().openMain(player);
                return true;
            }
        }
    }

    private boolean event(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        World eventWorld = plugin.getEventWorld();
        if (eventWorld == null) {
            player.sendMessage(ChatColor.RED + "Aucun event pour le moment.");
            return true;
        }
        if (player.getWorld().equals(eventWorld)) {
            player.sendMessage(ChatColor.YELLOW + "Vous êtes déjà dans le monde d'event : " + eventWorld.getName() + ".");
            return true;
        }
        player.teleport(eventWorld.getSpawnLocation());
        player.sendMessage(ChatColor.GREEN + "Teleportation vers le /event : " + eventWorld.getName() + ".");
        return true;
    }

    private boolean leave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        World current = player.getWorld();
        if (current == null) return true;
        if (plugin.isDefaultWorldName(current.getName()) || "world_nether".equalsIgnoreCase(current.getName()) || "world_the_end".equalsIgnoreCase(current.getName())) {
            say(player, "La commande /leave n'est disponible que dans les mondes crees par /world.");
            return true;
        }
        if (!plugin.isCustomManagedWorld(current.getName())) {
            say(player, "Ce monde ne peut pas etre quitte avec /leave.");
            return true;
        }
        if (!plugin.isWorldLeaveEnabled(current.getName())) {
            say(player, "Le /leave est desactive dans ce monde.");
            return true;
        }
        if (!plugin.canLeaveWorld(player)) {
            say(player, "Vous ne pouvez pas utiliser /leave pendant 30 secondes apres un combat ou une attaque.");
            return true;
        }
        World fallback = Bukkit.getWorld("world");
        if (fallback == null) {
            say(player, "Le monde principal est introuvable.");
            return true;
        }
        player.teleport(fallback.getSpawnLocation());
        say(player, "Retour vers le monde principal.");
        return true;
    }
    private boolean canManage(Village v, Player p) { return p.hasPermission("townia.village.manage") && (v.role(p.getUniqueId()) == VillageRole.MAYOR || v.role(p.getUniqueId()) == VillageRole.VICE_MAYOR); }
    private boolean canClaim(Village v, Player p) { return v != null && p.hasPermission("townia.village.claim") && v.isMember(p.getUniqueId()) && v.allows(p.getUniqueId(), fr.townia.model.VillageAction.CLAIM); }
    private int maximumClaims(Village village) { return village.members().size() * plugin.getConfig().getInt("claims-per-member", 8); }
    private void claimConflict(Player player, Village village, Village owner) {
        say(player, "Impossible de claim ce chunk : il appartient deja au village " + owner.name() + ".");
        player.sendActionBar(ChatColor.RED + "Zone deja claim par " + owner.name());
        if (!owner.id().equals(village.id())) {
            Player mayor = Bukkit.getPlayer(owner.mayor());
            if (mayor != null && !mayor.equals(player)) say(mayor, "Alerte : " + player.getName() + " a tente de claim un chunk de votre village.");
        }
    }
    private boolean denied(CommandSender sender) { say(sender, "You do not have permission for this action."); return true; }
    private boolean usage(CommandSender sender, String text) { say(sender, "Usage: /village " + text); return true; }
}
