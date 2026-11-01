package com.fartypoopy.sexmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.custommodel.CustomModels;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.item.NpcEditorWandItem;
import com.fartypoopy.sexmod.network.CustomModelNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client side of the NPC editor wand (original hj, a$b, fx, fd): wardrobe, model-code copy, {@code /setmodelcode},
 * {@code /whitelistserver} and the "open character customisation menu" key. Player-as-girl forms only ever existed for
 * two hard-coded accounts in the original, so the paths that need one always end in the original's hint message.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID, value = Dist.CLIENT)
public final class WandClient {
    /** Original ClientProxy.keyBindings[1] (LWJGL key 76, numpad 5). */
    public static final KeyMapping CUSTOMISE = new KeyMapping("open character customisation menu", InputConstants.KEY_NUMPAD5, "Sex mod");

    private WandClient() {
    }

    /** Original br.d handling: a failed load is retried before the wardrobe opens. */
    private static boolean modelsReady() {
        if (CustomModels.loadFailed) {
            CustomModels.loadFailed = CustomModels.reload(true) != 0;
        }
        return !CustomModels.loadFailed;
    }

    public static void openWardrobe(CompanionEntity girl) {
        if (modelsReady()) {
            WardrobeScreen.open(editTarget(girl));
        }
    }

    /** Original em.E() / f_.E(): a Galath with a daughter edits Manglelie while sneaking, and says so otherwise. */
    private static CompanionEntity editTarget(CompanionEntity girl) {
        Minecraft minecraft = Minecraft.getInstance();
        if (girl.kind() != com.fartypoopy.sexmod.entity.CompanionKind.GALATH || girl.familyId() == null
                || minecraft.level == null || minecraft.player == null) {
            return girl;
        }
        CompanionEntity daughter = null;
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof CompanionEntity mang && mang.kind() == com.fartypoopy.sexmod.entity.CompanionKind.MANGLELIE
                    && entity.getUUID().equals(girl.familyId())) {
                daughter = mang;
                break;
            }
        }
        if (daughter == null) {
            return girl;
        }
        if (minecraft.player.isShiftKeyDown()) {
            return daughter;
        }
        minecraft.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                net.minecraft.ChatFormatting.GRAY + "[sneak] + [right click] if you want to edit Manglelie instead"), true);
        return girl;
    }

    /** Original hj.a(AttackEntityEvent): "<name>'s model-code: <code>$<values>", also put on the clipboard. */
    public static void copyModelCode(CompanionEntity girl) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String code = girl.modelCode();
        String values = CustomModels.valuesToString(girl.girlSpecificValues());
        minecraft.player.displayClientMessage(Component.literal(String.format("%s's model-code: %s%s$%s",
                girl.getName().getString(), ChatFormatting.YELLOW, code, values)), false);
        minecraft.player.displayClientMessage(Component.literal("copied to clipboard").withStyle(ChatFormatting.ITALIC), false);
        minecraft.keyboardHandler.setClipboard(String.format("%s$%s", code, values));
    }

    /** Original hj.a(EntityPlayer, World): left-click with the wand copies your own form's model code. */
    public static void noPlayerGirl() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        CompanionEntity form = PlayerFormClient.localForm();
        if (form == null) {
            minecraft.player.displayClientMessage(Component.literal("you gotta turn into the girl, you want to copy the model-code off"), true);
            return;
        }
        String code = form.modelCode();
        String values = CustomModels.valuesToString(form.girlSpecificValues());
        String kind = form.kind().name().toLowerCase(java.util.Locale.ROOT);
        minecraft.player.displayClientMessage(Component.literal(String.format("%s's model-code: %s%s$%s",
                Character.toUpperCase(kind.charAt(0)) + kind.substring(1), ChatFormatting.YELLOW, code, values)), false);
        minecraft.player.displayClientMessage(Component.literal("copied to clipboard").withStyle(ChatFormatting.ITALIC), false);
        minecraft.keyboardHandler.setClipboard(String.format("%s$%s", code, values));
    }

    @SubscribeEvent
    public static void onLeftClickEmpty(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickEmpty event) {
        if (NpcEditorWandItem.holding(event.getEntity())) {
            noPlayerGirl();
        }
    }

    /** Original hj.a(Entity, ItemStack): the wand shows its active texture while aimed at a girl. */
    public static float activeProperty(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
                                       net.minecraft.world.entity.LivingEntity holder, int seed) {
        Minecraft minecraft = Minecraft.getInstance();
        return holder != null && holder == minecraft.player
                && minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof CompanionEntity ? 1.0F : 0.0F;
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(CUSTOMISE);
    }

    /** Original a$b.a(KeyInputEvent). */
    @SubscribeEvent
    public static void onKey(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (CUSTOMISE.consumeClick()) {
            if (minecraft.player == null || !modelsReady()) {
                continue;
            }
            CompanionEntity form = PlayerFormClient.localForm();
            if (form == null) {
                minecraft.player.displayClientMessage(Component.literal("You have to turn into the girl you want to customize"), true);
            } else {
                WardrobeScreen.open(form); // original a$b: your own form's wardrobe
            }
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        // original fd: let this server send its custom models
        event.getDispatcher().register(Commands.literal("whitelistserver")
                .executes(context -> whitelist(false))
                .then(Commands.argument("confirm", StringArgumentType.word())
                        .executes(context -> whitelist("confirm".equals(StringArgumentType.getString(context, "confirm"))))));
        // original fx: apply a copied model code to the girl under the crosshair
        event.getDispatcher().register(Commands.literal("setmodelcode")
                .executes(context -> setModelCode(""))
                .then(Commands.argument("code", StringArgumentType.greedyString())
                        .executes(context -> setModelCode(StringArgumentType.getString(context, "code")))));
    }

    private static int whitelist(boolean confirm) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        String host = CustomModelClient.serverHost();
        if (host == null) {
            minecraft.player.displayClientMessage(Component.literal("This is a multiplayer feature only").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        if (CustomModels.whitelisted(host)) {
            minecraft.player.displayClientMessage(Component.literal("Server is already whitelisted :)").withStyle(ChatFormatting.GREEN), false);
            return 0;
        }
        if (!confirm) {
            minecraft.player.displayClientMessage(Component.literal("By whitelisting this server, you allow the server to send you the custom models that are used on it").withStyle(ChatFormatting.YELLOW), false);
            minecraft.player.displayClientMessage(Component.literal("ONLY WHITELIST SERVERS, WHOSE SERVER OWNER YOU KNOW AND TRUST").withStyle(ChatFormatting.RED), false);
            minecraft.player.displayClientMessage(Component.literal("to confirm your decision type:").withStyle(ChatFormatting.YELLOW), false);
            minecraft.player.displayClientMessage(Component.literal("/whitelistserver confirm").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        CustomModels.whitelist(host);
        minecraft.player.displayClientMessage(Component.literal("confirmed :)").withStyle(ChatFormatting.GREEN), false);
        CustomModelClient.requestServerModels();
        return 1;
    }

    private static int setModelCode(String argument) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0;
        }
        String[] parts = argument.split("\\$");
        String code = parts.length > 0 ? parts[0] : "";
        String values = parts.length > 1 ? parts[1] : "";
        CompanionEntity girl = minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof CompanionEntity target
                ? target : PlayerFormClient.localForm(); // original fx.a(RayTraceResult): else your own form
        if (girl == null) {
            minecraft.player.displayClientMessage(Component.literal("You gotta transform into the girl you want to apply the model-code to"), true);
            return 0;
        }
        List<Integer> parsed = values.isEmpty() ? List.of() : CustomModels.parseValues(values);
        PacketDistributor.sendToServer(new CustomModelNetwork.SetModelCode(code, girl.getUUID(), parsed));
        String kind = girl.kind().name().toLowerCase(java.util.Locale.ROOT);
        minecraft.player.displayClientMessage(Component.literal(girl.isPlayerForm()
                ? "applied model code to your player-" + Character.toUpperCase(kind.charAt(0)) + kind.substring(1)
                : "applied model code to this " + girl.originalName()).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }
}
