package com.fartypoopy.sexmod.entity;

import com.fartypoopy.sexmod.FapCraft;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Original eb$a: every Creeper flees from Luna within 6 blocks (walk 1.0, sprint 1.2). */
@EventBusSubscriber(modid = FapCraft.MOD_ID)
public final class LunaEvents {
    private LunaEvents() {
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Creeper creeper)) {
            return;
        }
        creeper.goalSelector.addGoal(3, new AvoidEntityGoal<>(creeper, CompanionEntity.class,
                entity -> entity instanceof CompanionEntity companion && companion.kind() == CompanionKind.LUNA,
                6.0F, 1.0D, 1.2D, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test));
    }
}
