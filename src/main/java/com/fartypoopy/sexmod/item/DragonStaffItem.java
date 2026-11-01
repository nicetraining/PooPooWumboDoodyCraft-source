package com.fartypoopy.sexmod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The tribe control tool. Right-click (on a Kobold or a block) is handled by the Kobold and by {@code TribeEvents};
 * in the air it opens the four-quadrant menu for players that have a tribe. The item has no default use.
 */
public final class DragonStaffItem extends LegacyPurposeItem implements software.bernie.geckolib.animatable.GeoItem {
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    public DragonStaffItem(Properties properties) {
        super(properties, "dragon_staff", 0);
    }

    /** Original hy.registerControllers: none, the staff geo is static. */
    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            com.fartypoopy.sexmod.client.ClientHooks.openStaffMenu();
        }
        return InteractionResultHolder.fail(player.getItemInHand(hand));
    }
}
