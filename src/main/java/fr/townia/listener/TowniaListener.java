package fr.townia.listener;

import fr.townia.TowniaPlugin;
import fr.townia.model.Claim;
import fr.townia.model.Village;
import fr.townia.model.VillagePermissions;
import fr.townia.model.VillageRole;
import fr.townia.model.VillageAction;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.block.Action;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TowniaListener implements Listener {
    private final TowniaPlugin plugin;
    private final Map<UUID, String> lastZones = new HashMap<>();
    public TowniaListener(TowniaPlugin plugin) { this.plugin = plugin; }
    @EventHandler public void join(PlayerJoinEvent event) { plugin.activity().join(event.getPlayer()); plugin.skins().restore(event.getPlayer()); showZone(event.getPlayer(), event.getPlayer().getLocation(), true); }
    @EventHandler public void quit(PlayerQuitEvent event) { plugin.activity().quit(event.getPlayer()); lastZones.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) { showZone(event.getPlayer(), event.getPlayer().getLocation(), true); }
    @EventHandler public void move(PlayerMoveEvent event) { if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) { plugin.activity().touch(event.getPlayer()); if (event.getFrom().getChunk().getX() != event.getTo().getChunk().getX() || event.getFrom().getChunk().getZ() != event.getTo().getChunk().getZ()) showZone(event.getPlayer(), event.getTo(), false); } }
    @EventHandler public void breakBlock(BlockBreakEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getChunk(), VillageAction.BUILD)) event.setCancelled(true);
    }
    @EventHandler public void placeBlock(BlockPlaceEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getChunk(), VillageAction.PLACE)) event.setCancelled(true);
    }
    @EventHandler public void burn(BlockBurnEvent event) { if (plugin.villages().owner(new Claim(event.getBlock().getWorld().getName(), event.getBlock().getChunk().getX(), event.getBlock().getChunk().getZ())) != null) event.setCancelled(true); }
    @EventHandler public void ignite(BlockIgniteEvent event) { if (event.getPlayer() != null && !allowed(event.getPlayer(), event.getBlock().getChunk(), VillageAction.FIRE)) event.setCancelled(true); }
    @EventHandler public void damage(EntityDamageEvent event) { if (!(event instanceof EntityDamageByEntityEvent combat) || !(combat.getDamager() instanceof Player player)) return; VillageAction action = combat.getEntity() instanceof Player ? VillageAction.PVP : VillageAction.PVE; if (!allowed(player, combat.getEntity().getLocation().getChunk(), action)) event.setCancelled(true); }
    @EventHandler public void pickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player && !allowed(player, event.getItem().getChunk(), VillageAction.PICKUP)) event.setCancelled(true); }
    @EventHandler public void drop(BlockDropItemEvent event) {
        // Les blocs cassés doivent toujours laisser tomber leurs items, même sans permission "Jeter".
        // La restriction "Jeter" ne doit s'appliquer qu'au drop manuel d'objets par le joueur.
    }
    @EventHandler public void drop(PlayerDropItemEvent event) {
        if (!allowed(event.getPlayer(), event.getPlayer().getChunk(), VillageAction.DROP)) event.setCancelled(true);
    }
    @EventHandler public void throwPotion(ProjectileLaunchEvent event) { if (event.getEntity() instanceof ThrownPotion potion && potion.getShooter() instanceof Player player && !allowed(player, potion.getChunk(), VillageAction.THROW_POTIONS)) event.setCancelled(true); }
    @EventHandler public void openContainer(InventoryOpenEvent event) { if (event.getPlayer() instanceof Player player && event.getInventory().getLocation() != null && isStorage(event.getInventory().getType()) && !allowed(player, event.getInventory().getLocation().getChunk(), VillageAction.OPEN_CHEST)) event.setCancelled(true); }
    @EventHandler public void interact(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        VillageAction action = VillageAction.BUILD;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (block.getBlockData() instanceof Openable) action = VillageAction.USE_DOOR;
            else if (block.getType().name().endsWith("BUTTON")) action = VillageAction.USE_BUTTON;
            else if (block.getType().name().endsWith("LEVER")) action = VillageAction.USE_LEVER;
        }
        if (!allowed(event.getPlayer(), block.getChunk(), action)) event.setCancelled(true);
    }

    private boolean allowed(Player player, org.bukkit.Chunk chunk, VillageAction action) {
        if (player.hasPermission("townia.bypass")) return true;
        Village village = plugin.villages().owner(new Claim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
        if (village == null) return true;
        if (village.excluded().contains(player.getUniqueId())) return false;
        boolean result = village.allows(player.getUniqueId(), action);
        if (!result) player.sendActionBar(ChatColor.RED + "Action interdite dans le claim de " + village.name());
        return result;
    }

    private boolean isStorage(InventoryType type) {
        return switch (type) {
            case CHEST, ENDER_CHEST, BARREL, HOPPER, DROPPER, DISPENSER, SHULKER_BOX -> true;
            default -> false;
        };
    }

    private void showZone(Player player, Location location, boolean force) {
        World world = location.getWorld();
        if (world == null) return;
        Claim claim = Claim.at(world.getChunkAt(location));
        Village owner = plugin.villages().owner(claim);
        Village ownVillage = plugin.villages().byPlayer(player.getUniqueId());
        String zoneKey = world.getName() + ":" + (owner == null ? "wilderness" : "village:" + owner.id());
        if (!force && zoneKey.equals(lastZones.get(player.getUniqueId()))) return;
        lastZones.put(player.getUniqueId(), zoneKey);
        String coordinates = "Chunk " + claim.chunkX() + ", " + claim.chunkZ() + " | Position " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
        if (owner == null) {
            player.sendTitle(ChatColor.GREEN + "Zone libre", ChatColor.GRAY + coordinates, 5, 30, 10);
        } else if (ownVillage != null && ownVillage.id().equals(owner.id())) {
            player.sendTitle(ChatColor.GOLD + owner.name(), ChatColor.GREEN + coordinates, 5, 30, 10);
        } else {
            player.sendTitle(ChatColor.RED + owner.name(), ChatColor.GRAY + coordinates, 5, 30, 10);
        }
    }
}
