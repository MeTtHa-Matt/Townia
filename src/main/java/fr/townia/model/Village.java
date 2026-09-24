package fr.townia.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Location;

public final class Village {
    private final UUID id;
    private String name;
    private UUID mayor;
    private boolean open;
    private Location home;
    private final Map<UUID, VillageRole> members = new LinkedHashMap<>();
    private final Set<UUID> invited = new java.util.HashSet<>();
    private final Set<UUID> excluded = new java.util.HashSet<>();
    private final Set<Claim> claims = new java.util.HashSet<>();
    private final EnumMap<VillageRole, VillagePermissions> permissions = new EnumMap<>(VillageRole.class);
    private final Map<String, VillagePermissions> customRoles = new LinkedHashMap<>();
    private final Map<UUID, String> customRoleAssignments = new HashMap<>();
    private VillagePermissions nonMemberPermissions = VillagePermissions.defaultsForNonMembers();

    public Village(UUID id, String name, UUID mayor) {
        this.id = id;
        this.name = name;
        this.mayor = mayor;
        members.put(mayor, VillageRole.MAYOR);
        for (VillageRole role : VillageRole.values()) permissions.put(role, VillagePermissions.defaults(role));
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void rename(String name) { this.name = name; }
    public UUID mayor() { return mayor; }
    public boolean open() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public Location home() { return home == null ? null : home.clone(); }
    public void setHome(Location home) { this.home = home == null ? null : home.clone(); }
    public Map<UUID, VillageRole> members() { return members; }
    public Set<UUID> invited() { return invited; }
    public Set<UUID> excluded() { return excluded; }
    public Set<Claim> claims() { return claims; }
    public EnumMap<VillageRole, VillagePermissions> permissions() { return permissions; }
    public VillagePermissions nonMemberPermissions() { return nonMemberPermissions; }
    public void setNonMemberPermissions(VillagePermissions permissions) { nonMemberPermissions = permissions; }
    public void setPermissions(VillageRole role, VillagePermissions permissions) { this.permissions.put(role, permissions); }
    public Map<String, VillagePermissions> customRoles() { return customRoles; }
    public Map<UUID, String> customRoleAssignments() { return customRoleAssignments; }
    public boolean allows(UUID player, VillageAction action) {
        if (excluded.contains(player)) return false;
        VillagePermissions rolePermissions = customRoleAssignments.containsKey(player)
                ? customRoles.get(customRoleAssignments.get(player))
                : permissions.getOrDefault(role(player), nonMemberPermissions);
        return rolePermissions != null && rolePermissions.allows(action);
    }

    public VillageRole role(UUID player) { return members.get(player); }
    public boolean isMember(UUID player) { return members.containsKey(player); }
    public void addMember(UUID player) { members.put(player, VillageRole.MEMBER); }
    public void setRole(UUID player, VillageRole role) {
        if (role == VillageRole.MAYOR && !mayor.equals(player)) return;
        members.put(player, role);
    }

    public static List<UUID> eligibleInviteTargets(UUID self, Iterable<UUID> onlinePlayers, Iterable<UUID> villageMembers, Iterable<UUID> excludedPlayers) {
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
        return candidates;
    }
}
