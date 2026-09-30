package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.data.GalathBindingData;
import com.trolmastercard.sexmod.menu.CompanionInventoryMenu;
import com.trolmastercard.sexmod.registry.ModEntities;
import com.trolmastercard.sexmod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Shared 1.21.1 implementation for the eleven original companion entity IDs.
 *
 * <p>The original JAR had a deep obfuscated inheritance tree. This clean
 * implementation restores a server-authoritative baseline: animation,
 * wandering, ownership, follow/stay controls, gifting, save/reload, and synced
 * state. Character-specific scripted scenes are deliberately isolated from
 * this foundation so they can be ported without compromising world data.</p>
 */
public final class CompanionEntity extends PathfinderMob implements GeoEntity {
    private static final String ALLIE_RICH_REWARD_KEY = "sexmod:allie_rich_reward";
    private static final String SCENE_REWARD_DAY_KEY = "sexmod:scene_reward_day_";
    private static final int SCENE_CONTROL_COOLDOWN = 5;
    private static final int SCENE_ENVIRONMENT_CHECK_INTERVAL = 20;
    private static final double SCENE_PRIVACY_RADIUS = 12.0D;
    /** The player she is following right now (original "master"); transient, never saved. */
    private static final EntityDataAccessor<Optional<UUID>> DATA_MASTER =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_ACTION =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_DOWNED =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    /** 0 walk, 1 fast walk, 2 run: picks the movement animation. */
    private static final EntityDataAccessor<Integer> DATA_SPEED_CLASS =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_VARIANT =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_OUTFIT =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_HOME =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<String> DATA_SCENE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SCENE_PHASE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SCENE_PARTNER =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_FLYING =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);

    private final CompanionKind kind;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final SimpleContainer equipment = new SimpleContainer(7);
    private final CompanionStorage storage = new CompanionStorage(this);
    private boolean summonedByGalathCoin;
    private boolean technicalProxy;
    private int scenePhaseTicks;
    private int sceneControlCooldown;
    private int sceneEnvironmentTicks;
    private Vec3 scenePartnerReturnPosition;
    private float scenePartnerReturnYaw;
    private float scenePartnerReturnPitch;
    private Vec3 sceneAnchorPosition;
    private float sceneAnchorYaw;
    private boolean scenePreviousNoAi;
    private boolean scenePreviousNoGravity;
    private String lastAnimationKey = "";
    private int rangedAttackCooldown;
    private boolean richRewardGranted;
    /** Stable identity of this girl (original "girlID"); packets and saves address her by it. */
    private UUID girlId = UUID.randomUUID();
    private FollowState followState = FollowState.IDLE;
    private int followLinger;
    private int idleTimer;
    private int swingCooldown;
    private int reaggroCooldown;
    private int attackAnimTicks;
    private boolean pinned;
    private Vec3 pinPosition;
    private float pinYaw;
    private int goHomeTicks = -1;
    /** Scene the player asked for while she was still dressed; it starts once the strip has finished. */
    private CompanionScene queuedScene;

    public CompanionEntity(EntityType<? extends CompanionEntity> entityType, Level level, CompanionKind kind) {
        this(entityType, level, kind, false);
    }

    public CompanionEntity(
            EntityType<? extends CompanionEntity> entityType,
            Level level,
            CompanionKind kind,
            boolean technicalProxy
    ) {
        super(entityType, level);
        this.kind = kind;
        this.technicalProxy = technicalProxy;
        this.xpReward = 0;
        if (this.getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
        }
        this.equipment.addListener(ignored -> this.syncEquipmentSlots());
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        }) {
            this.setDropChance(slot, 0.0F);
        }
        if (technicalProxy) {
            this.setPersistenceRequired();
            this.setNoAi(true);
            this.setInvulnerable(true);
        }
    }

    public CompanionKind kind() {
        return this.kind;
    }

    public static AttributeSupplier.Builder createAttributes(CompanionKind kind) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, kind.maxHealth())
                .add(Attributes.MOVEMENT_SPEED, kind.movementSpeed())
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, switch (kind) {
                    case GALATH, MANGLELIE -> 7.0D;
                    case KOBOLD, GOBLIN -> 4.0D;
                    // Jenny, Ellie, Bia and Luna hit for exactly what their weapon adds (as in the original).
                    case JENNY, ELLIE, BIA, LUNA -> 0.0D;
                    default -> 3.0D;
                })
                .add(Attributes.ARMOR, kind == CompanionKind.GALATH ? 4.0D : 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, kind == CompanionKind.GALATH ? 0.20D : 0.0D);
    }

    public static boolean checkNaturalSpawnRules(
            EntityType<CompanionEntity> type,
            ServerLevelAccessor level,
            MobSpawnType spawnType,
            BlockPos position,
            RandomSource random
    ) {
        if (!Mob.checkMobSpawnRules(type, level, spawnType, position, random)) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel) {
            AABB area = new AABB(position).inflate(128.0D);
            return serverLevel.getEntitiesOfClass(
                    CompanionEntity.class,
                    area,
                    companion -> !companion.hasMaster() && !companion.isTechnicalProxy()
            ).size() < 2;
        }
        return true;
    }

    @Override
    protected void registerGoals() {
        // Same priorities as the original base entity.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MasterGoal());
        this.goalSelector.addGoal(2, new TemptGoal(
                this, 0.4D, Ingredient.of(Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT, Items.ENDER_PEARL), false));
        this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(5, new IdleLookGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.35D));

        // Kinds without the master/combat logic keep the old generic fighting until each one is ported.
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.15D, true) {
            @Override
            public boolean canUse() {
                return !CompanionEntity.this.kind.hasMasterAI() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !CompanionEntity.this.kind.hasMasterAI() && super.canContinueToUse();
            }
        });
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !CompanionEntity.this.kind.hasMasterAI() && super.canUse();
            }
        });
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MASTER, Optional.empty());
        builder.define(DATA_ACTION, CompanionAction.NONE.name());
        builder.define(DATA_DOWNED, false);
        builder.define(DATA_SPEED_CLASS, 0);
        builder.define(DATA_ATTACK_VARIANT, 0);
        builder.define(DATA_OUTFIT, OutfitMode.DEFAULT.serializedName());
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_HOME, Optional.empty());
        builder.define(DATA_SCENE, "");
        builder.define(DATA_SCENE_PHASE, 0);
        builder.define(DATA_SCENE_PARTNER, Optional.empty());
        builder.define(DATA_FLYING, false);
    }

    public Optional<UUID> masterId() {
        return this.entityData.get(DATA_MASTER);
    }

    public boolean hasMaster() {
        return this.masterId().isPresent();
    }

    public boolean isMaster(Player player) {
        return this.masterId().map(player.getUUID()::equals).orElse(false);
    }

    /** Sets (or with null clears) the follow target. Nothing is saved: after a reload she follows nobody. */
    public void setMaster(UUID master) {
        this.entityData.set(DATA_MASTER, Optional.ofNullable(master));
        if (master == null) {
            this.entityData.set(DATA_SPEED_CLASS, 0);
        }
    }

    /** The original's x(): stop following and go back to walking speed. */
    public void clearMaster() {
        this.setMaster(null);
    }

    public boolean isFollowing() {
        return this.hasMaster();
    }

    public UUID girlId() {
        return this.girlId;
    }

    public boolean isDowned() {
        return this.entityData.get(DATA_DOWNED);
    }

    public CompanionAction action() {
        return CompanionAction.byName(this.entityData.get(DATA_ACTION));
    }

    /** Same rule as the original: setting the current action again does nothing, and an attack never interrupts another action. */
    public void setAction(CompanionAction action) {
        CompanionAction next = action == null ? CompanionAction.NONE : action;
        CompanionAction current = this.action();
        if (next == current || (next == CompanionAction.ATTACK && current != CompanionAction.NONE)) {
            return;
        }
        this.entityData.set(DATA_ACTION, next.name());
    }

    private void setDowned(boolean downed) {
        this.entityData.set(DATA_DOWNED, downed);
        if (downed) {
            this.entityData.set(DATA_ACTION, CompanionAction.DOWNED.name());
            this.getNavigation().stop();
        } else if (this.action() == CompanionAction.DOWNED) {
            this.entityData.set(DATA_ACTION, CompanionAction.NONE.name());
        }
    }

    /** Called by the damage event: lethal damage while following leaves her downed at 1 HP; downed she takes no damage. */
    public float adjustIncomingDamage(float amount) {
        if (!this.kind.hasMasterAI() || this.technicalProxy) {
            return amount;
        }
        if (this.isDowned()) {
            return 0.0F;
        }
        if (this.hasMaster() && this.getHealth() - amount < 0.0F) {
            this.setDowned(true);
            return Math.max(0.0F, this.getHealth() - 1.0F);
        }
        return amount;
    }

    @Override
    public void heal(float amount) {
        super.heal(amount);
        if (this.isDowned() && this.getHealth() >= this.getMaxHealth()) {
            this.setDowned(false);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public OutfitMode outfit() {
        return OutfitMode.byName(this.entityData.get(DATA_OUTFIT));
    }

    public void setOutfit(OutfitMode outfit) {
        OutfitMode safeOutfit = outfit == null ? OutfitMode.DEFAULT : outfit;
        if (!this.kind.supportsOutfit(safeOutfit)) {
            safeOutfit = OutfitMode.DEFAULT;
        }
        this.entityData.set(DATA_OUTFIT, safeOutfit.serializedName());
    }

    /** Nude = the alternate model (the original's currentModel 0). */
    public boolean isNude() {
        return this.outfit() == OutfitMode.ALTERNATE;
    }

    public void toggleClothing() {
        if (this.kind.supportsOutfit(OutfitMode.ALTERNATE)) {
            this.setOutfit(this.isNude() ? OutfitMode.DEFAULT : OutfitMode.ALTERNATE);
        }
    }

    public int appearanceVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setAppearanceVariant(int variant) {
        this.entityData.set(
                DATA_VARIANT,
                Math.floorMod(variant, Math.max(1, this.kind.appearanceVariants()))
        );
    }

    public float appearanceScale() {
        return this.entityData.get(DATA_SCALE);
    }

    public void setAppearanceScale(float scale) {
        this.entityData.set(DATA_SCALE, Math.clamp(scale, 0.75F, 1.25F));
    }

    public Optional<BlockPos> homePos() {
        return this.entityData.get(DATA_HOME);
    }

    public void setHome(BlockPos home) {
        this.entityData.set(DATA_HOME, Optional.ofNullable(home).map(BlockPos::immutable));
    }

    public boolean isFlying() {
        return this.entityData.get(DATA_FLYING);
    }

    public boolean isTechnicalProxy() {
        return this.technicalProxy;
    }

    public SimpleContainer storage() {
        return this.storage;
    }

    public SimpleContainer equipment() {
        return this.equipment;
    }

    public CompanionScene currentScene() {
        return CompanionScene.byName(this.entityData.get(DATA_SCENE));
    }

    public int currentScenePhase() {
        return this.entityData.get(DATA_SCENE_PHASE);
    }

    public Optional<UUID> scenePartnerId() {
        return this.entityData.get(DATA_SCENE_PARTNER);
    }

    public boolean isInScene() {
        return this.currentScene() != null;
    }

    public CompanionScene.Phase currentSceneAnimation() {
        CompanionScene scene = this.currentScene();
        return scene == null ? null : scene.phase(this.currentScenePhase());
    }

    /**
     * The panel is offered when she follows nobody or follows this player (the scene check
     * happens where the panel opens).
     */
    public boolean canUsePanel(Player player) {
        return !this.hasMaster() || this.isMaster(player);
    }

    /** Kobolds sell their scene menu to anyone. */
    public boolean mayUseSceneActions(Player player) {
        return this.kind.opensMenuToStrangers() || this.canUsePanel(player);
    }

    /** The original's go-home routine: throw an ender pearl home, then teleport there. */
    public void startGoHome() {
        this.clearMaster();
        if (this.action() == CompanionAction.THROW_PEARL || this.isInScene() || this.isDowned()) {
            return;
        }
        BlockPos home = this.homePos().orElse(this.blockPosition());
        float yaw = (float) (Mth.atan2(this.getZ() - home.getZ(), this.getX() - home.getX()) * 57.29578D) + 90.0F;
        this.setAction(CompanionAction.THROW_PEARL);
        this.getNavigation().stop();
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.pinned = true;
        this.pinPosition = this.position();
        this.pinYaw = yaw;
        this.goHomeTicks = 0;
    }

    private void tickGoHome(ServerLevel serverLevel) {
        if (this.pinned && this.pinPosition != null) {
            this.setPos(this.pinPosition.x, this.pinPosition.y, this.pinPosition.z);
            this.setYRot(this.pinYaw);
            this.yHeadRot = this.pinYaw;
            this.setDeltaMovement(Vec3.ZERO);
        }
        if (this.goHomeTicks < 0) {
            return;
        }
        if (this.action() != CompanionAction.THROW_PEARL) {
            this.pinned = false;
            this.goHomeTicks = -1;
            return;
        }
        BlockPos home = this.homePos().orElse(this.blockPosition());
        Vec3 target = new Vec3(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D);
        this.goHomeTicks++;
        // The throw animation fires its "pearl" event at 0.96 s and again at 2.88 s.
        if (this.goHomeTicks == 19) {
            ThrownEnderpearl pearl = new ThrownEnderpearl(EntityType.ENDER_PEARL, serverLevel);
            pearl.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());
            Vec3 aim = target.subtract(this.position());
            pearl.shoot(aim.x, aim.y, aim.z, (float) Math.min(4.0D, aim.length() * 0.1D), 0.0F);
            serverLevel.addFreshEntity(pearl);
        } else if (this.goHomeTicks >= 58) {
            for (int burst = 0; burst < 32; burst++) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0D, this.getZ(),
                        1, 0.2D, 0.2D, 0.2D, 0.5D);
            }
            this.pinned = false;
            this.goHomeTicks = -1;
            this.teleportTo(target.x, target.y, target.z);
            this.fallDistance = 0.0F;
            this.setAction(CompanionAction.NONE);
            this.clearMaster();
        }
    }

    public void markSummonedByGalathCoin() {
        this.summonedByGalathCoin = true;
    }

    public boolean openControlMenu(ServerPlayer player) {
        if (!this.canUsePanel(player) || player.distanceToSqr(this) > 64.0D) {
            return false;
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                new CompanionInventoryMenu(containerId, inventory, this),
                        this.getDisplayName()
                ),
                buffer -> buffer.writeVarInt(this.getId())
        );
        return true;
    }

    /** Handles an entry of the right-click action panel; every check is repeated here because the client is not trusted. */
    public void performAction(ServerPlayer player, String action) {
        if (this.isInScene()) {
            return;
        }
        boolean commander = this.canUsePanel(player);
        switch (action) {
            case "followme" -> {
                if (commander) {
                    this.setMaster(player.getUUID());
                    player.displayClientMessage(
                            Component.translatable("message.sexmod.following", this.getDisplayName()), true);
                }
            }
            case "stopfollowme" -> {
                if (commander) {
                    this.clearMaster();
                    player.displayClientMessage(
                            Component.translatable("message.sexmod.staying", this.getDisplayName()), true);
                }
            }
            case "gohome" -> {
                if (commander) {
                    this.startGoHome();
                }
            }
            case "setnewhome" -> {
                if (commander) {
                    this.setHome(this.blockPosition());
                    player.displayClientMessage(Component.translatable("message.sexmod.home_set"), true);
                }
            }
            case "equipment" -> {
                if (commander) {
                    this.openControlMenu(player);
                }
            }
            default -> {
                CompanionScene scene = CompanionScene.byName(action);
                if (scene == null || !CompanionActions.actionsFor(this, player).contains(scene)) {
                    return;
                }
                if (!CompanionActions.canAfford(player, this, scene)) {
                    player.displayClientMessage(
                            Component.translatable("message.sexmod.cannot_afford", this.getDisplayName()), false);
                    return;
                }
                if (this.startScene(player, scene)) {
                    CompanionActions.pay(player, this, scene);
                }
            }
        }
    }

    public boolean openStorageMenu(ServerPlayer player) {
        if (!this.kind.hasStorage()
                || !this.canUsePanel(player)
                || player.distanceToSqr(this) > 64.0D) {
            return false;
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new net.minecraft.world.inventory.ChestMenu(
                                com.trolmastercard.sexmod.registry.ModMenus.COMPANION_STORAGE.get(),
                                containerId, inventory, this.storage, 3),
                        this.getDisplayName()
                ),
                buffer -> buffer.writeVarInt(this.getId())
        );
        return true;
    }

    public boolean startScene(Player player, CompanionScene scene) {
        if (!(this.level() instanceof ServerLevel serverLevel)
                || scene == null
                || scene.kind() != this.kind
                || this.isInScene()
                || !this.mayUseSceneActions(player)
                || player.distanceToSqr(this) > 64.0D
                || !this.isSceneEnvironmentValid(serverLevel, player, scene, true)) {
            return false;
        }

        this.queuedScene = null;
        if (this.kind == CompanionKind.JENNY
                && (scene == CompanionScene.JENNY_BLOWJOB || scene == CompanionScene.JENNY_BOOBJOB)
                && !this.isNude()) {
            // Like the original: she undresses first, then the chosen scene follows.
            this.queuedScene = scene;
            scene = CompanionScene.JENNY_STRIP;
        }

        SceneAnchor anchor = scene.anchorsOnBed()
                ? this.bedSceneAnchor(serverLevel).orElse(null)
                : null;
        if (anchor == null) {
            anchor = new SceneAnchor(
                    this.findGroundedSceneAnchor(serverLevel),
                    Mth.wrapDegrees(this.getYRot())
            );
        }

        this.getNavigation().stop();
        this.setTarget(null);
        this.scenePreviousNoAi = this.isNoAi();
        this.scenePreviousNoGravity = this.isNoGravity();
        this.sceneAnchorPosition = anchor.position();
        this.sceneAnchorYaw = anchor.yaw();
        this.setNoAi(true);
        this.setNoGravity(true);
        this.entityData.set(DATA_FLYING, false);
        this.scenePhaseTicks = 0;
        this.sceneControlCooldown = 0;
        this.sceneEnvironmentTicks = 0;
        this.entityData.set(DATA_SCENE, scene.serializedName());
        this.entityData.set(DATA_SCENE_PARTNER, Optional.of(player.getUUID()));

        if (scene.hasPartner()) {
            this.scenePartnerReturnPosition = player.position();
            this.scenePartnerReturnYaw = player.getYRot();
            this.scenePartnerReturnPitch = player.getXRot();
        } else {
            this.scenePartnerReturnPosition = null;
        }

        this.lockSceneTransforms(player, scene.hasPartner(), true);
        this.enterScenePhase(0);
        this.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.45F, 1.35F);
        return true;
    }

    /**
     * Flying companions can trigger a scene in mid air, which leaves the whole
     * animation suspended. This snaps the scene origin onto the closest surface
     * below when one exists, and otherwise keeps the current position so a scene
     * is never silently refused.
     */
    private Vec3 findGroundedSceneAnchor(ServerLevel serverLevel) {
        if (this.onGround()) {
            return this.position();
        }

        Vec3 from = this.position().add(0.0D, Math.min(1.0D, this.getBbHeight() * 0.5D), 0.0D);
        Vec3 to = new Vec3(
                from.x,
                Math.max(serverLevel.getMinBuildHeight(), from.y - 16.0D),
                from.z
        );
        BlockHitResult hit = serverLevel.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));
        if (hit.getType() == HitResult.Type.MISS) {
            return this.position();
        }

        return new Vec3(this.getX(), hit.getLocation().y + 0.01D, this.getZ());
    }

    /**
     * Keeps the companion pinned to the scene anchor and the partner inside the
     * model, where the {@code steve} bone renders it. The partner's view angles
     * are only initialised once, so the vanilla third person camera stays under
     * player control for the rest of the scene.
     */
    private void lockSceneTransforms(Player partner, boolean positionPartner, boolean initial) {
        if (this.sceneAnchorPosition == null) {
            return;
        }

        this.setPos(
                this.sceneAnchorPosition.x,
                this.sceneAnchorPosition.y,
                this.sceneAnchorPosition.z
        );
        this.xo = this.sceneAnchorPosition.x;
        this.yo = this.sceneAnchorPosition.y;
        this.zo = this.sceneAnchorPosition.z;
        this.setYRot(this.sceneAnchorYaw);
        this.yRotO = this.sceneAnchorYaw;
        this.setXRot(0.0F);
        this.xRotO = 0.0F;
        this.yBodyRot = this.sceneAnchorYaw;
        this.yBodyRotO = this.sceneAnchorYaw;
        this.yHeadRot = this.sceneAnchorYaw;
        this.yHeadRotO = this.sceneAnchorYaw;
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;

        if (!positionPartner || partner == null) {
            return;
        }

        if (partner.position().distanceToSqr(this.sceneAnchorPosition) > 1.0E-4D) {
            partner.teleportTo(
                    this.sceneAnchorPosition.x,
                    this.sceneAnchorPosition.y,
                    this.sceneAnchorPosition.z
            );
        }
        partner.setDeltaMovement(Vec3.ZERO);
        partner.fallDistance = 0.0F;

        if (initial) {
            // Start the orbit camera in front of the scene instead of behind it.
            partner.setYRot(Mth.wrapDegrees(this.sceneAnchorYaw + 180.0F));
            partner.setXRot(8.0F);
        }
    }

    public boolean canControlScene(Player player) {
        return this.isInScene()
                && this.mayUseSceneActions(player)
                && player.distanceToSqr(this) <= 64.0D
                && this.scenePartnerId().filter(player.getUUID()::equals).isPresent();
    }

    public boolean changeSceneIntensity(Player player, int direction) {
        CompanionScene scene = this.currentScene();
        CompanionScene.Phase currentPhase = scene == null
                ? null
                : scene.phase(this.currentScenePhase());
        if (scene == null
                || currentPhase == null
                || !currentPhase.loop()
                || direction == 0
                || !this.canControlScene(player)) {
            return false;
        }
        if (this.sceneControlCooldown > 0) {
            player.displayClientMessage(Component.translatable("message.sexmod.scene_control_wait"), true);
            return false;
        }

        int target = direction < 0
                ? scene.previousLoopPhase(this.currentScenePhase())
                : scene.nextLoopPhase(this.currentScenePhase());
        if (target < 0) {
            return false;
        }

        this.sceneControlCooldown = SCENE_CONTROL_COOLDOWN;
        this.enterScenePhase(target);
        return true;
    }

    public boolean requestSceneFinish(Player player) {
        CompanionScene scene = this.currentScene();
        CompanionScene.Phase currentPhase = scene == null
                ? null
                : scene.phase(this.currentScenePhase());
        if (scene == null
                || currentPhase == null
                || !currentPhase.loop()
                || !this.canControlScene(player)) {
            return false;
        }
        if (this.sceneControlCooldown > 0) {
            player.displayClientMessage(Component.translatable("message.sexmod.scene_control_wait"), true);
            return false;
        }

        this.sceneControlCooldown = SCENE_CONTROL_COOLDOWN;
        int finale = scene.finalePhase();
        if (finale >= 0 && this.currentScenePhase() != finale) {
            this.enterScenePhase(finale);
        } else {
            this.finishScene(true);
        }
        return true;
    }

    public void stopScene() {
        this.finishScene(false);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (super.isAlliedTo(other)) {
            return true;
        }

        if (other instanceof Player player && this.isMaster(player)) {
            return true;
        }

        if (other instanceof CompanionEntity companion) {
            Optional<UUID> master = this.masterId();
            return master.isPresent() && master.equals(companion.masterId());
        }

        return false;
    }

    @Override
    public boolean isPushable() {
        return !this.isInScene() && super.isPushable();
    }

    @Override
    public void tick() {
        super.tick();

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.tickCount == 1 && this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }

        if (this.rangedAttackCooldown > 0) {
            this.rangedAttackCooldown--;
        }
        if (this.sceneControlCooldown > 0) {
            this.sceneControlCooldown--;
        }

        this.tickScene(serverLevel);
        this.tickTargetSanity();
        this.tickGoHome(serverLevel);
        this.tickHealing(serverLevel);
        this.tickRangedCombat(serverLevel);
        this.tickFlight();

        if (this.tickCount % 20 == 0) {
            this.syncEquipmentSlots();
        }

        if (this.kind == CompanionKind.GALATH
                && this.summonedByGalathCoin
                && (this.tickCount == 1 || this.tickCount % 20 == 0)) {
            Optional<UUID> owner = this.masterId();
            if (owner.isEmpty()
                    || !GalathBindingData.get(serverLevel.getServer())
                    .isActive(owner.get(), this.getUUID())) {
                this.discard();
            }
        }
    }

    private void tickScene(ServerLevel serverLevel) {
        CompanionScene scene = this.currentScene();
        if (scene == null) {
            return;
        }

        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.lockSceneTransforms(null, false, false);

        ServerPlayer controller = this.scenePartnerId()
                .map(serverLevel.getServer().getPlayerList()::getPlayer)
                .orElse(null);
        if (controller == null
                || !controller.isAlive()
                || controller.level() != this.level()
                || controller.distanceToSqr(this) > 64.0D) {
            this.finishScene(false);
            return;
        }

        this.sceneEnvironmentTicks++;
        if (this.sceneEnvironmentTicks >= SCENE_ENVIRONMENT_CHECK_INTERVAL) {
            this.sceneEnvironmentTicks = 0;
            if (!this.isSceneEnvironmentValid(serverLevel, controller, scene, false)) {
                controller.displayClientMessage(
                        Component.translatable("message.sexmod.scene_environment_lost"),
                        true
                );
                this.finishScene(false);
                return;
            }
        }

        if (scene.hasPartner()) {
            controller.stopRiding();
        }
        this.lockSceneTransforms(controller, scene.hasPartner(), false);

        CompanionScene.Phase phase = scene.phase(this.currentScenePhase());
        if (phase == null) {
            this.finishScene(true);
            return;
        }

        this.scenePhaseTicks++;
        if (scene.isClothingToggle() && this.scenePhaseTicks == 60) {
            this.toggleClothing();
        }
        int timeout = phase.loop() && scene.hasInteractivePhases()
                ? phase.durationTicks() * 3
                : phase.durationTicks();
        if (this.scenePhaseTicks < timeout) {
            return;
        }

        int nextPhase = this.currentScenePhase() + 1;
        if (nextPhase >= scene.phases().size()) {
            this.finishScene(true);
        } else {
            this.enterScenePhase(nextPhase);
        }
    }

    private void enterScenePhase(int phaseIndex) {
        CompanionScene scene = this.currentScene();
        if (scene == null || scene.phase(phaseIndex) == null) {
            this.finishScene(true);
            return;
        }
        this.scenePhaseTicks = 0;
        this.entityData.set(DATA_SCENE_PHASE, phaseIndex);
        this.onScenePhaseEntered(scene, phaseIndex);
    }

    private void onScenePhaseEntered(CompanionScene scene, int phaseIndex) {
        if (!(this.level() instanceof ServerLevel)) {
            return;
        }

        String cuePath = this.sceneCuePath();
        if (cuePath != null && ModSounds.find(cuePath).isPresent()) {
            this.playSound(ModSounds.find(cuePath).orElseThrow().get(), 0.55F, 0.98F + phaseIndex * 0.03F);
        } else {
            this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.35F, 1.1F + phaseIndex * 0.04F);
        }
    }

    private String sceneCuePath() {
        return switch (this.kind) {
            case JENNY -> "girls.jenny.aftersessionmoan.aftersessionmoan0";
            case ELLIE -> "girls.ellie.aftersessionmoan.aftersessionmoan0";
            case BIA -> "girls.bia.breath.breath0";
            case LUNA -> "girls.luna.giggle.giggle0";
            case ALLIE -> "girls.allie.aftersessionmoan.aftersessionmoan0";
            case KOBOLD -> "girls.kobold.bjmoan.bjmoan0";
            case GALATH -> "girls.galath.breathing.breathing0";
            default -> null;
        };
    }

    private boolean isSceneEnvironmentValid(
            ServerLevel serverLevel,
            Player controller,
            CompanionScene scene,
            boolean notify
    ) {
        boolean privateArea = serverLevel.getEntitiesOfClass(
                Player.class,
                this.getBoundingBox().inflate(SCENE_PRIVACY_RADIUS),
                other -> !other.isSpectator() && !other.getUUID().equals(controller.getUUID())
        ).isEmpty();
        if (!privateArea) {
            if (notify) {
                controller.displayClientMessage(Component.translatable("message.sexmod.scene_private"), true);
            }
            return false;
        }

        if (scene.requiresBed() && !this.hasNearbyBed(serverLevel)) {
            if (notify) {
                controller.displayClientMessage(Component.translatable("message.sexmod.scene_bed_required"), true);
            }
            return false;
        }
        return true;
    }

    private boolean hasNearbyBed(ServerLevel serverLevel) {
        return this.findNearbyBed(serverLevel).isPresent();
    }

    private Optional<BlockPos> findNearbyBed(ServerLevel serverLevel) {
        BlockPos center = this.blockPosition();
        BlockPos closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos position : BlockPos.betweenClosed(
                center.offset(-4, -2, -4),
                center.offset(4, 2, 4)
        )) {
            if (!serverLevel.getBlockState(position).is(BlockTags.BEDS)) {
                continue;
            }

            double distance = position.distToCenterSqr(this.position());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = position.immutable();
            }
        }

        return Optional.ofNullable(closest);
    }

    /**
     * Places the scene on the mattress of a nearby bed and aligns it with the
     * bed axis. The bed poses are authored as if the characters were already
     * lying on it, so without this the whole animation plays wherever the
     * companion happened to stand and appears suspended in mid air.
     */
    private Optional<SceneAnchor> bedSceneAnchor(ServerLevel serverLevel) {
        return this.findNearbyBed(serverLevel).map(bedPos -> {
            BlockState state = serverLevel.getBlockState(bedPos);
            double x = bedPos.getX() + 0.5D;
            double z = bedPos.getZ() + 0.5D;
            float yaw = this.getYRot();

            if (state.getBlock() instanceof BedBlock
                    && state.hasProperty(BedBlock.FACING)
                    && state.hasProperty(BedBlock.PART)) {
                Direction facing = state.getValue(BedBlock.FACING);
                BlockPos other = state.getValue(BedBlock.PART) == BedPart.HEAD
                        ? bedPos.relative(facing.getOpposite())
                        : bedPos.relative(facing);

                if (serverLevel.getBlockState(other).is(BlockTags.BEDS)) {
                    x = (bedPos.getX() + other.getX()) / 2.0D + 0.5D;
                    z = (bedPos.getZ() + other.getZ()) / 2.0D + 0.5D;
                }

                yaw = facing.toYRot();
            }

            return new SceneAnchor(new Vec3(x, bedPos.getY(), z), Mth.wrapDegrees(yaw));
        });
    }

    private record SceneAnchor(Vec3 position, float yaw) {
    }

    private void finishScene(boolean completed) {
        CompanionScene scene = this.currentScene();
        ServerPlayer controller = null;

        if (this.level() instanceof ServerLevel serverLevel) {
            controller = this.scenePartnerId()
                    .map(serverLevel.getServer().getPlayerList()::getPlayer)
                    .orElse(null);
            if (controller != null
                    && scene != null
                    && scene.hasPartner()
                    && this.scenePartnerReturnPosition != null
                    && controller.level() == this.level()) {
                controller.teleportTo(
                        this.scenePartnerReturnPosition.x,
                        this.scenePartnerReturnPosition.y,
                        this.scenePartnerReturnPosition.z
                );
                controller.setYRot(this.scenePartnerReturnYaw);
                controller.setXRot(this.scenePartnerReturnPitch);
            }

            if (completed && scene != null && controller != null && !scene.isClothingToggle()) {
                if (scene == CompanionScene.ALLIE_RICH) {
                    this.grantAllieRichReward(controller, serverLevel);
                } else {
                    this.grantDailySceneReward(controller);
                }
                if (scene.hasPartner()) {
                    applyAfterglowEffects(controller);
                }
                controller.displayClientMessage(Component.translatable("message.sexmod.scene_completed"), true);
            } else if (!completed && controller != null) {
                controller.displayClientMessage(Component.translatable("message.sexmod.scene_stopped"), true);
            }
        }
        CompanionScene next = completed ? this.queuedScene : null;
        this.queuedScene = null;

        this.entityData.set(DATA_SCENE, "");
        this.entityData.set(DATA_SCENE_PHASE, 0);
        this.entityData.set(DATA_SCENE_PARTNER, Optional.empty());
        this.scenePhaseTicks = 0;
        this.sceneControlCooldown = 0;
        this.sceneEnvironmentTicks = 0;
        this.scenePartnerReturnPosition = null;
        this.sceneAnchorPosition = null;
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoAi(this.scenePreviousNoAi);
        this.setNoGravity(this.scenePreviousNoGravity);
        if (next != null && controller != null && controller.isAlive()) {
            this.startScene(controller, next);
        }
    }

    /**
     * Light-hearted exhaustion applied after a completed partner scene.
     *
     * <p>Levitation is deliberately short and is always paired with slow falling
     * that outlasts it, so the joke cannot turn into fall damage and inventory
     * loss in survival.</p>
     */
    private static void applyAfterglowEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 260, 0, false, true, true));
        player.fallDistance = 0.0F;
    }

    private void grantAllieRichReward(ServerPlayer player, ServerLevel serverLevel) {
        if (this.richRewardGranted
                || !this.isMaster(player)
                || player.getPersistentData().getBoolean(ALLIE_RICH_REWARD_KEY)) {
            return;
        }

        this.richRewardGranted = true;
        player.getPersistentData().putBoolean(ALLIE_RICH_REWARD_KEY, true);
        this.giveSceneReward(player, new ItemStack(Items.DIAMOND));
        this.giveSceneReward(player, new ItemStack(Items.GOLD_INGOT, 4));
        serverLevel.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                this.getX(),
                this.getY() + 0.8D,
                this.getZ(),
                24,
                0.5D,
                0.7D,
                0.5D,
                0.05D
        );
    }

    private void grantDailySceneReward(ServerPlayer player) {
        long rewardDay = this.level().getDayTime() / 24000L + 1L;
        String rewardKey = SCENE_REWARD_DAY_KEY + this.kind.registryPath();
        if (player.getPersistentData().getLong(rewardKey) == rewardDay) {
            player.displayClientMessage(Component.translatable("message.sexmod.scene_reward_cooldown"), true);
            return;
        }

        ItemStack reward = switch (this.kind) {
            case JENNY -> new ItemStack(Items.BREAD, 2);
            case ELLIE -> new ItemStack(Items.IRON_NUGGET, 3);
            case SLIME -> new ItemStack(Items.SLIME_BALL, 2);
            case BIA -> new ItemStack(Items.SWEET_BERRIES, 3);
            case BEE -> new ItemStack(Items.HONEY_BOTTLE);
            case LUNA -> new ItemStack(Items.STRING, 2);
            case ALLIE -> new ItemStack(Items.AMETHYST_SHARD);
            case KOBOLD -> new ItemStack(Items.COPPER_INGOT);
            case GOBLIN -> new ItemStack(Items.ARROW, 4);
            case GALATH -> new ItemStack(Items.BLAZE_POWDER);
            case MANGLELIE -> new ItemStack(Items.QUARTZ, 2);
        };
        player.getPersistentData().putLong(rewardKey, rewardDay);
        Component itemName = reward.getHoverName();
        int count = reward.getCount();
        this.giveSceneReward(player, reward);
        player.displayClientMessage(
                Component.translatable("message.sexmod.scene_reward", count, itemName),
                true
        );
    }

    private void giveSceneReward(Player player, ItemStack reward) {
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
    }

    private void tickTargetSanity() {
        if (this.attackAnimTicks > 0 && --this.attackAnimTicks == 0 && this.action() == CompanionAction.ATTACK) {
            this.setAction(CompanionAction.NONE);
        }
        if (this.isInScene()) {
            this.setTarget(null);
            return;
        }
        if (this.getTarget() != null
                && (!this.getTarget().isAlive() || this.isAlliedTo(this.getTarget()))) {
            this.setTarget(null);
        }
    }

    /** Original regeneration (Jenny, Ellie, Bia, Luna): every 80 ticks, more while nothing hostile is near. */
    private void tickHealing(ServerLevel serverLevel) {
        if (!this.kind.hasMasterAI()
                || this.technicalProxy
                || this.tickCount % 80 != 0
                || this.getHealth() >= this.getMaxHealth()) {
            return;
        }
        if (!this.hasMaster()) {
            this.heal(1.0F);
        } else {
            AABB box = this.getBoundingBox().inflate(7.0D, 1.0D, 7.0D);
            boolean hostileNear = !serverLevel.getEntitiesOfClass(Monster.class, box).isEmpty();
            float amount = hostileNear ? 1.0F : 4.0F;
            this.heal(amount);
            serverLevel.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.0D + this.random.nextDouble(),
                    this.getZ(), (int) amount, 1.0D, 1.0D, 1.0D, 0.02D);
        }
        if (this.isDowned() && !this.hasMaster()) {
            this.setDowned(false);
        }
    }

    private void tickRangedCombat(ServerLevel serverLevel) {
        LivingEntity target = this.getTarget();
        if (!this.kind.usesRangedMagic()
                || this.isInScene()
                || this.rangedAttackCooldown > 0
                || target == null
                || !target.isAlive()
                || this.distanceToSqr(target) > 256.0D
                || !this.hasLineOfSight(target)) {
            return;
        }

        EnergyBallEntity projectile = ModEntities.ENERGY_BALL.get().create(serverLevel);
        if (projectile == null) {
            return;
        }

        projectile.setOwner(this);
        projectile.setPos(this.getX(), this.getEyeY() - 0.15D, this.getZ());
        Vec3 aim = target.getEyePosition().subtract(projectile.position());
        projectile.shoot(aim.x, aim.y, aim.z, 1.1F, 2.0F);
        serverLevel.addFreshEntity(projectile);
        this.rangedAttackCooldown = 60;
        this.playSound(SoundEvents.BLAZE_SHOOT, 0.55F, this.kind == CompanionKind.GALATH ? 0.8F : 1.3F);
    }

    private void tickFlight() {
        if (!this.kind.canFly() || this.isInScene()) {
            this.entityData.set(DATA_FLYING, false);
            this.setNoGravity(false);
            return;
        }

        LivingEntity destination = this.getTarget();
        if (destination == null && this.hasMaster() && !this.kind.hasMasterAI()) {
            destination = this.masterPlayer().orElse(null);
        }

        if (destination == null) {
            this.entityData.set(DATA_FLYING, false);
            this.setNoGravity(false);
            return;
        }

        Vec3 offset = destination.position()
                .add(0.0D, Math.min(3.0D, destination.getBbHeight() + 0.75D), 0.0D)
                .subtract(this.position());
        double distance = offset.length();
        boolean shouldFly = distance > 5.0D || Math.abs(offset.y) > 1.5D || !this.onGround();
        this.entityData.set(DATA_FLYING, shouldFly);
        this.setNoGravity(shouldFly);

        if (!shouldFly || distance < 1.0E-4D) {
            return;
        }

        double speed = this.kind == CompanionKind.GALATH ? 0.22D : 0.14D;
        Vec3 desired = offset.normalize().scale(speed);
        this.setDeltaMovement(this.getDeltaMovement().scale(0.72D).add(desired));
        this.fallDistance = 0.0F;
    }

    private void syncEquipmentSlots() {
        EquipmentSlot[] slots = {
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };

        for (int index = 0; index < slots.length; index++) {
            this.setItemSlot(slots[index], this.equipment.getItem(index).copy());
        }
    }

    @Override
    protected void dropCustomDeathLoot(
            ServerLevel level,
            DamageSource damageSource,
            boolean recentlyHit
    ) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);
        // The original drops one plain item of each filled slot 0-5 and never the extra slot.
        for (int slot = 0; slot < this.equipment.getContainerSize(); slot++) {
            ItemStack stack = this.equipment.removeItemNoUpdate(slot);
            if (!stack.isEmpty() && slot < 6) {
                this.spawnAtLocation(new ItemStack(stack.getItem(), 1));
            }
        }
        for (int slot = 0; slot < this.storage.getContainerSize(); slot++) {
            ItemStack stack = this.storage.removeItemNoUpdate(slot);
            if (!stack.isEmpty()) {
                this.spawnAtLocation(stack);
            }
        }
        this.syncEquipmentSlots();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.isInScene()) {
            this.finishScene(false);
        }

        if (reason.shouldDestroy() && this.level() instanceof ServerLevel serverLevel) {
            if (this.kind == CompanionKind.GALATH && this.summonedByGalathCoin) {
                this.masterId().ifPresent(owner -> GalathBindingData
                        .get(serverLevel.getServer())
                        .clearIfMatches(owner, this.getUUID()));
            }
        }

        super.remove(reason);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (heldItem.isEmpty() && player.isShiftKeyDown()) {
            if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                this.openControlMenu(serverPlayer);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // Like the original, whatever the player holds, right-click opens the panel.
        if (this.level().isClientSide) {
            if (!this.isInScene() && this.mayUseSceneActions(player)) {
                com.trolmastercard.sexmod.client.ClientHooks.openActionPanel(this);
            } else if (this.kind.busyDialogue() != null) {
                player.sendSystemMessage(Component.literal("<")
                        .append(this.getDisplayName())
                        .append("> ")
                        .append(Component.translatable(this.kind.busyDialogue())));
            }
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public Component getName() {
        if (this.hasCustomName()) {
            return super.getName();
        }

        return Component.literal(this.kind.displayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("FapCraftKind", this.kind.registryPath());
        tag.putString("girlID", this.girlId.toString());
        if (!this.kind.hasClothingToggle()) {
            tag.putString("FapCraftOutfit", this.outfit().serializedName());
        }
        tag.putInt("FapCraftVariant", this.appearanceVariant());
        tag.putFloat("FapCraftScale", this.appearanceScale());
        this.homePos().ifPresent(home -> {
            tag.putDouble("homeX", home.getX());
            tag.putDouble("homeY", home.getY());
            tag.putDouble("homeZ", home.getZ());
        });
        tag.put("FapCraftEquipment", SlotNbt.save(this.equipment, this.registryAccess()));
        tag.put("FapCraftStorage", SlotNbt.save(this.storage, this.registryAccess()));
        tag.putBoolean("FapCraftGalathCoinSummon", this.summonedByGalathCoin);
        tag.putBoolean("FapCraftTechnicalProxy", this.technicalProxy);
        tag.putBoolean("FapCraftRichReward", this.richRewardGranted);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("girlID", Tag.TAG_STRING)) {
            try {
                this.girlId = UUID.fromString(tag.getString("girlID"));
            } catch (IllegalArgumentException ignored) {
                this.girlId = UUID.randomUUID();
            }
        }
        // Like the original: nothing about who she follows is saved.
        this.setMaster(null);

        // Dressed again after every reload for the girls who can undress (as in the original).
        this.setOutfit(this.kind.hasClothingToggle()
                ? OutfitMode.DEFAULT
                : OutfitMode.byName(tag.getString("FapCraftOutfit")));
        this.setAppearanceVariant(tag.getInt("FapCraftVariant"));
        if (tag.contains("FapCraftScale", Tag.TAG_FLOAT)) {
            this.setAppearanceScale(tag.getFloat("FapCraftScale"));
        }

        if (tag.contains("FapCraftHomeX", Tag.TAG_INT)) {
            this.setHome(new BlockPos(
                    tag.getInt("FapCraftHomeX"),
                    tag.getInt("FapCraftHomeY"),
                    tag.getInt("FapCraftHomeZ")
            ));
        } else if (tag.contains("homeX", Tag.TAG_ANY_NUMERIC)) {
            this.setHome(BlockPos.containing(
                    tag.getDouble("homeX"),
                    tag.getDouble("homeY"),
                    tag.getDouble("homeZ")
            ));
        }

        if (tag.contains("FapCraftEquipment", Tag.TAG_LIST)) {
            SlotNbt.load(
                    this.equipment,
                    tag.getList("FapCraftEquipment", Tag.TAG_COMPOUND),
                    this.registryAccess()
            );
        } else if (tag.contains("inventory", Tag.TAG_COMPOUND)) {
            CompoundTag legacyInventory = tag.getCompound("inventory");
            this.equipment.fromTag(
                    legacyInventory.getList("Items", Tag.TAG_COMPOUND),
                    this.registryAccess()
            );
        }

        if (tag.contains("FapCraftStorage", Tag.TAG_LIST)) {
            SlotNbt.load(this.storage, tag.getList("FapCraftStorage", Tag.TAG_COMPOUND), this.registryAccess());
        }

        if (!this.hasCustomName() && tag.contains("sexmod:customname", Tag.TAG_STRING)) {
            String legacyName = tag.getString("sexmod:customname").strip();
            if (!legacyName.isEmpty()) {
                this.setCustomName(Component.literal(legacyName.substring(
                        0,
                        Math.min(legacyName.length(), 32)
                )));
            }
        }

        this.summonedByGalathCoin = tag.getBoolean("FapCraftGalathCoinSummon");
        this.technicalProxy = tag.getBoolean("FapCraftTechnicalProxy") || this.technicalProxy;
        this.richRewardGranted = tag.getBoolean("FapCraftRichReward");

        // Running scenes are intentionally not persisted. This prevents a
        // disconnected player from being locked after a server restart.
        this.entityData.set(DATA_SCENE, "");
        this.entityData.set(DATA_SCENE_PHASE, 0);
        this.entityData.set(DATA_SCENE_PARTNER, Optional.empty());
        this.syncEquipmentSlots();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::animationPredicate));
    }

    private <T extends CompanionEntity> software.bernie.geckolib.animation.PlayState animationPredicate(
            AnimationState<T> state
    ) {
        CompanionScene.Phase sceneAnimation = this.currentSceneAnimation();
        if (sceneAnimation != null) {
            return state.setAndContinue(sceneAnimation.rawAnimation());
        }

        if (this.kind.hasMasterAI()) {
            CompanionAction action = this.action();
            if (action != CompanionAction.NONE) {
                return state.setAndContinue(this.kind.stateAnimation(
                        action.animationSuffix(this.entityData.get(DATA_ATTACK_VARIANT))));
            }
            if (state.isMoving()) {
                String suffix = switch (this.entityData.get(DATA_SPEED_CLASS)) {
                    case 2 -> "run";
                    case 1 -> "fastwalk";
                    default -> "walk";
                };
                return state.setAndContinue(this.kind.stateAnimation(suffix));
            }
        }

        if (state.getData(DataTickets.ENTITY) != null && state.isMoving()) {
            return state.setAndContinue(this.kind.walkAnimation());
        }

        return state.setAndContinue(this.kind.idleAnimation());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    private Optional<Player> masterPlayer() {
        return this.masterId().map(this.level()::getPlayerByUUID);
    }

    private enum FollowState {
        ATTACK, FOLLOW, IDLE, RIDE, DOWNED
    }

    /** Looks at nearby players only while she is idle, like the original watch goal. */
    private final class IdleLookGoal extends LookAtPlayerGoal {
        private IdleLookGoal() {
            super(CompanionEntity.this, Player.class, 3.0F, 1.0F);
        }

        @Override
        public boolean canUse() {
            return (!CompanionEntity.this.hasMaster() || CompanionEntity.this.followState == FollowState.IDLE)
                    && super.canUse();
        }
    }

    /**
     * The original follow/fight goal of Jenny, Ellie, Bia and Luna. It runs while she has a master and
     * ends (dropping the master) when that player is gone.
     */
    private final class MasterGoal extends Goal {
        private int repathTicks;
        private FollowState previous = FollowState.IDLE;

        private MasterGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return CompanionEntity.this.kind.hasMasterAI()
                    && !CompanionEntity.this.technicalProxy
                    && CompanionEntity.this.hasMaster();
        }

        @Override
        public boolean canContinueToUse() {
            return CompanionEntity.this.kind.hasMasterAI()
                    && CompanionEntity.this.masterPlayer().isPresent();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.previous = FollowState.IDLE;
            this.repathTicks = 0;
            CompanionEntity.this.followState = FollowState.IDLE;
        }

        @Override
        public void stop() {
            CompanionEntity self = CompanionEntity.this;
            self.getNavigation().stop();
            self.followState = FollowState.IDLE;
            self.followLinger = 0;
            if (self.action() != CompanionAction.THROW_PEARL && !self.isDowned()) {
                self.setAction(CompanionAction.NONE);
            }
            self.setMaster(null);
        }

        @Override
        public void tick() {
            CompanionEntity self = CompanionEntity.this;
            Player master = self.masterPlayer().orElse(null);
            if (master == null) {
                return;
            }
            if (self.swingCooldown > 0) {
                self.swingCooldown--;
            }
            if (self.reaggroCooldown > 0) {
                self.reaggroCooldown--;
            }

            FollowState next = this.decide(master);
            if (this.previous == FollowState.ATTACK && next != FollowState.ATTACK) {
                self.reaggroCooldown = 60;
                self.setTarget(null);
            }
            self.followState = next;
            switch (next) {
                case ATTACK -> this.attack(self.getTarget());
                case FOLLOW -> this.follow(master);
                case IDLE -> this.idle(master);
                default -> self.getNavigation().stop();
            }
            this.previous = next;
        }

        private FollowState decide(Player master) {
            CompanionEntity self = CompanionEntity.this;
            if (self.isDowned() || self.isInScene()) {
                return FollowState.DOWNED;
            }
            if (this.validTarget(self.getTarget(), master)) {
                return FollowState.ATTACK;
            }
            if (self.getTarget() != null) {
                self.setTarget(null);
            }
            LivingEntity attacker = self.getLastHurtByMob();
            if (this.validTarget(attacker, master)) {
                self.setTarget(attacker);
                return FollowState.ATTACK;
            }
            LivingEntity revenge = master.getLastHurtByMob();
            if (revenge != null
                    && master.tickCount - master.getLastHurtByMobTimestamp() < 140
                    && this.validTarget(revenge, master)) {
                self.setTarget(revenge);
                return FollowState.ATTACK;
            }
            if (this.previous != FollowState.FOLLOW) {
                LivingEntity nearest = self.level().getEntitiesOfClass(
                                Monster.class,
                                self.getBoundingBox().inflate(5.0D, 2.0D, 5.0D),
                                monster -> !(monster instanceof Creeper) && this.validTarget(monster, master))
                        .stream()
                        .min(java.util.Comparator.comparingDouble(monster -> self.distanceToSqr(monster)))
                        .orElse(null);
                if (nearest != null) {
                    self.setTarget(nearest);
                    return FollowState.ATTACK;
                }
            }
            if (self.distanceTo(master) > 5.0F) {
                self.followLinger = 60;
                return FollowState.FOLLOW;
            }
            if (this.previous == FollowState.FOLLOW && self.followLinger > 0) {
                self.followLinger--;
                return FollowState.FOLLOW;
            }
            return FollowState.IDLE;
        }

        private boolean validTarget(LivingEntity target, Player master) {
            CompanionEntity self = CompanionEntity.this;
            return target != null
                    && !(target instanceof CompanionEntity)
                    && self.reaggroCooldown <= 0
                    && target != self
                    && target != master
                    && target.isAlive()
                    && !target.isRemoved()
                    && target.distanceToSqr(master) <= 225.0D
                    && self.distanceToSqr(target) <= 400.0D
                    && !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
        }

        private void attack(LivingEntity target) {
            CompanionEntity self = CompanionEntity.this;
            if (target == null) {
                return;
            }
            self.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = self.distanceTo(target);
            double speed = distance < 2.0D ? 0.5D : 0.7D;
            self.entityData.set(DATA_SPEED_CLASS, distance < 2.0D ? 0 : 2);
            if (--this.repathTicks <= 0) {
                this.repathTicks = 5;
                self.getNavigation().moveTo(target, speed);
            } else {
                self.getNavigation().setSpeedModifier(speed);
            }
            if (distance < 1.9D && self.swingCooldown <= 0) {
                self.entityData.set(DATA_ATTACK_VARIANT, self.random.nextInt(3));
                self.setAction(CompanionAction.ATTACK);
                self.attackAnimTicks = 10;
                self.swing(InteractionHand.MAIN_HAND);
                self.doHurtTarget(target);
                self.swingCooldown = self.weaponSwingDelay();
            }
        }

        private void follow(Player master) {
            CompanionEntity self = CompanionEntity.this;
            double distance = self.distanceTo(master);
            double bonus = Math.floor(distance / 5.0D) * 0.2D;
            boolean sprinting = master.isSprinting();
            double speed = (sprinting ? 0.7D : 0.5D) + bonus;
            self.entityData.set(DATA_SPEED_CLASS, sprinting ? 2 : (bonus > 0.0D ? 1 : 0));
            if (distance < self.getAttributeValue(Attributes.FOLLOW_RANGE)) {
                if (--this.repathTicks <= 0) {
                    this.repathTicks = 5;
                    self.getNavigation().moveTo(master, speed);
                } else {
                    self.getNavigation().setSpeedModifier(speed);
                }
            } else {
                self.catchUpTeleport(master);
            }
            self.idleTimer = 300;
        }

        private void idle(Player master) {
            CompanionEntity self = CompanionEntity.this;
            self.entityData.set(DATA_SPEED_CLASS, 0);
            if (--self.idleTimer <= 0) {
                self.idleTimer = 200 + self.random.nextInt(100);
                self.getNavigation().moveTo(
                        master.getX() + 1 + self.random.nextInt(4),
                        master.getY(),
                        master.getZ() + 1 + self.random.nextInt(4),
                        0.5D
                );
            }
        }
    }

    /** Ticks per swing derived from the held weapon's attack speed, like the original. */
    private int weaponSwingDelay() {
        ItemAttributeModifiers modifiers = this.getMainHandItem().getOrDefault(
                net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double speed = 0.0D;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().is(Attributes.ATTACK_SPEED)) {
                speed = entry.modifier().amount();
            }
        }
        return Math.max(1, (int) Math.round(Math.abs(speed) / 3.373494D * 20.0D));
    }

    /** The original's catch-up teleport: a safe spot within ten blocks east/south of the master. */
    private void catchUpTeleport(Player master) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double x = master.getX() + this.random.nextInt(10);
            double z = master.getZ() + this.random.nextInt(10);
            BlockPos pos = BlockPos.containing(x, master.getY(), z);
            if (!this.level().getBlockState(pos.below()).isAir()
                    && this.level().getFluidState(pos).isEmpty()
                    && this.level().noCollision(this, this.getBoundingBox()
                    .move(x - this.getX(), master.getY() - this.getY(), z - this.getZ()))) {
                this.teleportTo(x, master.getY(), z);
                this.getNavigation().stop();
                this.setDeltaMovement(Vec3.ZERO);
                return;
            }
        }
        this.teleportTo(master.getX(), master.getY(), master.getZ());
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
    }
}
