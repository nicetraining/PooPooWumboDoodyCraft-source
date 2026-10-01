package com.trolmastercard.sexmod.registry;

import com.trolmastercard.sexmod.FapCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModPotions {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, FapCraft.MOD_ID);
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, FapCraft.MOD_ID);
    private static final List<Item> LEGACY_RED_FLOWERS = List.of(
            Items.POPPY,
            Items.BLUE_ORCHID,
            Items.ALLIUM,
            Items.AZURE_BLUET,
            Items.RED_TULIP,
            Items.ORANGE_TULIP,
            Items.WHITE_TULIP,
            Items.PINK_TULIP,
            Items.OXEYE_DAISY
    );

    public static final DeferredHolder<MobEffect, MobEffect> HORNY_EFFECT = EFFECTS.register(
            "horny_potion",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0xFF62C8)
    );
    public static final DeferredHolder<Potion, Potion> HORNY_POTION = POTIONS.register(
            "horny_potion",
            () -> new Potion(
                    new MobEffectInstance(HORNY_EFFECT, 20 * 180),
                    new MobEffectInstance(MobEffects.CONFUSION, 200, 1)
            )
    );

    private ModPotions() {
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
        POTIONS.register(modEventBus);
    }

    public static void registerBrewingRecipes(RegisterBrewingRecipesEvent event) {
        LEGACY_RED_FLOWERS.forEach(flower ->
                event.getBuilder().addMix(Potions.MUNDANE, flower, HORNY_POTION));
    }

    private static final class MarkerEffect extends MobEffect {
        private MarkerEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
