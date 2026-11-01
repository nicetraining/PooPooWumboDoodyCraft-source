package com.fartypoopy.sexmod.entity;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.network.PlayerFormNetwork;
import com.fartypoopy.sexmod.registry.ModEntities;
import com.fartypoopy.sexmod.registry.ModPotions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Original ei / b_ / co / q / fu (server side): a player who drinks the horny potion picks a girl and becomes her "form",
 * a girl entity that is drawn instead of the player and follows them. Each player keeps a model code and per-girl values
 * per kind in their own data ({@code sexmod:CustomModel<KIND>}, {@code sexmod:GirlSpecific<KIND>}).
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID)
public final class PlayerForms {
    public static final String MODEL_KEY = "sexmod:CustomModel";
    public static final String SPECIFIC_KEY = "sexmod:GirlSpecific";
    /** Original co: the picker opens once the potion has run for 100 ticks (3600 -> 3500). */
    private static final int PICKER_AT = 3500;
    /** Original ei.D(): strip / dress-up flips the outfit at tick 65 and ends at tick 100. */
    private static final int STRIP_FLIP = 65;
    private static final int STRIP_END = 100;

    /** Original ei.al: owner -> form, server side. */
    private static final Map<UUID, CompanionEntity> FORMS = new ConcurrentHashMap<>();

    private PlayerForms() {
    }

    /** Original fy.playerClass != null: every kind except Manglelie has a player form. */
    public static boolean hasForm(CompanionKind kind) {
        return kind != CompanionKind.MANGLELIE;
    }

