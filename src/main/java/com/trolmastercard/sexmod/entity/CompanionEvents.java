package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.FapCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Damage rules that apply to companions from outside their own class. */
@EventBusSubscriber(modid = FapCraft.MOD_ID)
public final class CompanionEvents {
    private CompanionEvents() {
    }

    /** Lethal damage while following leaves the girl downed at 1 HP; a downed girl takes no damage. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof CompanionEntity companion) {
            event.setNewDamage(companion.adjustIncomingDamage(event.getNewDamage()));
        }
    }
}
