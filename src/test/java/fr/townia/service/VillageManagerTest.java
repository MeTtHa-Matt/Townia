package fr.townia.service;

import fr.townia.TowniaPlugin;
import fr.townia.gui.VillageMenu;
import fr.townia.model.Claim;
import fr.townia.model.VillageAction;
import fr.townia.model.Village;
import fr.townia.model.VillagePermissions;
import fr.townia.model.VillageRole;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VillageManagerTest {
    @Test void cannotOverlapClaims() {
        VillageManager manager = new VillageManager(new File("build/test-data.yml"));
        Village first = manager.create("First", UUID.randomUUID());
        Village second = manager.create("Second", UUID.randomUUID());
        Claim claim = new Claim("world", 1, 2);
        assertTrue(manager.claim(first, claim));
        assertFalse(manager.claim(second, claim));
        assertSame(first, manager.owner(claim));
    }
    @Test void rejectsDuplicateVillageNameAndPlayer() {
        VillageManager manager = new VillageManager(new File("build/test-data.yml"));
        UUID mayor = UUID.randomUUID();
        assertNotNull(manager.create("First", mayor));
        assertNull(manager.create("first", UUID.randomUUID()));
        assertNull(manager.create("Second", mayor));
    }

    @Test void defaultsProtectMembersAndNonMembersFromBuilding() {
        Village village = new Village(UUID.randomUUID(), "Town", UUID.randomUUID());
        assertFalse(village.permissions().get(VillageRole.MEMBER).allows(VillageAction.BUILD));
        assertFalse(village.nonMemberPermissions().allows(VillageAction.BUILD));
        assertTrue(village.permissions().get(VillageRole.VICE_MAYOR).allows(VillageAction.BUILD));
    }

    @Test void uuidsMustBeComparedByValueNotIdentity() {
        UUID mayor = UUID.randomUUID();
        UUID sameValueFromString = UUID.fromString(mayor.toString());
        assertEquals(mayor, sameValueFromString);
        assertNotSame(mayor, sameValueFromString);
        assertTrue(mayor.equals(sameValueFromString));
    }

    @Test void inviteTargetsExcludeMembersAndExcludedPlayers() {
        UUID self = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID excluded = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        UUID outsiderTwo = UUID.randomUUID();

        List<UUID> result = Village.eligibleInviteTargets(self,
                List.of(self, member, excluded, outsider, outsiderTwo),
                List.of(member),
                List.of(excluded));

        assertEquals(List.of(outsider, outsiderTwo), result);
    }

    @Test void customRoleAssignmentsOverrideDefaultRoleLabel() {
        UUID player = UUID.randomUUID();
        Village village = new Village(UUID.randomUUID(), "Town", UUID.randomUUID());
        village.addMember(player);
        village.customRoleAssignments().put(player, "Builder");

        assertEquals("Builder", village.customRoleAssignments().get(player));
        assertEquals("Builder", village.customRoleAssignments().get(player));
    }

    @Test void placingBlocksHasItsOwnPermissionSeparateFromBreaking() {
        VillagePermissions mayorPermissions = VillagePermissions.defaults(VillageRole.MAYOR);
        VillagePermissions memberPermissions = VillagePermissions.defaults(VillageRole.MEMBER);

        assertTrue(mayorPermissions.allows(VillageAction.BUILD));
        assertTrue(mayorPermissions.allows(VillageAction.PLACE));
        assertFalse(memberPermissions.allows(VillageAction.BUILD));
        assertFalse(memberPermissions.allows(VillageAction.PLACE));
    }

    @Test void claimOutlineContainsChunkPerimeterPoints() {
        Claim claim = new Claim("world", 2, 3);
        List<int[]> points = VillageMenu.chunkOutlinePoints(claim);

        assertFalse(points.isEmpty());
        assertTrue(points.stream().anyMatch(point -> point[0] == 32 && point[2] == 48));
        assertTrue(points.stream().anyMatch(point -> point[0] == 47 && point[2] == 48));
        assertTrue(points.stream().anyMatch(point -> point[0] == 32 && point[2] == 63));
        assertTrue(points.stream().anyMatch(point -> point[0] == 47 && point[2] == 63));
    }

    @Test void customWorldInventorySyncDefaultsToDisabled() {
        assertFalse(TowniaPlugin.defaultWorldInventorySync());
        assertTrue(TowniaPlugin.resolveWorldInventorySync(Boolean.TRUE));
        assertFalse(TowniaPlugin.resolveWorldInventorySync(Boolean.FALSE));
    }

    @Test void inventorySyncFlagsDefineResetBehaviourPerWorld() {
        assertFalse(TowniaPlugin.defaultWorldInventorySync());
        assertTrue(TowniaPlugin.resolveWorldInventorySync(Boolean.TRUE));
        assertFalse(TowniaPlugin.resolveWorldInventorySync(Boolean.FALSE));
        assertTrue(TowniaPlugin.shouldKeepInventory(true));
        assertFalse(TowniaPlugin.shouldKeepInventory(false));
    }

    @Test void defaultServerWorldsAreRecognizedAsSystemWorlds() {
        assertTrue(TowniaPlugin.isProtectedWorldForDeletion("world"));
        assertFalse(TowniaPlugin.isProtectedWorldForDeletion("world_nether"));
        assertFalse(TowniaPlugin.isProtectedWorldForDeletion("world_the_end"));
        assertFalse(TowniaPlugin.isProtectedWorldForDeletion("townia_survival"));
    }

    @Test void villageHomeIsStoredAndRestoredAsSingleLocation() {
        VillageManager manager = new VillageManager(new File("build/test-home-data.yml"));
        Village village = manager.create("HomeTown", UUID.randomUUID());
        assertNotNull(village);

        Location home = new Location(null, 42.5, 64.0, 18.5, 90.0f, 12.0f);
        village.setHome(home);

        manager.save();
        manager.load();

        Village reloaded = manager.byName("HomeTown");
        assertNotNull(reloaded);
        assertNotNull(reloaded.home());
        assertEquals(42.5, reloaded.home().getX(), 0.001);
        assertEquals(64.0, reloaded.home().getY(), 0.001);
        assertEquals(18.5, reloaded.home().getZ(), 0.001);
        assertEquals(90.0f, reloaded.home().getYaw(), 0.001f);
    }

    @Test void villageHomeRequiresAnExplicitVillagePermission() {
        VillagePermissions mayorPermissions = VillagePermissions.defaults(VillageRole.MAYOR);
        VillagePermissions memberPermissions = VillagePermissions.defaults(VillageRole.MEMBER);

        assertTrue(mayorPermissions.allows(VillageAction.HOME));
        assertTrue(memberPermissions.allows(VillageAction.HOME));
    }

    @Test void inventoryPolicySavesOnExitAndRestoresOnlyForSyncWorlds() {
        assertTrue(TowniaPlugin.shouldSaveInventoryOnExit(false));
        assertTrue(TowniaPlugin.shouldSaveInventoryOnExit(true));
        assertTrue(TowniaPlugin.shouldRestoreInventoryOnEnter(true));
        assertFalse(TowniaPlugin.shouldRestoreInventoryOnEnter(false));
        assertTrue(TowniaPlugin.shouldInitializeSavedWorldInventory(true, false));
        assertFalse(TowniaPlugin.shouldInitializeSavedWorldInventory(true, true));
        assertFalse(TowniaPlugin.shouldInitializeSavedWorldInventory(false, false));
    }

}