    /** Original ei.d(UUID) / ei.g(player). */
    public static CompanionEntity formOf(MinecraftServer server, UUID owner) {
        CompanionEntity form = FORMS.get(owner);
        if (form != null && !form.isRemoved()) {
            return form;
        }
        FORMS.remove(owner);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof CompanionEntity girl && girl.formOwner().filter(owner::equals).isPresent()) {
                    FORMS.put(owner, girl);
                    return girl;
                }
            }
        }
        return null;
    }

    public static CompanionEntity formOf(ServerPlayer player) {
        return formOf(player.server, player.getUUID());
    }

    /** Original b_$a: drop the current form, then (unless the player picked themself) create the new one. */
    public static void become(ServerPlayer player, CompanionKind kind) {
        CompanionEntity old = formOf(player);
        if (old != null && (old.kind() == CompanionKind.BEE || old.kind() == CompanionKind.ALLIE)) {
            allowFlight(player, false); // original e9.y() / e5.y(); the Galath form has no y() and leaves flight on
        }
        removeForms(player.server, player.getUUID());
        if (kind == null || !hasForm(kind)) {
            return;
        }
        if (kind == CompanionKind.BEE || kind == CompanionKind.ALLIE || kind == CompanionKind.GALATH) {
            allowFlight(player, true); // original e9.B() / e5.B() / er.B()
        }
        ServerLevel level = player.serverLevel();
        CompanionEntity form = ModEntities.get(kind).create(level);
        if (form == null) {
            return;
        }
        form.setFormOwner(player.getUUID());
        form.setOutfit(model(kind, 1)); // original em: currentModel starts at 1
        form.setDeltaMovement(Vec3.ZERO);
        form.moveTo(player.getX(), player.getY() + 69.0D, player.getZ(), player.getYRot(), 0.0F); // original: 69 above, the first tick moves her
        applyStoredLook(form, player);
        level.addFreshEntity(form);
        FORMS.put(player.getUUID(), form);
    }

    /**
     * The original's currentModel index as an outfit: 0 is the bare model (jennynude, ellie/nude, bianude, slime/nude,
     * bee, goblin, kobold, allie), 1 the dressed or armored one.
     */
    static OutfitMode model(CompanionKind kind, int index) {
        if (index == 0) {
            return kind.supportsOutfit(OutfitMode.ALTERNATE) ? OutfitMode.ALTERNATE : OutfitMode.DEFAULT;
        }
        return kind == CompanionKind.SLIME || kind == CompanionKind.BEE || kind == CompanionKind.GOBLIN
                || kind == CompanionKind.KOBOLD || kind == CompanionKind.ALLIE ? OutfitMode.ARMORED : OutfitMode.DEFAULT;
    }

    static int modelIndex(CompanionEntity form) {
        if (form.kind() == CompanionKind.GALATH) {
            return form.formBare() ? 0 : 1;
        }
        return form.outfit() == model(form.kind(), 0) ? 0 : 1;
    }

    /** Original em.f(int) on a form. */
    static void setModel(CompanionEntity form, int index) {
        if (form.kind() == CompanionKind.GALATH) {
            form.setFormBare(index == 0);
        }
        form.setOutfit(model(form.kind(), index));
    }

    /** Original ei.c(boolean): the winged forms let their player fly; taking it away also ends a flight in progress. */
    static void allowFlight(ServerPlayer player, boolean allowed) {
        if (!com.fartypoopy.sexmod.Config.ALLOW_FLYING.get()) {
            return; // original ei.c(boolean): if (!ag) return
        }
        if (player.isCreative() || player.isSpectator()) {
            return; // their own flight is not ours to take (the 1.12 code would have cleared it until the next mode change)
        }
        player.getAbilities().mayfly = allowed;
        if (!allowed) {
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
    }

    /** Original q.a(world, player, ei) and b_: every form of this player goes away. */
    public static void removeForms(MinecraftServer server, UUID owner) {
        FORMS.remove(owner);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof CompanionEntity girl && girl.formOwner().filter(owner::equals).isPresent()) {
                    girl.discard();
                }
            }
        }
    }

    private static void applyStoredLook(CompanionEntity form, Player player) {
        CompoundTag data = player.getPersistentData();
        String specifics = data.getString(SPECIFIC_KEY + form.kind().name());
        if (!specifics.isEmpty()) {
            var values = com.fartypoopy.sexmod.custommodel.CustomModels.parseValues(specifics);
            if (form.girlSpecificValid(values)) {
                form.applyGirlSpecific(values);
            }
        }
        form.setModelCode(data.getString(MODEL_KEY + form.kind().name()));
    }

    /** Original fw$a for a player form: the look is kept in the player's data for every later transformation. */
    public static void storeLook(CompanionEntity form, String code, java.util.List<Integer> values) {
        if (!(form.level() instanceof ServerLevel level)) {
            return;
        }
        form.formOwner().map(level.getServer().getPlayerList()::getPlayer).ifPresent(player -> {
            CompoundTag data = player.getPersistentData();
            data.putString(MODEL_KEY + form.kind().name(), code);
            if (!values.isEmpty() && form.girlSpecificValid(values)) {
                data.putString(SPECIFIC_KEY + form.kind().name(), com.fartypoopy.sexmod.custommodel.CustomModels.valuesToString(values));
            }
        });
    }

    /** Original ei.func_70619_bc / onUpdate: follow the player, copy the look, armour, swing and the strip timer. */
    static void tickForm(CompanionEntity form, ServerLevel level) {
        UUID ownerId = form.formOwner().orElse(null);
        if (ownerId == null) {
            return;
        }
        form.noPhysics = true;
        form.setNoGravity(true);
        FORMS.put(ownerId, form);
        ServerPlayer owner = CompanionEntity.scenePlayer(level, ownerId);
        if (owner == null) {
            form.teleportTo(form.getX(), level.getMinBuildHeight(), form.getZ()); // original: parked at y 0 while offline
            return;
        }
        if (owner.level() != level) {
            // original fu (respawn) / dimension change: the form goes where her player is
            form.changeDimension(new DimensionTransition(owner.serverLevel(), owner.position(), Vec3.ZERO, owner.getYRot(), 0.0F,
                    DimensionTransition.DO_NOTHING));
            return;
        }
        if (form.kind() == CompanionKind.ALLIE && !form.formOwnerSeen) {
            allowFlight(owner, true); // original e5.func_70619_bc(): the first time her player is found again
        }
        form.formOwnerSeen = true;
        String code = owner.getPersistentData().getString(MODEL_KEY + form.kind().name());
        if (!code.equals(form.modelCode())) {
            form.setModelCode(code);
        }
        if (form.kind() == CompanionKind.GOBLIN && tickGoblinCarry(form, owner, level)) {
            mirrorArmour(form, owner);
            return;
        }
        if (form.isInScene() || form.formWait() != 0) {
            // original b(UUID) / fu: the girl-player stays on her spot (flying, no gravity) until she jumps out
            Vec3 spot = form.formAnchor != null ? form.formAnchor : form.position();
            owner.setNoGravity(true);
            owner.setDeltaMovement(Vec3.ZERO);
            if (owner.position().distanceToSqr(spot) > 0.0025D) {
                owner.teleportTo(spot.x, spot.y, spot.z);
            }
            if (form.formWait() != 0) {
                form.moveTo(spot.x, spot.y, spot.z, form.formAnchorYaw, 0.0F);
                form.yBodyRot = form.formAnchorYaw;
                tickWait(form, owner, level);
            }
        } else {
            form.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
            form.yBodyRot = owner.yBodyRot;
            form.yHeadRot = owner.yHeadRot;
        }
        mirrorArmour(form, owner);
        tickActions(form, owner);
    }

    /** Original ei.f(player): the form wears the player's armour (an elytra in the chest slot). */
    private static void mirrorArmour(CompanionEntity form, ServerPlayer owner) {
        Map<EquipmentSlot, ItemStack> wanted = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            wanted.put(slot, ItemStack.EMPTY);
        }
        for (ItemStack stack : owner.getArmorSlots()) {
            if (stack.getItem() instanceof ElytraItem) {
                wanted.put(EquipmentSlot.CHEST, stack);
            } else if (stack.getItem() instanceof ArmorItem armour) {
                wanted.put(armour.getEquipmentSlot(), stack);
            }
        }
        wanted.forEach((slot, stack) -> {
            if (!ItemStack.matches(form.getItemBySlot(slot), stack)) {
                form.setItemSlot(slot, stack.copy());
            }
        });
    }

    /** Original ei.func_70619_bc (swing -> ATTACK) and e_.a (bow -> BOW), plus the strip timer of ei.D(). */
    private static void tickActions(CompanionEntity form, ServerPlayer owner) {
        if (form.formStripTicks >= 0) {
            form.formStripTicks++;
            if (form.formStripTicks == STRIP_FLIP) {
                setModel(form, modelIndex(form) == 0 ? 1 : 0); // original ei.D(): f(ah() == 0 ? 1 : 0)
            }
            if (form.formStripTicks >= STRIP_END) {
                form.formStripTicks = -1;
                form.setAction(CompanionAction.NONE);
            }
            return;
        }
        if (form.isInScene()) {
            return;
        }
        CompanionAction action = form.action();
        boolean bow = owner.isUsingItem() && owner.getUseItem().getItem() instanceof BowItem;
        if (bow && action != CompanionAction.BOW) {
            form.setAction(CompanionAction.BOW);
        } else if (!bow && action == CompanionAction.BOW) {
            form.setAction(CompanionAction.NONE);
        } else if (action == CompanionAction.NONE && owner.swinging) {
            form.setAction(CompanionAction.ATTACK);
            form.attackAnimTicks = 10;
        } else if (action == CompanionAction.ATTACK && --form.attackAnimTicks <= 0) {
            // original: the client's attackDone ends it (attack0 at 0.32 s, attack1/2 at 0.48 s); a swing then starts the next
            form.setAction(CompanionAction.NONE);
        }
    }

    /** Original fu (inventory button): strip or dress up; only while nothing else is going on. */
    public static void strip(ServerPlayer player) {
        CompanionEntity form = formOf(player);
        if (form == null || form.isInScene() || form.action() != CompanionAction.NONE || form.formStripTicks >= 0
                || !hasStripButton(form.kind())) {
            return;
        }
        form.formStripTicks = 0;
        form.setAction(CompanionAction.STRIP);
    }

    /** Original A() == false: Jenny, Ellie, Bia, Slime and Galath forms can strip / dress up. */
    public static boolean hasStripButton(CompanionKind kind) {
        return kind == CompanionKind.JENNY || kind == CompanionKind.ELLIE || kind == CompanionKind.BIA
                || kind == CompanionKind.SLIME || kind == CompanionKind.GALATH;
    }

    /** Original fu.a(RightClickBlock): sneak-clicking a bed puts a Jenny, Ellie or Luna form on its free side to wait. */
    @SubscribeEvent
    public static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        CompanionEntity form = player.level().isClientSide
                ? com.fartypoopy.sexmod.client.ClientFormAccess.formOf(player.getUUID())
                : player instanceof ServerPlayer serverPlayer ? formOf(serverPlayer) : null;
        if (form == null) {
            return;
        }
        var level = player.level();
        var pos = event.getPos();
        boolean busy = form.isInScene() || form.formWait() != 0;
        if (waitsAtBed(form.kind()) && level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            if (busy) {
                event.setCanceled(true);
                return;
            }
            if (!player.isShiftKeyDown() || level.isClientSide) {
                return; // the client still sends the click; the server decides
            }
            event.setCanceled(true);
            net.minecraft.core.BlockPos best = null;
            float yaw = 0.0F;
            net.minecraft.core.Direction[] sides = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                    net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};
            float[] yaws = {0.0F, 90.0F, 180.0F, -90.0F};
            for (int i = 0; i < sides.length; i++) {
                var side = pos.relative(sides[i]);
                if (level.getBlockState(side).isAir() && (best == null
                        || player.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(side))
                        < player.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(best)))) {
                    best = side;
                    yaw = yaws[i];
                }
            }
            if (best == null) {
                if (!level.isClientSide) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Bed is obscured"));
                }
                return;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                startWait(serverPlayer, net.minecraft.world.phys.Vec3.atBottomCenterOf(best), yaw);
            }
            return;
        }
        // original fu.b: a Slime form sneak-clicks the ground in front of her (empty hand, looking down) to wait there
        if (form.kind() == CompanionKind.SLIME && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
                && !busy && player.getXRot() >= 20.0F) {
            Vec3 hit = event.getHitVec().getLocation();
            if (hit.distanceTo(player.position()) > 3.0D || level.isClientSide) {
                return;
            }
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                startWait(serverPlayer, new Vec3(hit.x, Math.floor(hit.y), hit.z), player.getYRot());
            }
        }
    }

    /** Original co.a(PlayerTickEvent): after 100 ticks of the potion the player is offered the girls. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffectInstance effect = player.getEffect(ModPotions.HORNY_EFFECT);
        if (effect == null || effect.getDuration() > PICKER_AT) {
            return;
        }
        player.removeEffect(ModPotions.HORNY_EFFECT);
        Map<String, String> specifics = new java.util.HashMap<>();
        for (CompanionKind kind : new CompanionKind[] {CompanionKind.KOBOLD, CompanionKind.GOBLIN}) {
            String value = player.getPersistentData().getString(SPECIFIC_KEY + kind.name());
            if (!value.isEmpty()) {
                specifics.put(kind.name(), value);
            }
        }
        PacketDistributor.sendToPlayer(player, new PlayerFormNetwork.OpenPicker(specifics));
    }

    /** Original q (login): stale forms of this player go, the remaining one is reset. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.setInvisible(false);
        player.setNoGravity(false);
        player.noPhysics = false;
        if (!player.getAbilities().instabuild && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        CompanionEntity form = formOf(player);
        if (form != null) {
            form.setAction(CompanionAction.NONE);
            form.formStripTicks = -1;
            leave(player); // original s.a.a(form): a scene or wait from before the logout is over
        }
        // original q.a / q.b: the two developer accounts are given their Bia / Ellie form at every login
        if (player.getUUID().equals(DEVELOPER_BIA)) {
            become(player, CompanionKind.BIA);
        } else if (player.getUUID().equals(DEVELOPER_ELLIE)) {
            become(player, CompanionKind.ELLIE);
        }
    }

    private static final UUID DEVELOPER_BIA = UUID.fromString("b91e6484-8911-4def-ab04-9fa3452fca5f");
    private static final UUID DEVELOPER_ELLIE = UUID.fromString("adf20149-2adc-4a9d-9af5-8e9aeda019d6");

    /** Original fu (respawn): the form is moved to where the player respawned. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompanionEntity form = formOf(player);
            if (form != null && form.level() == player.level()) {
                form.teleportTo(player.getX(), player.getY(), player.getZ());
            }
        }
    }

    /** The per-kind looks live in the player's data; keep them through death and the End portal. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        CompoundTag now = event.getEntity().getPersistentData();
        for (String key : old.getAllKeys()) {
            if (key.startsWith(MODEL_KEY) || key.startsWith(SPECIFIC_KEY)) {
                now.putString(key, old.getString(key));
            }
        }
    }

    /** Original fu (LivingHurt / LivingDamage): Allie, Bee and Slime forms take no fall damage. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            CompanionEntity form = formOf(player);
            if (form != null && (form.kind() == CompanionKind.ALLIE || form.kind() == CompanionKind.BEE
                    || form.kind() == CompanionKind.SLIME)) {
                event.setCanceled(true);
            }
        }
    }

    /** Original fu (PlayerSleepInBed): a sneaking player with a form does not go to sleep (the bed is for her scenes). */
    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isShiftKeyDown() && formOf(player) != null) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    // ---------------------------------------------------------------- interactions (batch 2)

    /** Original ei.b(EntityPlayer) per form: the actions another player can ask her for. */
    public static java.util.List<String> actions(CompanionKind kind) {
        return switch (kind) {
            case JENNY -> java.util.List.of("action.names.blowjob", "action.names.boobjob");
            case ELLIE -> java.util.List.of("Face fuck");
            case BIA -> java.util.List.of("action.names.headpat");
            case SLIME -> java.util.List.of("action.names.blowjob");
            case BEE -> java.util.List.of("action.names.sex");
            case ALLIE -> java.util.List.of("action.names.deepthroat", "Reverse cowgirl");
            case LUNA -> java.util.List.of("action.names.touchboobs", "action.names.headpat");
            case KOBOLD -> java.util.List.of("anal", "oral", "mating");
            case GOBLIN -> java.util.List.of("anal", "paizuri");
            case GALATH -> java.util.List.of("cowgirl", "mating press", "ride"); // "ride" starts nothing in er.b(String, UUID)
            default -> java.util.List.of();
        };
    }

    /** Original ei.b(String, UUID) per form: the scene each action starts. */
    public static CompanionScene sceneFor(CompanionKind kind, String action) {
        return switch (kind) {
            case JENNY -> action.equals("action.names.blowjob") ? CompanionScene.JENNY_BLOWJOB
                    : action.equals("action.names.boobjob") ? CompanionScene.JENNY_BOOBJOB : null;
            case ELLIE -> action.equals("Face fuck") ? CompanionScene.ELLIE_CARRY : null;
            case BIA -> action.equals("action.names.headpat") ? CompanionScene.BIA_HEADPAT : null;
            case SLIME -> action.equals("action.names.blowjob") ? CompanionScene.SLIME_BLOWJOB : null;
            case BEE -> action.equals("action.names.sex") ? CompanionScene.BEE_SEX : null;
            case ALLIE -> action.equals("action.names.deepthroat") ? CompanionScene.ALLIE_DEEPTHROAT
                    : action.equals("Reverse cowgirl") ? CompanionScene.ALLIE_REVERSE_COWGIRL : null;
            case LUNA -> action.equals("action.names.touchboobs") ? CompanionScene.LUNA_TOUCH
                    : action.equals("action.names.headpat") ? CompanionScene.LUNA_HEADPAT : null;
            case KOBOLD -> action.equals("anal") ? CompanionScene.KOBOLD_ANAL : action.equals("oral") ? CompanionScene.KOBOLD_BLOWJOB
                    : action.equals("mating") ? CompanionScene.KOBOLD_MATING_PRESS : null;
            case GOBLIN -> action.equals("anal") ? CompanionScene.GOBLIN_NELSON
                    : action.equals("paizuri") ? CompanionScene.GOBLIN_PAIZURI : null;
            // original er: "cowgirl" is RAPE_INTRO -> RAPE_ON_GOING -> RAPE_CUM driven by the meter (no draining),
            // "mating press" starts in CORRUPT_SLOW
            case GALATH -> action.equals("cowgirl") ? CompanionScene.GALATH_COWGIRL
                    : action.equals("mating press") ? CompanionScene.GALATH_ANAL : null;
            default -> null;
        };
    }

    /**
     * The intro and loop of each form's wait (original ei.u() after fu's bed / ground action); null when her u() plays
     * nothing (Bia until her player picks, Kobold and Goblin: they only stand there).
     */
    public static String[] waitAnimations(CompanionKind kind) {
        return switch (kind) {
            case ELLIE -> new String[] {"sitdown", "sitdownidle"};
            case LUNA -> new String[] {"wait", "wait"};
            case JENNY, SLIME -> new String[] {"doggygoonbed", "doggywait"};
            default -> null;
        };
    }

    /** Original eg.a(String): Bia's girl-player picks at the bed - anal waits bent over, doggy sits down. */
    static String[] biaWaitAnimations(String action) {
        return switch (action) {
            case "anal" -> new String[] {"anal_prepare", "anal_wait"};
            case "doggy" -> new String[] {"sitdown", "sitdownidle"};
            default -> null;
        };
    }

    /** Ticks of the wait intro before the loop (the intro animations' lengths). */
    static int waitIntroTicks(CompanionKind kind) {
        return switch (kind) {
            case ELLIE -> 109;
            case LUNA -> 0;
            case BIA -> 111;
            default -> 131;
        };
    }

    /** Original ei.v(): every form but Allie, Bee, Slime and Galath can sneak-click a bed (Kobold and Goblin just wait). */
    public static boolean waitsAtBed(CompanionKind kind) {
        return kind == CompanionKind.JENNY || kind == CompanionKind.ELLIE || kind == CompanionKind.BIA || kind == CompanionKind.LUNA
                || kind == CompanionKind.KOBOLD || kind == CompanionKind.GOBLIN;
    }

    private record Request(UUID girl, UUID male, String action, long expires) {
    }

    /** Pending sex prompts by recipient (original w.a on the recipient's client, 1200 ticks). */
    private static final Map<UUID, Request> REQUESTS = new ConcurrentHashMap<>();

    /** Original g4$a (server): pass the request on to the other player. */
    public static void request(ServerPlayer sender, UUID girl, UUID male, String action, boolean toGirl) {
        if (!sender.getUUID().equals(toGirl ? male : girl)) {
            return;
        }
        ServerPlayer girlPlayer = sender.server.getPlayerList().getPlayer(girl);
        ServerPlayer malePlayer = sender.server.getPlayerList().getPlayer(male);
        if (girlPlayer == null) {
            FapCraft.LOGGER.info("Sex prompt invalid -> female player not found");
            return;
        }
        if (malePlayer == null) {
            FapCraft.LOGGER.info("Sex prompt invalid -> male player not found");
            return;
        }
        CompanionEntity form = formOf(girlPlayer);
        if (form == null || formOf(malePlayer) != null || !actions(form.kind()).contains(action)
                || girlPlayer.level() != malePlayer.level() || girlPlayer.distanceTo(malePlayer) > 16.0F) {
            return;
        }
        ServerPlayer recipient = toGirl ? girlPlayer : malePlayer;
        REQUESTS.put(recipient.getUUID(), new Request(girl, male, action, sender.level().getGameTime() + 1200L));
        PacketDistributor.sendToPlayer(recipient, new PlayerFormNetwork.Prompt(action, male, girl, toGirl));
    }

    /** Original eu$a: the asked player said yes; the form's scene starts with the male as partner. */
    public static void accept(ServerPlayer accepter, UUID girl, UUID male, String action) {
        Request request = REQUESTS.remove(accepter.getUUID());
        if (request == null || !request.girl().equals(girl) || !request.male().equals(male) || !request.action().equals(action)
                || accepter.level().getGameTime() > request.expires()) {
            return;
        }
        ServerPlayer girlPlayer = accepter.server.getPlayerList().getPlayer(girl);
        ServerPlayer malePlayer = accepter.server.getPlayerList().getPlayer(male);
        CompanionEntity form = girlPlayer == null ? null : formOf(girlPlayer);
        if (form == null || malePlayer == null || form.isInScene() || form.formWait() != 0) {
            return;
        }
        startAction(form, malePlayer, action);
    }

    public static void startActionForTest(CompanionEntity form, ServerPlayer partner, String action) {
        startAction(form, partner, action);
    }

    static void startAction(CompanionEntity form, ServerPlayer partner, String action) {
        CompanionScene scene = sceneFor(form.kind(), action);
        if (scene == null) {
            return;
        }
        if (scene == CompanionScene.JENNY_BOOBJOB || scene == CompanionScene.SLIME_BLOWJOB || form.kind() == CompanionKind.BEE
                || form.kind() == CompanionKind.KOBOLD || form.kind() == CompanionKind.GOBLIN) {
            // original es / ec / e9 / e7 / eq.b(String, UUID): f(0) - the bare model for the scene
            form.setOutfit(model(form.kind(), 0));
        }
        form.formAnchor = form.position();
        form.formAnchorYaw = 0.0F; // original ei.b(UUID): she faces yaw 0, her partner a block south of her facing 180
        if (scene == CompanionScene.GOBLIN_NELSON) {
            form.formAnchorYaw = 180.0F; // original eq.q(): she turns to his yaw (180)
        }
        form.formDirectStart = true;
        try {
            form.startFormScene(partner, scene);
        } finally {
            form.formDirectStart = false;
        }
    }

    /** Original s$a (ResetGirl): the girl-player jumps out of her wait or scene. */
    public static void leave(ServerPlayer owner) {
        CompanionEntity form = formOf(owner);
        if (form == null) {
            return;
        }
        if (form.isInScene()) {
            form.stopScene();
        } else if (form.formWait() != 0) {
            form.setFormWait(0);
            onFormSceneEnded(form);
        }
    }

    /**
     * Original ee.a(String, UUID) "animationFollowUp": Ellie's girl-player picks what follows her sit-down; original
     * eg.a(String): Bia's picks anal or doggy at the bed, undresses and starts the matching wait.
     */
    public static void chooseFollowUp(ServerPlayer owner, String action) {
        CompanionEntity form = formOf(owner);
        if (form == null || form.formWait() == 0 || form.isInScene()) {
            return;
        }
        if (form.kind() == CompanionKind.ELLIE) {
            form.formFollowUp = action.equals("action.names.cowgirl") ? CompanionScene.ELLIE_COWGIRL
                    : action.equals("action.names.missionary") ? CompanionScene.ELLIE_MISSIONARY : null;
        } else if (form.kind() == CompanionKind.BIA && form.formWaitAnims().isEmpty()) {
            String[] anims = biaWaitAnimations(action);
            if (anims == null) {
                return;
            }
            form.formFollowUp = action.equals("anal") ? CompanionScene.BIA_ANAL : CompanionScene.BIA_PRONE;
            form.setOutfit(model(CompanionKind.BIA, 0));
            form.formWaitTicks = 0;
            form.setFormWaitAnims(anims[0] + "|" + anims[1]);
            form.setFormWait(1);
        }
    }

    /** Original fu (bed / ground): she waits there; the next player to step up starts her scene. */
    public static void startWait(ServerPlayer owner, Vec3 anchor, float yaw) {
        CompanionEntity form = formOf(owner);
        if (form == null || form.isInScene() || form.formWait() != 0) {
            return;
        }
        owner.teleportTo(anchor.x, anchor.y, anchor.z);
        owner.setYRot(yaw);
        form.formAnchor = anchor;
        form.formAnchorYaw = yaw;
        form.formWaitTicks = 0;
        form.formApproachTicks = 0;
        form.formApproacher = null;
        form.formFollowUp = switch (form.kind()) {
            case JENNY -> CompanionScene.JENNY_DOGGY;
            case SLIME -> CompanionScene.SLIME_DOGGY;
            case LUNA -> CompanionScene.LUNA_SITTING;
            default -> null; // Ellie and Bia: their girl-player chooses; Kobold and Goblin: nothing follows
        };
        if (form.kind() == CompanionKind.JENNY || form.kind() == CompanionKind.SLIME) {
            setModel(form, 0); // original es.u() / fu.b (slime ground): currentModel 0
        }
        String[] anims = waitAnimations(form.kind());
        form.setFormWaitAnims(anims == null ? "" : anims[0] + "|" + anims[1]);
        form.moveTo(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        form.setFormWait(1);
    }

    /** Waiting: intro, then the loop; the nearest other player within reach of her spot starts the follow-up. */
    private static void tickWait(CompanionEntity form, ServerPlayer owner, ServerLevel level) {
        if (form.formWaitAnims().isEmpty()) {
            return; // nothing plays: she stands there until her player picks (Bia) or jumps out
        }
        form.formWaitTicks++;
        if (form.formWait() == 1 && form.formWaitTicks >= waitIntroTicks(form.kind())) {
            form.setFormWait(2);
        }
        if (form.formWait() != 2 || form.formFollowUp == null) {
            return;
        }
        if (form.formApproacher != null) {
            // original ed.a() / eg.a(): the partner was faded out; the scene starts when the countdown ends
            ServerPlayer partner = level.getServer().getPlayerList().getPlayer(form.formApproacher);
            if (partner == null) {
                partner = CompanionEntity.scenePlayer(level, form.formApproacher);
            }
            if (partner == null) {
                form.formApproacher = null;
                return;
            }
            if (--form.formApproachTicks > 0) {
                return;
            }
            form.formApproacher = null;
            startFollowUp(form, partner);
            return;
        }
        double reach = form.kind() == CompanionKind.LUNA ? 1.25D : 1.0D;
        ServerPlayer nearest = null;
        for (ServerPlayer other : level.players()) {
            if (other != owner && !other.isSpectator() && other.position().distanceTo(form.formAnchor) <= reach
                    && (nearest == null || other.distanceToSqr(form) < nearest.distanceToSqr(form))) {
                nearest = other;
            }
        }
        if (nearest == null) {
            return;
        }
        if (formOf(nearest) != null) {
            nearest.displayClientMessage(net.minecraft.network.chat.Component.literal("sowy no lesbo action yet uwu")
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), false);
            return;
        }
        int delay = switch (form.kind()) {
            case LUNA -> 25; // original ed.a(): the sitting starts 25 ticks after the player stepped up
            case BIA -> 22; // original eg.a(): em.j
            default -> 0;
        };
        if (delay == 0) {
            startFollowUp(form, nearest);
            return;
        }
        form.formApproacher = nearest.getUUID();
        form.formApproachTicks = delay;
        PacketDistributor.sendToPlayer(nearest, new com.fartypoopy.sexmod.network.TransitionPayload()); // original fh.b()
    }

    private static void startFollowUp(CompanionEntity form, ServerPlayer partner) {
        CompanionScene next = form.formFollowUp;
        form.setFormWait(0);
        if (form.kind() == CompanionKind.ELLIE || form.kind() == CompanionKind.LUNA) {
            form.setOutfit(model(form.kind(), 0)); // original ee / ed: currentModel 0 before her scene
        }
        if (next == CompanionScene.BIA_PRONE && form.formAnchor != null) {
            // original eg.a(): the doggy intro moves her one block on (ck.a(0, 0, 1, yaw)); her player goes along
            double yaw = Math.toRadians(form.formAnchorYaw);
            form.formAnchor = form.formAnchor.add(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        form.startFormScene(partner, next);
    }

    /** Original s$a.a(em): after her wait or scene the girl-player can move again, both stand on free ground. */
    static void onFormSceneEnded(CompanionEntity form) {
        if (!(form.level() instanceof ServerLevel level)) {
            return;
        }
        form.setFormWait(0);
        form.setFormWaitAnims("");
        form.formAnchor = null;
        form.formFollowUp = null;
        form.formApproacher = null;
        if (form.kind() == CompanionKind.BEE) {
            form.setOutfit(model(CompanionKind.BEE, 1)); // original e9.g(): f(1) when her scene ends
        }
        form.formOwner().map(id -> CompanionEntity.scenePlayer(level, id)).ifPresent(owner -> {
            owner.setNoGravity(false);
            Vec3 at = owner.position();
            while (!level.getBlockState(net.minecraft.core.BlockPos.containing(at)).isAir() && at.y < level.getMaxBuildHeight()) {
                at = at.add(0.0D, 1.0D, 0.0D);
            }
            owner.teleportTo(at.x, at.y, at.z);
        });
    }

    static Optional<CompanionEntity> cached(UUID owner) {
        return Optional.ofNullable(FORMS.get(owner));
    }

    // ---------------------------------------------------------------- goblin form carry (original eq / eq$a)

    /** Carried states: her player is held 2 blocks above the carrier and the form is drawn on the carrier like an NPC goblin. */
    public static boolean goblinCarried(CompanionEntity form) {
        int state = form.goblinState();
        return form.kind() == CompanionKind.GOBLIN && form.goblinCarrier().isPresent()
                && (state == GoblinBehavior.PICK_UP || state == GoblinBehavior.SHOULDER
                || state == GoblinBehavior.THROWING && form.goblinPhase() <= GOBLIN_THROW_RELEASE);
    }

    static final int GOBLIN_PICK_UP = 45;
    static final int GOBLIN_THROW_RELEASE = 15;
    static final int GOBLIN_THROW_END = 39;

    /**
     * Original eq$a.a(EntityInteract): a player without a form sneak-right-clicks a goblin girl-player and picks her up
     * (eq.c(player): only while she does nothing, nobody carries her yet and he carries no other goblin).
     */
    @SubscribeEvent
    public static void onPickUpGoblin(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer carrier) || !(event.getTarget() instanceof ServerPlayer target)
                || !carrier.isShiftKeyDown()) {
            return;
        }
        CompanionEntity form = formOf(target);
        if (form == null || form.kind() != CompanionKind.GOBLIN || formOf(carrier) != null) {
            return;
        }
        pickUpGoblin(form, carrier);
    }

    public static void pickUpGoblin(CompanionEntity form, ServerPlayer carrier) {
        if (form.isInScene() || form.formWait() != 0 || form.action() != CompanionAction.NONE
                || form.goblinState() != GoblinBehavior.NULL || form.goblinCarrier().isPresent()) {
            return;
        }
        if (GoblinBehavior.carries(carrier.serverLevel(), carrier)) {
            carrier.displayClientMessage(net.minecraft.network.chat.Component.literal("you are already carrying a Goblin"), true);
            return;
        }
        form.setGoblinCarrier(carrier.getUUID());
        form.setGoblinState(GoblinBehavior.PICK_UP);
        form.setGoblinPhase(0);
        form.formCarryTicks = 0;
    }

    /** Original ea.c() for a form (the wheel has no scenes for her): the left tile throws her away. */
    public static void goblinWheel(CompanionEntity form, ServerPlayer carrier, int op) {
        if (op == 0 && form.goblinState() == GoblinBehavior.SHOULDER
                && form.goblinCarrier().filter(carrier.getUUID()::equals).isPresent()) {
            form.setGoblinState(GoblinBehavior.THROWING);
            form.setGoblinPhase(0);
            form.formCarryTicks = 0;
        }
    }

    /** Original eq.d() / j() / o() / e(): pick-up, shoulder, throw, landing and standing up. True while she is held. */
    private static boolean tickGoblinCarry(CompanionEntity form, ServerPlayer owner, ServerLevel level) {
        int state = form.goblinState();
        if (state == GoblinBehavior.NULL) {
            return false;
        }
        form.formCarryTicks++;
        ServerPlayer carrier = form.goblinCarrier().map(id -> CompanionEntity.scenePlayer(level, id)).orElse(null);
        switch (state) {
            case GoblinBehavior.PICK_UP, GoblinBehavior.SHOULDER -> {
                if (carrier == null || carrier.level() != level) {
                    releaseGoblin(form, owner);
                    return false;
                }
                if (state == GoblinBehavior.PICK_UP && form.formCarryTicks >= GOBLIN_PICK_UP) {
                    form.setGoblinState(GoblinBehavior.SHOULDER);
                }
                holdGoblin(form, owner, carrier);
                return true;
            }
            case GoblinBehavior.THROWING -> {
                form.setGoblinPhase(Math.min(form.formCarryTicks, 255));
                if (form.formCarryTicks < GOBLIN_THROW_RELEASE) {
                    if (carrier != null) {
                        holdGoblin(form, owner, carrier);
                    }
                    return true;
                }
                if (form.formCarryTicks == GOBLIN_THROW_RELEASE) {
                    // original eq.d() at 15: her player flies off along the carrier's view at 1.5 and falls normally
                    owner.noPhysics = false;
                    owner.setNoGravity(false);
                    if (carrier != null) {
                        owner.setDeltaMovement(carrier.getLookAngle().scale(1.5D));
                        owner.hurtMarked = true;
                    }
                }
                if (form.formCarryTicks >= GOBLIN_THROW_END) {
                    form.setGoblinCarrier(null);
                    form.setGoblinState(GoblinBehavior.THROWN);
                    form.formCarryTicks = 0;
                }
                return false;
            }
            case GoblinBehavior.THROWN -> {
                // original eq.o(): 30 ticks on the ground, then she stands up
                if (!owner.onGround()) {
                    form.formCarryTicks = 0;
                } else if (form.formCarryTicks >= 30) {
                    form.setGoblinState(GoblinBehavior.STAND_UP);
                    form.formCarryTicks = 0;
                }
                return false;
            }
            case GoblinBehavior.STAND_UP -> {
                if (form.formCarryTicks >= 37) { // original eq.e()
                    form.setGoblinState(GoblinBehavior.NULL);
                    form.formCarryTicks = 0;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** Original eq.j() / eq$a.a(player): she sits on the carrier, her player 2 blocks above him without gravity. */
    private static void holdGoblin(CompanionEntity form, ServerPlayer owner, ServerPlayer carrier) {
        form.moveTo(carrier.getX(), carrier.getY(), carrier.getZ(), carrier.yBodyRot, 0.0F);
        form.yBodyRot = carrier.yBodyRot;
        form.yHeadRot = carrier.yBodyRot;
        owner.noPhysics = true;
        owner.setNoGravity(true);
        owner.setDeltaMovement(Vec3.ZERO);
        owner.fallDistance = 0.0F;
        Vec3 above = carrier.position().add(0.0D, 2.0D, 0.0D);
        if (owner.position().distanceToSqr(above) > 0.25D) {
            owner.teleportTo(above.x, above.y, above.z);
        }
        owner.setPos(above.x, above.y, above.z); // the client keeps itself there too (PlayerFormClient.holdCarriedGoblin)
    }

    private static void releaseGoblin(CompanionEntity form, ServerPlayer owner) {
        form.setGoblinCarrier(null);
        form.setGoblinState(GoblinBehavior.NULL);
        form.formCarryTicks = 0;
        owner.noPhysics = false;
        owner.setNoGravity(false);
    }
}
