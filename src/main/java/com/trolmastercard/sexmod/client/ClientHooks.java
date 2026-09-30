package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import net.minecraft.client.Minecraft;

/** Entry points called from common code on the logical client; kept in one class so servers never load screens. */
public final class ClientHooks {
    private ClientHooks() {
    }

    /** Bia's second menu: only the given scenes, no follow/home column. */
    public static void openSceneMenu(int entityId, java.util.List<String> sceneNames) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !(minecraft.level.getEntity(entityId) instanceof CompanionEntity companion)) {
            return;
        }
        java.util.List<com.trolmastercard.sexmod.entity.CompanionScene> scenes = new java.util.ArrayList<>();
        for (String name : sceneNames) {
            com.trolmastercard.sexmod.entity.CompanionScene scene =
                    com.trolmastercard.sexmod.entity.CompanionScene.byName(name);
            if (scene != null) {
                scenes.add(scene);
            }
        }
        minecraft.setScreen(new CompanionActionScreen(companion, scenes, false));
    }

    public static void openBeeScreen(CompanionEntity bee) {
        Minecraft.getInstance().setScreen(new BeeScreen(bee));
    }

    public static void openActionPanel(CompanionEntity companion) {
        Minecraft.getInstance().setScreen(new CompanionActionScreen(companion));
    }
}
