package com.trolmastercard.sexmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.GoblinBehavior;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Original key 34 (G): opens the wheel of the goblin on your shoulder. */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class GoblinWheelKey {
    public static final KeyMapping KEY = new KeyMapping("key.sexmod.goblin_wheel", InputConstants.KEY_G, "key.categories.sexmod");

    private GoblinWheelKey() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        while (KEY.consumeClick()) {
            if (minecraft.screen != null) {
                continue;
            }
            for (CompanionEntity goblin : minecraft.level.getEntitiesOfClass(CompanionEntity.class,
                    minecraft.player.getBoundingBox().inflate(3.0D), e -> e.goblin() != null)) {
                if (goblin.goblinState() == GoblinBehavior.SHOULDER
                        && goblin.goblinCarrier().filter(minecraft.player.getUUID()::equals).isPresent()) {
                    minecraft.setScreen(new GoblinWheelScreen(goblin));
                    break;
                }
            }
        }
    }
}
