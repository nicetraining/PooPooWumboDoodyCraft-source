package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.registry.ModEntities;
import com.fartypoopy.sexmod.registry.ModMenus;
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
        event.registerEntityRenderer(ModEntities.FRIENDLY_SLIME.get(), FriendlySlimeRenderer::new);
        event.registerEntityRenderer(ModEntities.CUSTOM_MODEL.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.PYROCINICAL.get(), PyrocinicalRenderer::new);
        event.registerEntityRenderer(ModEntities.LUNA_HOOK.get(), LunaHookRenderer::new);
        event.registerEntityRenderer(ModEntities.ENERGY_BALL.get(), EnergyBallRenderer::new);
    }

    @SubscribeEvent
    public static void registerClientItems(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private DragonStaffRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new DragonStaffRenderer();
                }
                return this.renderer;
            }
        }, com.fartypoopy.sexmod.registry.ModItems.DRAGON_STAFF.get());
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private AlliesLampRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new AlliesLampRenderer();
                }
                return this.renderer;
            }
        }, com.fartypoopy.sexmod.registry.ModItems.ALLIES_LAMP.get());
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private GalathCoinRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GalathCoinRenderer();
                }
                return this.renderer;
            }
        }, com.fartypoopy.sexmod.registry.ModItems.GALATH_COIN.get());
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private KoboldEggItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) {
                    this.renderer = new KoboldEggItemRenderer();
                }
                return this.renderer;
            }
        }, com.fartypoopy.sexmod.registry.ModItems.KOBOLD_EGG_ITEM.get());
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FriendlySlimeRenderer.LAYER, FriendlySlimeRenderer::createLayer);
    }

    @SubscribeEvent
    public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        CustomModelClient.install();
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                com.fartypoopy.sexmod.registry.ModItems.NPC_EDITOR_WAND.get(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "active"), WandClient::activeProperty));
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
                com.fartypoopy.sexmod.registry.ModItems.LUNA_ROD.get(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "cast"),
                (stack, level, entity, seed) -> entity instanceof com.fartypoopy.sexmod.entity.CompanionEntity companion
                        && companion.fishCast() ? 1.0F : 0.0F));
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COMPANION_INVENTORY.get(), CompanionInventoryScreen::new);
        event.register(ModMenus.COMPANION_STORAGE.get(), net.minecraft.client.gui.screens.inventory.ContainerScreen::new);
    }

    /** 1.12 EntityRenderer: the in-game overlay (and every RenderGameOverlayEvent HUD) is skipped while F1 hides the GUI. */
    private static boolean overlayShown() {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        return !minecraft.options.hideGui || minecraft.screen != null;
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "transition"),
                (graphics, delta) -> { if (overlayShown()) TransitionOverlay.render(graphics, delta); });
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "scene_hud"),
                (graphics, delta) -> { if (overlayShown()) SceneHud.render(graphics, delta); });
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "flight_hud"),
                (graphics, delta) -> { if (overlayShown()) FlightHud.render(graphics, delta); });
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, "escape_hud"),
                (graphics, delta) -> { if (overlayShown()) EscapeHud.render(graphics, delta); });
    }
}
