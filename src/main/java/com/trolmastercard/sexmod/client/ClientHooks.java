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

    /** The Dragon Staff's menu exists only for players that have a tribe (original: list of members not empty). */
    public static void openStaffMenu() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && TribeClient.hasTribe()) {
            minecraft.setScreen(new StaffRadialScreen());
            TribeClient.request();
        }
    }

    public static void openNameTribe(java.util.UUID tribeId) {
        Minecraft.getInstance().setScreen(new NameTribeScreen(tribeId));
    }

    public static void openBeeScreen(CompanionEntity bee) {
        Minecraft.getInstance().setScreen(new BeeScreen(bee));
    }

    public static void openActionPanel(CompanionEntity companion) {
        Minecraft.getInstance().setScreen(new CompanionActionScreen(companion));
    }

    /** Animation choice: the carrier sees the first-person catch/pick-up/throw animations, everybody else the third-person ones. */
    public static boolean isCarrierFirstPerson(CompanionEntity goblin) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && minecraft.options.getCameraType().isFirstPerson()
                && goblin.goblinCarrier().filter(minecraft.player.getUUID()::equals).isPresent();
    }

    private static final java.util.Map<Integer, Double> KOBOLD_LAST_DISTANCE = new java.util.HashMap<>();
    private static long koboldGreetingAt = -300L;

    /** Original ff.w: an idle Kobold greets her owner when he walks up from over 2 blocks to under 2 (300 tick cooldown for all Kobolds). */
    public static void mangLook(CompanionEntity daughter) {
        ManglelieRide.tick(daughter);
    }

    public static void koboldGreeting(CompanionEntity kobold) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || kobold.koboldState() != com.trolmastercard.sexmod.tribe.KoboldBehavior.ST_NONE
                || kobold.isInScene() || kobold.tribeFollow() || !kobold.masterId().filter(minecraft.player.getUUID()::equals).isPresent()) {
            return;
        }
        double distance = kobold.distanceTo(minecraft.player);
        double previous = KOBOLD_LAST_DISTANCE.getOrDefault(kobold.getId(), distance);
        KOBOLD_LAST_DISTANCE.put(kobold.getId(), distance);
        long now = kobold.level().getGameTime();
        if (distance < 10.0D && previous > 2.0D && distance < 2.0D && now - koboldGreetingAt >= 300L) {
            koboldGreetingAt = now;
            minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("<")
                    .append(kobold.getDisplayName()).append("> Hey master!"));
            kobold.playCue("girls.kobold.heymaster.heymaster" + kobold.getRandom().nextInt(6), 1.0F);
        }
    }

    /** Original ff: opening a Kobold's menu as its owner plays one of the "master" voice lines. */
    public static void koboldMasterSound(CompanionEntity kobold) {
        kobold.playCue("girls.kobold.master.master" + kobold.getRandom().nextInt(6), 1.0F);
    }

    /** Original em ambient roll: the nearby local player (under 10 blocks) reads the girl's "whopa". */
    public static void whopa(CompanionEntity girl) {
        net.minecraft.client.player.LocalPlayer local = net.minecraft.client.Minecraft.getInstance().player;
        if (local != null && local.position().distanceTo(girl.position()) < 10.0D) {
            local.sendSystemMessage(net.minecraft.network.chat.Component.literal("<").append(girl.getDisplayName()).append("> whopa"));
        }
    }
}
