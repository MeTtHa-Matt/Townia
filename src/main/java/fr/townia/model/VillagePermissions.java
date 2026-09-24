package fr.townia.model;

public record VillagePermissions(boolean pvp, boolean pve, boolean build, boolean place, boolean claim, boolean pickup, boolean drop, boolean throwPotions, boolean fire, boolean openChest, boolean useDoor, boolean useButton, boolean useLever, boolean home, boolean createRoles) {
    public static VillagePermissions defaults(VillageRole role) {
        return switch (role) {
            case MAYOR, VICE_MAYOR -> new VillagePermissions(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true);
            case MEMBER -> new VillagePermissions(false, true, false, false, false, true, true, true, false, false, true, true, false, true, false);
        };
    }

    public static VillagePermissions defaultsForNonMembers() {
        return new VillagePermissions(false, false, false, false, false, false, false, false, false, false, false, false, false, false, false);
    }

    public boolean allows(VillageAction action) {
        return switch (action) {
            case PVP -> pvp;
            case PVE -> pve;
            case BUILD -> build;
            case PLACE -> place;
            case CLAIM -> claim;
            case PICKUP -> pickup;
            case DROP -> drop;
            case THROW_POTIONS -> throwPotions;
            case FIRE -> fire;
            case OPEN_CHEST -> openChest;
            case USE_DOOR -> useDoor;
            case USE_BUTTON -> useButton;
            case USE_LEVER -> useLever;
            case HOME -> home;
            case CREATE_ROLES -> createRoles;
        };
    }

    public VillagePermissions with(VillageAction action, boolean allowed) {
        return switch (action) {
            case PVP -> new VillagePermissions(allowed, pve, build, place, claim, pickup, drop, throwPotions, fire, openChest, useDoor, useButton, useLever, home, createRoles);
            case PVE -> new VillagePermissions(pvp, allowed, build, place, claim, pickup, drop, throwPotions, fire, openChest, useDoor, useButton, useLever, home, createRoles);
            case BUILD -> new VillagePermissions(pvp, pve, allowed, place, claim, pickup, drop, throwPotions, fire, openChest, useDoor, useButton, useLever, home, createRoles);
            case PLACE -> new VillagePermissions(pvp, pve, build, allowed, claim, pickup, drop, throwPotions, fire, openChest, useDoor, useButton, useLever, home, createRoles);
            case CLAIM -> new VillagePermissions(pvp, pve, build, place, allowed, pickup, drop, throwPotions, fire, openChest, useDoor, useButton, useLever, home, createRoles);
            case PICKUP -> new VillagePermissions(pvp, pve, build, place, claim, allowed, drop, throwPotions, fire, openChest, useDoor, useButton, useLever, home, createRoles);
            case DROP -> new VillagePermissions(pvp, pve, build, place, claim, pickup, allowed, throwPotions, fire, openChest, useDoor, useButton, useLever, home, createRoles);
            case THROW_POTIONS -> new VillagePermissions(pvp, pve, build, place, claim, pickup, drop, allowed, fire, openChest, useDoor, useButton, useLever, home, createRoles);
            case FIRE -> new VillagePermissions(pvp, pve, build, place, claim, pickup, drop, throwPotions, allowed, openChest, useDoor, useButton, useLever, home, createRoles);
            case OPEN_CHEST -> new VillagePermissions(pvp, pve, build, place, claim, pickup, drop, throwPotions, fire, allowed, useDoor, useButton, useLever, home, createRoles);
            case USE_DOOR -> new VillagePermissions(pvp, pve, build, place, claim, pickup, drop, throwPotions, fire, openChest, allowed, useButton, useLever, home, createRoles);
            case USE_BUTTON -> new VillagePermissions(pvp, pve, build, place, claim, pickup, drop, throwPotions, fire, openChest, useDoor, allowed, useLever, home, createRoles);
            case USE_LEVER -> new VillagePermissions(pvp, pve, build, place, claim, pickup, drop, throwPotions, fire, openChest, useDoor, useButton, allowed, home, createRoles);
            case HOME -> new VillagePermissions(pvp, pve, build, place, claim, pickup, drop, throwPotions, fire, openChest, useDoor, useButton, useLever, allowed, createRoles);
            case CREATE_ROLES -> new VillagePermissions(pvp, pve, build, place, claim, pickup, drop, throwPotions, fire, openChest, useDoor, useButton, useLever, home, allowed);
        };
    }
}
