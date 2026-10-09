package me.keano.azurite.modules.masks;

import me.keano.azurite.modules.framework.Module;
import me.keano.azurite.utils.CC;
import me.keano.azurite.utils.Utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (c) 2025. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class MaskFusionListener extends Module<MaskManager> {

    public MaskFusionListener(MaskManager manager) {
        super(manager);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        ItemStack cursor = e.getCursor();
        ItemStack clicked = e.getCurrentItem();

        if (cursor == null || cursor.getType() == Material.AIR) return;
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (!getManager().isMaskItem(cursor)) return;
        if (!MaskManager.isHelmet(clicked.getType())) return;
        if (getManager().isMaskItem(clicked)) return;

        String maskId = getManager().getMaskIdFromItem(cursor);
        if (maskId == null) return;

        e.setCancelled(true);

        ItemStack fusedHelmet = fuseHelmetWithMask(clicked, maskId);

        e.setCurrentItem(fusedHelmet);

        if (cursor.getAmount() <= 1) {
            e.setCursor(new ItemStack(Material.AIR));
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            e.setCursor(cursor);
        }

        player.updateInventory();
        player.sendMessage(CC.t("&aYou fused your mask with your helmet!"));
    }

    private ItemStack fuseHelmetWithMask(ItemStack helmet, String maskId) {
        ItemStack fused = helmet.clone();

        ItemMeta meta = fused.getItemMeta();
        if (meta == null) return fused;

        List<String> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(meta.getLore());
        }

        MaskManager.Mask mask = getManager().getMask(maskId);
        if (mask != null) {
            // Bug 4 fix: strip " Mask" suffix and use &8 (dark gray) color
            String maskName = mask.getDisplayName().replaceAll("(?i)\\s+Mask$", "");
            String maskLoreLine = CC.t("&8" + maskName);
            if (!lore.contains(maskLoreLine)) {
                lore.add(maskLoreLine);
            }
        }

        // Bug 2 fix: make the fused helmet unbreakable to prevent damage flicker
        // Only set the NBT flag on modern versions; on legacy the PlayerItemDamageEvent
        // cancellation in MaskEquipListener handles it.
        if (Utils.isModernVer()) {
            meta.setUnbreakable(true);
            try {
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.valueOf("HIDE_UNBREAKABLE"));
            } catch (IllegalArgumentException ignored) {
            }
        }

        meta.setLore(lore);
        fused.setItemMeta(meta);

        fused = NBTUtils.setMaskId(fused, maskId);
        return fused;
    }
}
