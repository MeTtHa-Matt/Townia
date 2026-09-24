package fr.townia.service;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ActivityService {
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, Long> joinedAt = new HashMap<>();
    private final Map<UUID, Long> playtimeSeconds = new HashMap<>();
    private final long timeoutMillis;

    public ActivityService(long timeoutMinutes) { timeoutMillis = timeoutMinutes * 60_000L; }
    public void join(Player player) { joinedAt.put(player.getUniqueId(), System.currentTimeMillis()); touch(player); }
    public void quit(Player player) { flush(player); joinedAt.remove(player.getUniqueId()); lastActivity.remove(player.getUniqueId()); }
    public void touch(Player player) { lastActivity.put(player.getUniqueId(), System.currentTimeMillis()); }
    public boolean isAfk(Player player) { return System.currentTimeMillis() - lastActivity.getOrDefault(player.getUniqueId(), 0L) > timeoutMillis; }
    public long playtime(UUID player) { return playtimeSeconds.getOrDefault(player, 0L); }
    public void flush(Player player) {
        Long start = joinedAt.get(player.getUniqueId());
        if (start != null) playtimeSeconds.merge(player.getUniqueId(), (System.currentTimeMillis() - start) / 1000L, Long::sum);
        joinedAt.put(player.getUniqueId(), System.currentTimeMillis());
    }
}
