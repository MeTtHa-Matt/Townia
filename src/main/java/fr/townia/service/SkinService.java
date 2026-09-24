package fr.townia.service;

import org.bukkit.entity.Player;

public interface SkinService {
    void restore(Player player);

    static SkinService disabled() { return player -> {}; }
}
