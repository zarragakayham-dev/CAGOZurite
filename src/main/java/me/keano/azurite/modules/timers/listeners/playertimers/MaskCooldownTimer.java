package me.keano.azurite.modules.timers.listeners.playertimers;

import lombok.Getter;
import me.keano.azurite.modules.board.extra.ActionBarConfig;
import me.keano.azurite.modules.timers.TimerManager;
import me.keano.azurite.modules.timers.type.PlayerTimer;
import me.keano.azurite.utils.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Copyright (c) 2025. Keano
 * Aggregate mask cooldown timer. Each mask ability cooldown is tracked
 * per-player with a displayName used in the ActionBar.
 */
@Getter
public class MaskCooldownTimer extends PlayerTimer {

    private final Map<UUID, String> displayNameCache = new HashMap<>();

    public MaskCooldownTimer(TimerManager manager) {
        super(
                manager,
                ActionBarConfig.MASK,
                false,
                "MaskCooldown",
                "PLAYER_TIMERS.MASK",
                ""
        );
    }

    public void applyTimer(Player player, long durationMillis, String displayName) {
        applyTimer(player, durationMillis);
        displayNameCache.put(player.getUniqueId(), displayName);
    }

    @Override
    public void removeTimer(Player player) {
        super.removeTimer(player);
        displayNameCache.remove(player.getUniqueId());
    }

    @Override
    public void tick() {
        timerCache.entrySet().removeIf(entry -> {
            if (entry.getValue() < System.currentTimeMillis()) {
                displayNameCache.remove(entry.getKey());
                Bukkit.getServer().getPluginManager().callEvent(new me.keano.azurite.modules.timers.event.AsyncTimerExpireEvent(this, entry.getKey()));
                return true;
            }
            return false;
        });
    }

    public String getDisplayName(Player player) {
        String name = displayNameCache.get(player.getUniqueId());
        if (name == null) return "Mask";
        return name;
    }

    public String getActionBarLine(Player player) {
        if (!hasTimer(player)) return null;
        if (getActionBar() == null || !getActionBar().isEnabled()) return null;
        String line = getActionBar().getLine();
        String displayName = getDisplayName(player);
        String time = Formatter.getRemaining(getRemaining(player), false);
        return line
                .replace("%mask%", displayName)
                .replace("%time%", time);
    }

    public boolean hasCooldown(Player player, String abilityName) {
        if (!hasTimer(player)) return false;
        String name = displayNameCache.get(player.getUniqueId());
        return name != null && name.equalsIgnoreCase(abilityName);
    }
}
