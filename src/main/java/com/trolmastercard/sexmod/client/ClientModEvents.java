package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionKind;
import com.trolmastercard.sexmod.registry.ModEntities;
import com.trolmastercard.sexmod.registry.ModMenus;
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
        event.registerEntityRenderer(ModEntities.FRIENDLY_SLIME.get(), net.minecraft.client.renderer.entity.SlimeRenderer::new);
        event.registerEntityRenderer(ModEntities.CUSTOM_MODEL.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.PYROCINICAL.get(), PyrocinicalRenderer::new);
        event.registerEntityRenderer(ModEntities.LUNA_HOOK.get(), LunaHookRenderer::new);
        event.registerEntityRenderer(ModEntities.ENERGY_BALL.get(), EnergyBallRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                com.trolmastercard.sexmod.registry.ModItems.LUNA_ROD.get(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "cast"),
                (stack, level, entity, seed) -> entity instanceof com.trolmastercard.sexmod.entity.CompanionEntity companion
                        && companion.fishCast() ? 1.0F : 0.0F));
    }

    /** Original hn: the spots of the Kobold egg item take the tribe colour stored on the stack. */
    @SubscribeEvent
    public static void registerItemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            String color = data == null ? "" : data.copyTag().getString("tribeColor");
            return 0xFF000000 | com.trolmastercard.sexmod.tribe.KoboldColor.byName(color).mainRgb();
        }, com.trolmastercard.sexmod.registry.ModItems.KOBOLD_EGG_ITEM.get());
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
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "scene_hud"),
                SceneHud::render);
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "flight_hud"),
                FlightHud::render);
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "escape_hud"),
                EscapeHud::render);
    }
}
