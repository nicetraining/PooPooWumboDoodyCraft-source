package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionKind;
import com.trolmastercard.sexmod.registry.ModEntities;
import com.trolmastercard.sexmod.registry.ModMenus;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        for (CompanionKind kind : CompanionKind.values()) {
            event.registerEntityRenderer(ModEntities.get(kind), CompanionRenderer::new);
        }
        for (var type : ModEntities.compatibilityTypes()) {
            event.registerEntityRenderer(type, CompanionRenderer::new);
        }
        event.registerEntityRenderer(ModEntities.KOBOLD_EGG.get(), KoboldEggRenderer::new);
        event.registerEntityRenderer(ModEntities.LUNA_HOOK.get(), FishingHookRenderer::new);
        event.registerEntityRenderer(ModEntities.ENERGY_BALL.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COMPANION_INVENTORY.get(), CompanionInventoryScreen::new);
        event.register(ModMenus.COMPANION_STORAGE.get(), net.minecraft.client.gui.screens.inventory.ContainerScreen::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "transition"),
                TransitionOverlay::render);
    }
}
