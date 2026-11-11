package com.fartypoopy.sexmod.mixin;

import com.fartypoopy.sexmod.worldgen.HouseClearance;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World-generation trees are not placed on or right beside a girl's house (see {@link HouseClearance}). */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void sexmod$keepOffHouses(FeaturePlaceContext<TreeConfiguration> context, CallbackInfoReturnable<Boolean> result) {
        if (HouseClearance.blocksTree(context.level(), context.origin())) {
            result.setReturnValue(false);
        }
    }
}
