package com.trolmastercard.sexmod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The tribe control tool. Right-click (on a Kobold or a block) is handled by the Kobold and by {@code TribeEvents};
 * in the air it opens the four-quadrant menu for players that have a tribe. The item has no default use.
 */
public final class DragonStaffItem extends LegacyPurposeItem {
    public DragonStaffItem(Properties properties) {
        super(properties, "dragon_staff", 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            com.trolmastercard.sexmod.client.ClientHooks.openStaffMenu();
        }
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }
}
