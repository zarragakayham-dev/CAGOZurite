package me.keano.azurite.modules.masks;

import me.keano.azurite.modules.framework.Module;
import me.keano.azurite.modules.timers.listeners.playertimers.MaskCooldownTimer;
import me.keano.azurite.utils.CC;
import me.keano.azurite.utils.Formatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Copyright (c) 2025. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class MaskAbilityListener extends Module<MaskManager> {

    private final Random random = new Random();

    // Spiderman double-jump state tracking
    private static final long JUMP_WINDOW_MS = 1500L; // time window for second jump
    private final Map<UUID, Long> firstJumpTime = new HashMap<>();

    public MaskAbilityListener(MaskManager manager) {
        super(manager);
    }

    private MaskCooldownTimer getMaskTimer() {
        return getInstance().getTimerManager().getMaskCooldownTimer();
    }

    private boolean hasMask(Player player, String maskId) {
        if (player == null) return false;
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType() == Material.AIR) return false;
        String id = getManager().getMaskIdFromItem(helmet);
        return id != null && id.equalsIgnoreCase(maskId);
    }

    private boolean isOnCooldown(Player player, String displayName) {
        MaskCooldownTimer timer = getMaskTimer();
        if (timer == null) return false;
        return timer.hasCooldown(player, displayName);
    }

    private void setCooldown(Player player, String displayName, long durationMillis) {
        MaskCooldownTimer timer = getMaskTimer();
        if (timer == null) return;
        timer.applyTimer(player, durationMillis, displayName);
    }

    private long getCooldownRemaining(Player player) {
        MaskCooldownTimer timer = getMaskTimer();
        if (timer == null || !timer.hasTimer(player)) return 0L;
        return timer.getRemaining(player);
    }

    private String getCooldownMessage(String maskKey) {
        String path = "MASK_COOLDOWN." + maskKey.toUpperCase() + "_COOLDOWN";
        String msg = getLanguageConfig().getString(path);
        if (msg == null || msg.isEmpty()) {
            msg = "&cYou cannot use this for &c&l%time%&c.";
        }
        return msg;
    }

    private void sendCooldownMessage(Player player, String maskKey) {
        long remaining = getCooldownRemaining(player);
        String time = Formatter.getRemaining(remaining, false);
        player.sendMessage(CC.t(getCooldownMessage(maskKey)
                .replace("%time%", time)
                .replace("%seconds%", time)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player damaged = (Player) e.getEntity();

        // Venom: 10% chance to survive a lethal hit
        if (hasMask(damaged, "venom")) {
            if (damaged.getHealth() - e.getFinalDamage() <= 0) {
                if (random.nextInt(100) < 10) {
                    e.setCancelled(true);
                    damaged.setHealth(damaged.getMaxHealth());
                    damaged.sendMessage(CC.t("&a&lVENOM MASK &7- You survived a lethal hit!"));
                    damaged.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                }
            }
        }

        if (!(e.getDamager() instanceof Player)) return;
        Player damager = (Player) e.getDamager();

        // Spiderman: 6% chance to deal +10% extra damage
        if (hasMask(damager, "spiderman")) {
            if (random.nextInt(100) < 6) {
                double extraDamage = e.getDamage() * 0.10;
                e.setDamage(e.getDamage() + extraDamage);
                damager.sendMessage(CC.t("&c&lSPIDERMAN MASK &7- You dealt a critical hit!"));
            }
        }

        // Venom: 3% chance to receive Strength I for 5 seconds
        if (hasMask(damager, "venom")) {
            if (random.nextInt(100) < 3) {
                damager.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 100, 0));
                damager.sendMessage(CC.t("&c&lVENOM MASK &7- You received Strength I for 5 seconds!"));
            }
        }

        // Hulk: 3% chance to strike lightning and Strength II for 5 seconds
        if (hasMask(damager, "hulk")) {
            if (random.nextInt(100) < 3) {
                Location loc = damaged.getLocation();
                loc.getWorld().strikeLightning(loc);
                damager.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 100, 1));
                damager.sendMessage(CC.t("&c&lHULK MASK &7- You struck lightning and gained Strength II!"));
            }
        }

        // Thor: 5% chance to archer tag the enemy
        if (hasMask(damager, "thor")) {
            if (random.nextInt(100) < 5) {
                if (getInstance().getTimerManager().getArcherTagTimer() != null) {
                    getInstance().getTimerManager().getArcherTagTimer().applyTimer(damaged);
                    damager.sendMessage(CC.t("&c&lTHOR MASK &7- You archer tagged your enemy!"));
                }
            }
        }

        // Ironman: 5% chance to receive Speed III on a hit (1.7 style)
        if (hasMask(damager, "ironman")) {
            if (random.nextInt(100) < 5) {
                damager.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2));
                damager.sendMessage(CC.t("&c&lIRONMAN MASK &7- You received Speed III!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();

        // Spiderman: Double jump - two-stage jump detection
        if (hasMask(player, "spiderman")) {
            if (e.getAction().name().contains("RIGHT") && player.isSneaking()) {
                handleSpidermanJump(player);
            }
        }

        // Flash: Dash (shift + right-click, direction the player is looking)
        if (hasMask(player, "flash")) {
            if (e.getAction().name().contains("RIGHT") && player.isSneaking()) {
                if (!isOnCooldown(player, "Flash Dash")) {
                    Vector dir = player.getLocation().getDirection().normalize().multiply(3.0);
                    dir.setY(0.5);
                    player.setVelocity(dir);
                    player.sendMessage(CC.t("&c&lFLASH MASK &7- Dash activated!"));
                    setCooldown(player, "Flash Dash", 5 * 60 * 1000L);
                } else {
                    sendCooldownMessage(player, "flash");
                }
            }
        }

        // Thor: Speed 6 (amplifier 5) for 3 seconds (shift + right-click)
        if (hasMask(player, "thor")) {
            if (e.getAction().name().contains("RIGHT") && player.isSneaking()) {
                if (!isOnCooldown(player, "Thor Speed")) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 3, 5));
                    player.sendMessage(CC.t("&c&lTHOR MASK &7- Speed boost activated!"));
                    setCooldown(player, "Thor Speed", 5 * 60 * 1000L);
                } else {
                    sendCooldownMessage(player, "thor");
                }
            }
        }
    }

    private void handleSpidermanJump(Player player) {
        // First jump or re-arm: player must be on ground to arm the first jump
        if (player.isOnGround()) {
            // Arm the first jump - record the time
            firstJumpTime.put(player.getUniqueId(), System.currentTimeMillis());
            // Apply initial jump velocity
            Vector vel = player.getVelocity().clone().setY(1.0);
            player.setVelocity(vel);
            player.sendMessage(CC.t("&c&lSPIDERMAN MASK &7- Jump activated! Press again in air to double jump!"));
            return;
        }

        // Player is airborne - check for second jump within the window
        Long firstTime = firstJumpTime.get(player.getUniqueId());
        if (firstTime == null) {
            // Player is airborne but never armed - allow arming from air
            firstJumpTime.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        long elapsed = System.currentTimeMillis() - firstTime;
        if (elapsed > JUMP_WINDOW_MS) {
            // Window expired, re-arm
            firstJumpTime.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        // Second jump within window - apply double jump
        firstJumpTime.remove(player.getUniqueId());

        if (isOnCooldown(player, "Spiderman Double Jump")) {
            sendCooldownMessage(player, "spiderman");
            return;
        }

        // Apply purely vertical velocity (no horizontal dash/impulse)
        Vector vel = new Vector(0, 1.0, 0);
        player.setVelocity(vel);
        player.sendMessage(CC.t("&c&lSPIDERMAN MASK &7- Double jump activated!"));
        setCooldown(player, "Spiderman Double Jump", 8 * 60 * 1000L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        // Clear jump state when player lands
        if (player.isOnGround()) {
            firstJumpTime.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        firstJumpTime.remove(e.getPlayer().getUniqueId());
        MaskCooldownTimer timer = getMaskTimer();
        if (timer != null) timer.removeTimer(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        firstJumpTime.remove(e.getEntity().getUniqueId());
        MaskCooldownTimer timer = getMaskTimer();
        if (timer != null) timer.removeTimer(e.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        firstJumpTime.remove(e.getPlayer().getUniqueId());
        MaskCooldownTimer timer = getMaskTimer();
        if (timer != null) timer.removeTimer(e.getPlayer());
    }

    // Flash: 8% chance to dodge Ninja Star (EnderPearl projectile hit)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearlHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player damaged = (Player) e.getEntity();

        if (e.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
            if (hasMask(damaged, "flash")) {
                Entity damager = e.getDamager();
                if (damager != null) {
                    if (random.nextInt(100) < 8) {
                        e.setCancelled(true);
                        damaged.sendMessage(CC.t("&c&lFLASH MASK &7- You dodged a projectile!"));
                    }
                }
            }
        }
    }

    // Ironman: Switcher range +5 blocks
    public int getSwitcherRangeBonus(Player player) {
        if (hasMask(player, "ironman")) {
            return 5;
        }
        return 0;
    }

    // Ironman: 10% chance to bypass partner item cooldown
    public boolean bypassAbilityCooldown(Player player) {
        if (hasMask(player, "ironman")) {
            return random.nextInt(100) < 10;
        }
        return false;
    }

    // Venom: 15% ender pearl cooldown reduction
    public double getEnderPearlCooldownMultiplier(Player player) {
        if (hasMask(player, "venom")) {
            return 0.85;
        }
        return 1.0;
    }
}
