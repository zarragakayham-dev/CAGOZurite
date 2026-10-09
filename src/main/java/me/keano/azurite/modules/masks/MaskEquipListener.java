package me.keano.azurite.modules.masks;

import me.keano.azurite.modules.framework.Module;
import me.keano.azurite.utils.CC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.entity.EnderPearl;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copyright (c) 2025. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class MaskEquipListener extends Module<MaskManager> {

    private static String CB_PACKAGE;
    private static String NMS_PACKAGE;

    private static Class<?> craftItemStackClass;
    private static Class<?> nmsItemStackClass;
    private static Class<?> packetClass;
    private static Class<?> craftPlayerClass;
    private static Class<?> entityPlayerClass;
    private static Class<?> playerConnectionClass;

    private static Method asNMSCopy;
    private static Method sendPacket;
    private static Method getHandle;

    private static Constructor<?> packetConstructor;

    static {
        try {
            CB_PACKAGE = Bukkit.getServer().getClass().getPackage().getName();
            NMS_PACKAGE = CB_PACKAGE.replace("org.bukkit.craftbukkit", "net.minecraft.server");

            craftItemStackClass = Class.forName(CB_PACKAGE + ".inventory.CraftItemStack");
            nmsItemStackClass = Class.forName(NMS_PACKAGE + ".ItemStack");
            packetClass = Class.forName(NMS_PACKAGE + ".PacketPlayOutEntityEquipment");
            craftPlayerClass = Class.forName(CB_PACKAGE + ".entity.CraftPlayer");
            entityPlayerClass = Class.forName(NMS_PACKAGE + ".EntityPlayer");
            playerConnectionClass = Class.forName(NMS_PACKAGE + ".PlayerConnection");

            asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            sendPacket = playerConnectionClass.getMethod("sendPacket", Class.forName(NMS_PACKAGE + ".Packet"));
            getHandle = craftPlayerClass.getMethod("getHandle");

            packetConstructor = packetClass.getConstructor(int.class, int.class, nmsItemStackClass);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final Map<UUID, String> activeVisualMasks = new HashMap<>();

    public MaskEquipListener(MaskManager manager) {
        super(manager);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            checkAndUpdateVisual(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            checkAndUpdateVisual(player);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        activeVisualMasks.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        Player player = e.getPlayer();

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            checkAndUpdateVisual(player);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemDamage(PlayerItemDamageEvent e) {
        // Bug 2: Cancel durability reduction for fused mask helmets to prevent flicker
        if (getManager().isMaskItem(e.getItem())) {
            e.setCancelled(true);
            return;
        }

        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            checkAndUpdateVisual(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        // Bug 1: Re-send mask visual to the thrower when they launch an EnderPearl,
        // since the client resets its own helmet view after throwing
        if (!(e.getEntity() instanceof EnderPearl)) return;
        if (!(e.getEntity().getShooter() instanceof Player)) return;

        Player player = (Player) e.getEntity().getShooter();
        String activeMask = activeVisualMasks.get(player.getUniqueId());
        if (activeMask == null) return;

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            String stillActive = activeVisualMasks.get(player.getUniqueId());
            if (stillActive != null) {
                sendMaskVisual(player, stillActive);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        activeVisualMasks.remove(player.getUniqueId());

        Bukkit.getScheduler().runTaskLater(getInstance(), () -> {
            checkAndUpdateVisual(player);
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        activeVisualMasks.remove(e.getEntity().getUniqueId());
    }

    public void checkAndUpdateVisual(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();

        if (helmet == null || helmet.getType() == Material.AIR) {
            String activeMask = activeVisualMasks.get(player.getUniqueId());
            if (activeMask != null) {
                activeVisualMasks.remove(player.getUniqueId());
                getManager().clearEquippedMask(player.getUniqueId());
            }
            return;
        }

        String maskId = getManager().getMaskIdFromItem(helmet);
        if (maskId == null) {
            String activeMask = activeVisualMasks.get(player.getUniqueId());
            if (activeMask != null) {
                sendRealHelmetVisual(player);
                activeVisualMasks.remove(player.getUniqueId());
                getManager().clearEquippedMask(player.getUniqueId());
            }
            return;
        }

        // Only re-send if the mask changed or no active visual
        String activeMask = activeVisualMasks.get(player.getUniqueId());
        if (activeMask != null && activeMask.equalsIgnoreCase(maskId)) {
            // Mask is still the same and active - don't re-send to avoid flicker
            return;
        }

        sendMaskVisual(player, maskId);
    }

    public void sendMaskVisual(Player player, String maskId) {
        MaskManager.Mask mask = getManager().getMask(maskId);
        if (mask == null) return;

        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType() == Material.AIR) return;

        ItemStack fakeHelmet = buildVisualItem(mask, helmet);

        sendEquipmentPacket(player, 4, fakeHelmet);
        activeVisualMasks.put(player.getUniqueId(), maskId);
        getManager().setEquippedMask(player.getUniqueId(), maskId);
    }

    private ItemStack buildVisualItem(MaskManager.Mask mask, ItemStack helmet) {
        ItemStack visual = SkullUtils.createSkull(mask.getTexture());

        ItemMeta visualMeta = visual.getItemMeta();
        ItemMeta helmetMeta = helmet.getItemMeta();

        if (visualMeta != null && helmetMeta != null) {
            // Bug 1 fix: always preserve the helmet's original display name
            if (helmetMeta.hasDisplayName()) {
                visualMeta.setDisplayName(helmetMeta.getDisplayName());
            }

            // Bug 4 fix: strip " Mask" suffix and use &8 (dark gray) color
            String maskName = mask.getDisplayName().replaceAll("(?i)\\s+Mask$", "");
            String maskLoreLine = CC.t("&8" + maskName);
            List<String> lore = new ArrayList<>();
            if (helmetMeta.hasLore()) {
                lore.addAll(helmetMeta.getLore());
            }
            if (!lore.contains(maskLoreLine)) {
                lore.add(maskLoreLine);
            }
            visualMeta.setLore(lore);

            visual.setItemMeta(visualMeta);

            if (helmet.getEnchantments() != null && !helmet.getEnchantments().isEmpty()) {
                visual.addUnsafeEnchantments(helmet.getEnchantments());
            }
        }

        return visual;
    }

    public void sendRealHelmetVisual(Player player) {
        ItemStack realHelmet = player.getInventory().getHelmet();
        if (realHelmet == null || realHelmet.getType() == Material.AIR) {
            sendEquipmentPacket(player, 4, null);
        } else {
            sendEquipmentPacket(player, 4, realHelmet);
        }
    }

    private void sendEquipmentPacket(Player player, int slot, ItemStack item) {
        try {
            Object entityPlayer = getHandle.invoke(player);
            int entityId = player.getEntityId();

            Object nmsItem = null;
            if (item != null) {
                nmsItem = asNMSCopy.invoke(null, item);
            }

            Object packet = packetConstructor.newInstance(entityId, slot, nmsItem);

            Object connection = entityPlayerClass.getField("playerConnection").get(entityPlayer);
            sendPacket.invoke(connection, packet);

            for (Player nearby : Bukkit.getOnlinePlayers()) {
                if (nearby.equals(player)) continue;
                if (!nearby.getWorld().equals(player.getWorld())) continue;
                if (!nearby.canSee(player)) continue;

                Object nearbyHandle = getHandle.invoke(nearby);
                Object nearbyConn = entityPlayerClass.getField("playerConnection").get(nearbyHandle);
                sendPacket.invoke(nearbyConn, packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
