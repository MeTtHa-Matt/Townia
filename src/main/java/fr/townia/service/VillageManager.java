package fr.townia.service;

import fr.townia.model.Claim;
import fr.townia.model.Village;
import fr.townia.model.VillageRole;
import fr.townia.model.VillagePermissions;
import fr.townia.model.VillageAction;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VillageManager {
    private final File file;
    private final Map<UUID, Village> villages = new HashMap<>();
    private final Map<Claim, UUID> claimOwners = new HashMap<>();
    private final Map<Claim, UUID> claimers = new HashMap<>();
    private final Map<Claim, String> claimPositions = new HashMap<>();

    public VillageManager(File file) { this.file = file; }

    public Map<UUID, Village> villages() { return villages; }
    public Village byId(UUID id) { return villages.get(id); }
    public Village byPlayer(UUID player) {
        return villages.values().stream().filter(v -> v.isMember(player)).findFirst().orElse(null);
    }
    public Village byName(String name) {
        return villages.values().stream().filter(v -> v.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public Village create(String name, UUID mayor) {
        if (byName(name) != null || byPlayer(mayor) != null) return null;
        Village village = new Village(UUID.randomUUID(), name, mayor);
        villages.put(village.id(), village);
        return village;
    }
    public boolean claim(Village village, Claim claim) {
        return claim(village, claim, village.mayor());
    }

    public boolean claim(Village village, Claim claim, UUID claimer) {
        return claim(village, claim, claimer, null);
    }

    public boolean claim(Village village, Claim claim, UUID claimer, Location position) {
        if (claimOwners.containsKey(claim)) return false;
        village.claims().add(claim);
        claimOwners.put(claim, village.id());
        claimers.put(claim, claimer);
        if (position != null) claimPositions.put(claim, position.getBlockX() + "," + position.getBlockY() + "," + position.getBlockZ());
        return true;
    }
    public boolean unclaim(Village village, Claim claim) {
        if (!village.claims().remove(claim)) return false;
        claimOwners.remove(claim);
        claimers.remove(claim);
        claimPositions.remove(claim);
        return true;
    }
    public Village owner(Claim claim) {
        UUID owner = claimOwners.get(claim);
        return owner == null ? null : villages.get(owner);
    }

    public UUID claimer(Claim claim) { return claimers.get(claim); }
    public String claimPosition(Claim claim) { return claimPositions.getOrDefault(claim, (claim.chunkX() << 4) + ",?," + (claim.chunkZ() << 4)); }

    public int removeClaimsBy(Village village, UUID player, int maximum) {
        int removed = 0;
        for (Claim claim : new java.util.ArrayList<>(village.claims())) {
            if (removed >= maximum || !player.equals(claimers.get(claim))) continue;
            unclaim(village, claim);
            removed++;
        }
        return removed;
    }

    public int trimClaimsTo(Village village, int maximum) {
        int removed = 0;
        for (Claim claim : new java.util.ArrayList<>(village.claims())) {
            if (village.claims().size() <= maximum) break;
            unclaim(village, claim);
            removed++;
        }
        return removed;
    }

    public boolean delete(Village village) {
        if (!villages.remove(village.id(), village)) return false;
        village.claims().forEach(claim -> { claimOwners.remove(claim); claimers.remove(claim); claimPositions.remove(claim); });
        return true;
    }

    public void load() {
        villages.clear(); claimOwners.clear(); claimers.clear(); claimPositions.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("villages");
        if (section == null) return;
        for (String idValue : section.getKeys(false)) {
            UUID id = UUID.fromString(idValue);
            String path = "villages." + idValue;
            UUID mayor = UUID.fromString(yaml.getString(path + ".mayor"));
            Village village = new Village(id, yaml.getString(path + ".name", "Village"), mayor);
            village.setOpen(yaml.getBoolean(path + ".open", false));
            String homeWorld = yaml.getString(path + ".home.world");
            if (homeWorld != null && !homeWorld.isBlank()) {
                village.setHome(new Location(Bukkit.getWorld(homeWorld),
                        yaml.getDouble(path + ".home.x", 0.0),
                        yaml.getDouble(path + ".home.y", 64.0),
                        yaml.getDouble(path + ".home.z", 0.0),
                        (float) yaml.getDouble(path + ".home.yaw", 0.0),
                        (float) yaml.getDouble(path + ".home.pitch", 0.0)));
            } else {
                double x = yaml.getDouble(path + ".home.x", 0.0);
                double y = yaml.getDouble(path + ".home.y", 64.0);
                double z = yaml.getDouble(path + ".home.z", 0.0);
                if (yaml.contains(path + ".home.x") || yaml.contains(path + ".home.y") || yaml.contains(path + ".home.z")) {
                    village.setHome(new Location(null, x, y, z, (float) yaml.getDouble(path + ".home.yaw", 0.0), (float) yaml.getDouble(path + ".home.pitch", 0.0)));
                }
            }
            village.members().clear();
            for (String entry : yaml.getStringList(path + ".members")) {
                String[] values = entry.split(":", 2);
                village.members().put(UUID.fromString(values[0]), VillageRole.valueOf(values[1]));
            }
            village.invited().addAll(yaml.getStringList(path + ".invited").stream().map(UUID::fromString).toList());
            village.excluded().addAll(yaml.getStringList(path + ".excluded").stream().map(UUID::fromString).toList());
            for (VillageRole role : VillageRole.values()) village.setPermissions(role, readPermissions(yaml, path + ".permissions." + role.name(), VillagePermissions.defaults(role)));
            village.setNonMemberPermissions(readPermissions(yaml, path + ".permissions.NON_MEMBER", VillagePermissions.defaultsForNonMembers()));
            for (String role : yaml.getConfigurationSection(path + ".custom-roles") == null ? java.util.List.<String>of() : yaml.getConfigurationSection(path + ".custom-roles").getKeys(false)) {
                village.customRoles().put(role, readPermissions(yaml, path + ".custom-roles." + role, VillagePermissions.defaultsForNonMembers()));
            }
            for (String entry : yaml.getStringList(path + ".custom-role-assignments")) {
                String[] values = entry.split(":", 2);
                village.customRoleAssignments().put(UUID.fromString(values[0]), values[1]);
            }
            for (String value : yaml.getStringList(path + ".claims")) {
                String[] values = value.split(":", 3);
                Claim claim = new Claim(values[0], Integer.parseInt(values[1]), Integer.parseInt(values[2]));
                String claimer = yaml.getString(path + ".claimers." + values[0] + "." + values[1] + "." + values[2]);
                claim(village, claim, claimer == null ? mayor : UUID.fromString(claimer));
                String position = yaml.getString(path + ".claim-positions." + values[0] + "." + values[1] + "." + values[2]);
                if (position != null) claimPositions.put(claim, position);
            }
            villages.put(id, village);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Village village : villages.values()) {
            String path = "villages." + village.id();
            yaml.set(path + ".name", village.name());
            yaml.set(path + ".mayor", village.mayor().toString());
            yaml.set(path + ".open", village.open());
            if (village.home() != null) {
                yaml.set(path + ".home.world", village.home().getWorld() == null ? null : village.home().getWorld().getName());
                yaml.set(path + ".home.x", village.home().getX());
                yaml.set(path + ".home.y", village.home().getY());
                yaml.set(path + ".home.z", village.home().getZ());
                yaml.set(path + ".home.yaw", village.home().getYaw());
                yaml.set(path + ".home.pitch", village.home().getPitch());
            }
            yaml.set(path + ".members", village.members().entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).toList());
            yaml.set(path + ".invited", village.invited().stream().map(UUID::toString).toList());
            yaml.set(path + ".excluded", village.excluded().stream().map(UUID::toString).toList());
            for (VillageRole role : VillageRole.values()) writePermissions(yaml, path + ".permissions." + role.name(), village.permissions().get(role));
            writePermissions(yaml, path + ".permissions.NON_MEMBER", village.nonMemberPermissions());
            for (Map.Entry<String, VillagePermissions> role : village.customRoles().entrySet()) writePermissions(yaml, path + ".custom-roles." + role.getKey(), role.getValue());
            yaml.set(path + ".custom-role-assignments", village.customRoleAssignments().entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue()).toList());
            yaml.set(path + ".claims", village.claims().stream().map(c -> c.world() + ":" + c.chunkX() + ":" + c.chunkZ()).toList());
            for (Claim claim : village.claims()) yaml.set(path + ".claimers." + claim.world() + "." + claim.chunkX() + "." + claim.chunkZ(), claimers.getOrDefault(claim, village.mayor()).toString());
            for (Claim claim : village.claims()) yaml.set(path + ".claim-positions." + claim.world() + "." + claim.chunkX() + "." + claim.chunkZ(), claimPositions.getOrDefault(claim, (claim.chunkX() << 4) + ",?," + (claim.chunkZ() << 4)));
        }
        try { yaml.save(file); } catch (IOException exception) { throw new IllegalStateException("Unable to save village data", exception); }
    }

    private VillagePermissions readPermissions(YamlConfiguration yaml, String path, VillagePermissions defaults) {
        return new VillagePermissions(
                yaml.getBoolean(path + ".pvp", defaults.pvp()),
                yaml.getBoolean(path + ".pve", defaults.pve()),
                yaml.getBoolean(path + ".build", defaults.build()),
                yaml.getBoolean(path + ".place", defaults.place()),
                yaml.getBoolean(path + ".claim", defaults.claim()),
                yaml.getBoolean(path + ".pickup", defaults.pickup()),
                yaml.getBoolean(path + ".drop", defaults.drop()),
                yaml.getBoolean(path + ".throwPotions", defaults.throwPotions()),
                yaml.getBoolean(path + ".fire", defaults.fire()),
                yaml.getBoolean(path + ".openChest", defaults.openChest()),
                yaml.getBoolean(path + ".useDoor", defaults.useDoor()),
                yaml.getBoolean(path + ".useButton", defaults.useButton()),
                yaml.getBoolean(path + ".useLever", defaults.useLever()),
                yaml.getBoolean(path + ".home", defaults.home()),
                yaml.getBoolean(path + ".createRoles", defaults.createRoles())
        );
    }

    private void writePermissions(YamlConfiguration yaml, String path, VillagePermissions permissions) {
        yaml.set(path + ".pvp", permissions.pvp());
        yaml.set(path + ".pve", permissions.pve());
        yaml.set(path + ".build", permissions.build());
        yaml.set(path + ".place", permissions.place());
        yaml.set(path + ".claim", permissions.claim());
        yaml.set(path + ".pickup", permissions.pickup());
        yaml.set(path + ".drop", permissions.drop());
        yaml.set(path + ".throwPotions", permissions.throwPotions());
        yaml.set(path + ".fire", permissions.fire());
        yaml.set(path + ".openChest", permissions.openChest());
        yaml.set(path + ".useDoor", permissions.useDoor());
        yaml.set(path + ".useButton", permissions.useButton());
        yaml.set(path + ".useLever", permissions.useLever());
        yaml.set(path + ".home", permissions.home());
        yaml.set(path + ".createRoles", permissions.createRoles());
    }
}
