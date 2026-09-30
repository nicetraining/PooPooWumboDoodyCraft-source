package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.entity.KoboldEggEntity;
import com.trolmastercard.sexmod.registry.ModEntities;
import com.trolmastercard.sexmod.tribe.KoboldColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import java.util.UUID;

/**
 * Original c7: only obtained from the owner's mating scene. Placed on a block it spawns an egg at the exact click
 * position; it hatches into a new member of the tribe written on the item.
 */
public final class KoboldEggItem extends LegacyPurposeItem {
    public KoboldEggItem(Properties properties) {
        super(properties, "kobold_egg_item", 0);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        KoboldEggEntity egg = ModEntities.KOBOLD_EGG.get().create(serverLevel);
        if (egg == null) {
            return InteractionResult.FAIL;
        }
        CustomData data = context.getItemInHand().get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        egg.setEggColor(KoboldColor.byName(tag.getString("tribeColor")).name());
        if (tag.contains("tribeID", net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                egg.setTribeId(UUID.fromString(tag.getString("tribeID")));
            } catch (IllegalArgumentException ignored) {
                // hatches into a fresh tribe
            }
        }
        var at = context.getClickLocation();
        egg.setPos(at.x, at.y, at.z);
        if (!serverLevel.addFreshEntity(egg)) {
            return InteractionResult.FAIL;
        }
        context.getItemInHand().shrink(1); // original: consumed even in creative
        return InteractionResult.CONSUME;
    }
}
