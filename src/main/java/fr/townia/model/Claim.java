package fr.townia.model;

import java.util.Objects;
import org.bukkit.Chunk;

public record Claim(String world, int chunkX, int chunkZ) {
    public Claim {
        Objects.requireNonNull(world, "world");
    }

    public static Claim at(Chunk chunk) {
        return new Claim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }
}
