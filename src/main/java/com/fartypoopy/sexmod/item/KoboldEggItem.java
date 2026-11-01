package com.fartypoopy.sexmod.item;

import com.fartypoopy.sexmod.entity.KoboldEggEntity;
import com.fartypoopy.sexmod.registry.ModEntities;
import com.fartypoopy.sexmod.tribe.KoboldColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/**
 * Original c7: only obtained from the owner's mating scene. Placed on a block it spawns an egg at the exact click
 * position; it hatches into a new member of the tribe written on the item.
 */
public final class KoboldEggItem extends LegacyPurposeItem implements software.bernie.geckolib.animatable.GeoItem {
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    public KoboldEggItem(Properties properties) {
        super(properties, "kobold_egg_item", 0);
    }

    /** Original c7.registerControllers: none, the item model is static. */
    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    /**
     * Original c7's RightClickBlock handler: it runs before the block is used and never cancels it, so the egg is placed
     * at the exact hit position even when the block opens (a chest, a door), and it is consumed even in creative.
     */
    public static void rightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel) || !(event.getItemStack().getItem() instanceof KoboldEggItem)) {
            return;
        }
        KoboldEggEntity egg = ModEntities.KOBOLD_EGG.get().create(serverLevel);
        if (egg == null) {
            return;
        }
        ItemStack stack = event.getItemStack();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        egg.setEggColor(KoboldColor.byName(tag.getString("tribeColor")).name());
        if (tag.contains("tribeID", net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                egg.setTribeId(UUID.fromString(tag.getString("tribeID")));
            } catch (IllegalArgumentException ignored) {
                // hatches into a fresh tribe
            }
        }
        var at = event.getHitVec().getLocation();
        egg.setPos(at.x, at.y, at.z);
        serverLevel.addFreshEntity(egg);
        stack.shrink(1);
    }
}
