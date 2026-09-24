package fr.townia.model;

public enum VillageAction {
    PVP("PVP"),
    PVE("PVE"),
    BUILD("Casser"),
    PLACE("Poser"),
    CLAIM("Claim des chunks"),
    PICKUP("Ramasser"),
    DROP("Jeter"),
    THROW_POTIONS("Jeter des potions"),
    FIRE("Feu"),
    OPEN_CHEST("Ouvrir les coffres"),
    USE_DOOR("Utiliser les portes"),
    USE_BUTTON("Utiliser les boutons"),
    USE_LEVER("Utiliser les leviers"),
    HOME("Se teleporter au home"),
    CREATE_ROLES("Creer des roles");

    private final String label;
    VillageAction(String label) { this.label = label; }
    public String label() { return label; }
}
