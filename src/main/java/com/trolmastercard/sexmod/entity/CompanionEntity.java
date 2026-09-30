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
    private static final int SCENE_CONTROL_COOLDOWN = 5;
    private static final int SCENE_ENVIRONMENT_CHECK_INTERVAL = 20;
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
    private static final EntityDataAccessor<Boolean> DATA_BEE_TAMED =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BEE_CHEST =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SLIME_STATE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLIME_HORNY =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SLIME_BIRTH =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ALLIE_USES =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GALATH_BOOST =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
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
    /** Bed walk (original fg.a()/b()): the spot beside the bed she is walking to, while walking. */
    private BedWalk bedWalk;
    private int bedWalkTicks;
    /** The spot beside the bed, kept from arrival until the chained scenes end; used as the scene anchor. */
    private Vec3 bedSpotPosition;
    private float bedSpotYaw;
    private int attachCountdown = -1;
    /** True while the scene that follows Jenny's payment animation starts, so it is not asked to pay again. */
    private boolean skipPayment;
    private boolean skipDelay;
    private CompanionScene delayedScene;
    private UUID delayedScenePlayer;
    private int delayedSceneTicks;
    private int waitTicks;
    private Vec3 scenePartnerReturnPosition;
    private float scenePartnerReturnYaw;
    private float scenePartnerReturnPitch;
    private Vec3 sceneAnchorPosition;
    private float sceneAnchorYaw;
    private boolean scenePreviousNoAi;
    private boolean scenePreviousNoGravity;
    private String lastAnimationKey = "";
    private int rangedAttackCooldown;
    /** Stable identity of this girl (original "girlID"); packets and saves address her by it. */
    private UUID girlId = UUID.randomUUID();
    private FollowState followState = FollowState.IDLE;
    private int followLinger;
    private int idleTimer;
    private int swingCooldown;
    private int reaggroCooldown;
    private static final EntityDataAccessor<Optional<UUID>> DATA_TRIBE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_TRIBE_NAME =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_KOBOLD_SIZE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_BODY_COLOR =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_EYE_COLOR =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_KOBOLD_NAME =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_K_LEADER =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_K_FOLLOW =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_K_ATTACKED =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_K_STATE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_K_CHOP =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private int attackAnimTicks;
    private boolean pinned;
    private Vec3 pinPosition;
    private float pinYaw;
    private int goHomeTicks = -1;
    /** Scene the player asked for while she was still dressed; it starts once the strip has finished. */
    private SlimeBehavior slimeBehavior;
    private BeeBehavior beeBehavior;
    private com.trolmastercard.sexmod.tribe.KoboldBehavior koboldBehavior;
    private CompanionScene queuedScene;
    /** A scene to queue after {@link #queuedScene} has started (Allie: prepare, then the chosen scene). */
    private CompanionScene chainedScene;

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
        if (kind == CompanionKind.SLIME && !technicalProxy) {
            this.slimeBehavior = new SlimeBehavior(this);
            // registerGoals() already ran inside the Mob constructor, before this field existed: the original
            // slime has no goals at all, everything is scripted.
            this.goalSelector.removeAllGoals(goal -> true);
            this.targetSelector.removeAllGoals(goal -> true);
        }
        if (kind == CompanionKind.BEE && !technicalProxy) {
            this.beeBehavior = new BeeBehavior(this);
            this.configureBee();
        }
        if (kind == CompanionKind.KOBOLD && !technicalProxy) {
            this.koboldBehavior = new com.trolmastercard.sexmod.tribe.KoboldBehavior(this);
            this.configureKobold();
        }
        if (this.getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
        }
        if (!technicalProxy && (kind == CompanionKind.JENNY || kind == CompanionKind.ELLIE
                || kind == CompanionKind.BIA || kind == CompanionKind.LUNA)) {
            // Original e2 constructor: iron sword in slot 0, bow in slot 1 (Luna also a fishing rod in slot 6).
            this.equipment.setItem(0, new ItemStack(Items.IRON_SWORD));
            this.equipment.setItem(1, new ItemStack(Items.BOW));
            if (kind == CompanionKind.LUNA) {
                this.equipment.setItem(6, new ItemStack(Items.FISHING_ROD));
            }
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
                .add(Attributes.FLYING_SPEED, 0.4D)
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
        if (this.slimeBehavior != null) {
            return; // original Slime: empty goal set, everything is scripted in SlimeBehavior
        }
        // Same priorities as the original base entity.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new BedWalkGoal());
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
        builder.define(DATA_GALATH_BOOST, 0);
        builder.define(DATA_ALLIE_USES, 0);
        builder.define(DATA_BEE_TAMED, false);
        builder.define(DATA_BEE_CHEST, false);
        builder.define(DATA_SLIME_STATE, 0);
        builder.define(DATA_SLIME_HORNY, 0);
        builder.define(DATA_SLIME_BIRTH, -1);
        builder.define(DATA_TRIBE, Optional.empty());
        builder.define(DATA_TRIBE_NAME, "null");
        builder.define(DATA_KOBOLD_SIZE, 0.0F);
        builder.define(DATA_BODY_COLOR, "PURPLE");
        builder.define(DATA_EYE_COLOR, com.trolmastercard.sexmod.tribe.KoboldColor.values()[
                this.random.nextInt(com.trolmastercard.sexmod.tribe.KoboldColor.values().length)].name());
        builder.define(DATA_KOBOLD_NAME, com.trolmastercard.sexmod.tribe.KoboldBehavior.randomName(this.random));
        builder.define(DATA_K_LEADER, false);
        builder.define(DATA_K_FOLLOW, false);
        builder.define(DATA_K_ATTACKED, false);
        builder.define(DATA_K_CHOP, false);
        builder.define(DATA_K_STATE, 0);
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
        if (this.kind == CompanionKind.GALATH && this.hasMaster() && !this.technicalProxy) {
            if (this.galathDespawnTicks >= 0) {
                return 0.0F;
            }
            if (this.getHealth() - amount <= 0.0F) {
                // Original: a tamed Galath never dies, she is called back into the coin after 900 ms.
                this.galathDespawnTicks = 18;
                return 0.0F;
            }
        }
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

    private int galathDespawnTicks = -1;
    private int galathBoostTicks;
    private double galathBoostSpeed;

    /** Original bk "RequestRiding": she is mounted and flies by the rider's wing-flap impulses. */
    private boolean startRide(Player player) {
        if (!player.startRiding(this, true)) {
            return false;
        }
        this.getNavigation().stop();
        this.setNoGravity(true);
        this.setDeltaMovement(0.0D, 0.25D, 0.0D);
        return true;
    }

    public Player galathRider() {
        return this.kind == CompanionKind.GALATH && this.getFirstPassenger() instanceof Player rider ? rider : null;
    }

    /** Original d(vec) from packet ct: adds horizontal speed, vertical is half the y component. */
    public void applyFlapImpulse(Player sender, Vec3 impulse) {
        if (this.galathRider() != sender || impulse.lengthSqr() > 9.0D) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x + impulse.x, impulse.y / 2.0D, motion.z + impulse.z);
    }

    /** BOOST: 43 ticks; from tick 13 a fast dash along the rider's look that decays by 0.94 per tick. */
    public void startBoost(Player sender) {
        if (this.galathRider() == sender && this.galathBoostTicks <= 0) {
            this.galathBoostTicks = 43;
            this.galathBoostSpeed = 0.0D;
            this.entityData.set(DATA_GALATH_BOOST, 1);
        }
    }

    private void tickGalathRide() {
        Player rider = this.galathRider();
        if (rider == null) {
            if (this.galathBoostTicks > 0) {
                this.galathBoostTicks = 0;
                this.entityData.set(DATA_GALATH_BOOST, 0);
            }
            return;
        }
        this.getNavigation().stop();
        this.setNoGravity(true);
        float yaw = rider.getYHeadRot();
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        if (this.galathBoostTicks > 0) {
            int elapsed = 43 - this.galathBoostTicks;
            if (elapsed == 13) {
                this.galathBoostSpeed = 1.5D; // tune: the original uses 6.0 in its own unit
            }
            if (elapsed >= 13) {
                this.setDeltaMovement(rider.getLookAngle().scale(this.galathBoostSpeed));
                this.galathBoostSpeed *= 0.94D;
            }
            if (--this.galathBoostTicks <= 0) {
                this.entityData.set(DATA_GALATH_BOOST, 0);
            }
        }
    }

    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
        if (this.kind == CompanionKind.GALATH
                && (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING))) {
            return true; // original: fire, wall, drowning, fall and cramming do nothing to her
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        return this.kind != CompanionKind.GALATH && super.canBeAffected(effect);
    }

    @Override
    public boolean fireImmune() {
        return this.kind == CompanionKind.GALATH || super.fireImmune();
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

    // ---- Kobold tribe state (original ff data parameters 122-129) ----

    public com.trolmastercard.sexmod.tribe.KoboldBehavior koboldBehavior() {
        return this.koboldBehavior;
    }

    public Optional<UUID> tribeId() {
        return this.entityData.get(DATA_TRIBE);
    }

    public void setTribeId(UUID tribe) {
        this.entityData.set(DATA_TRIBE, Optional.ofNullable(tribe));
    }

    public String tribeName() {
        return this.entityData.get(DATA_TRIBE_NAME);
    }

    public void setTribeName(String name) {
        this.entityData.set(DATA_TRIBE_NAME, name == null ? "null" : name);
    }

    /** 0 .. 0.25; render scale is 0.75 + size, max health 10 - 25 * size. */
    public float koboldSize() {
        return this.entityData.get(DATA_KOBOLD_SIZE);
    }

    public void setKoboldSize(float size) {
        this.entityData.set(DATA_KOBOLD_SIZE, Math.clamp(size, 0.0F, 0.25F));
    }

    public com.trolmastercard.sexmod.tribe.KoboldColor bodyColor() {
        return com.trolmastercard.sexmod.tribe.KoboldColor.byName(this.entityData.get(DATA_BODY_COLOR));
    }

    public void setBodyColor(com.trolmastercard.sexmod.tribe.KoboldColor color) {
        this.entityData.set(DATA_BODY_COLOR, color.name());
    }

    public com.trolmastercard.sexmod.tribe.KoboldColor eyeColor() {
        return com.trolmastercard.sexmod.tribe.KoboldColor.byName(this.entityData.get(DATA_EYE_COLOR));
    }

    public void setEyeColor(com.trolmastercard.sexmod.tribe.KoboldColor color) {
        this.entityData.set(DATA_EYE_COLOR, color.name());
    }

    public String koboldName() {
        return this.entityData.get(DATA_KOBOLD_NAME);
    }

    public void setKoboldName(String name) {
        this.entityData.set(DATA_KOBOLD_NAME, name);
    }

    public boolean isTribeLeader() {
        return this.entityData.get(DATA_K_LEADER);
    }

    public void setTribeLeader(boolean leader) {
        this.entityData.set(DATA_K_LEADER, leader);
    }

    public boolean tribeFollow() {
        return this.entityData.get(DATA_K_FOLLOW);
    }

    public void setTribeFollow(boolean follow) {
        this.entityData.set(DATA_K_FOLLOW, follow);
    }

    public boolean koboldUnderAttack() {
        return this.entityData.get(DATA_K_ATTACKED);
    }

    public void setKoboldUnderAttack(boolean attacked) {
        this.entityData.set(DATA_K_ATTACKED, attacked);
    }

    /** 0 none, 1 attack, 2 sleep, 3 work (the original fp states ATTACK, SLEEP, MINE). */
    public int koboldState() {
        return this.entityData.get(DATA_K_STATE);
    }

    public void setKoboldState(int state) {
        this.entityData.set(DATA_K_STATE, state);
    }

    public boolean koboldChopping() {
        return this.entityData.get(DATA_K_CHOP);
    }

    public void setKoboldChopping(boolean chopping) {
        this.entityData.set(DATA_K_CHOP, chopping);
    }

    /** Wild tribes are unowned; the owner is the tribe master. */
    public boolean isOwnedKobold() {
        return this.hasMaster();
    }

    /** Original ff.func_184645_a before the action menu; null = continue with the menu. */
    private InteractionResult koboldInteract(Player player, ItemStack held) {
        boolean client = this.level().isClientSide;
        if (this.koboldBehavior.isBusy() || this.isInScene()) {
            return InteractionResult.PASS;
        }
        if (this.isMaster(player) && held.is(Items.NAME_TAG) && held.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                && !this.koboldUnderAttack() && this.koboldState() != com.trolmastercard.sexmod.tribe.KoboldBehavior.ST_SLEEP) {
            if (!client) {
                this.setKoboldName(held.getHoverName().getString());
                held.shrink(1);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (this.koboldUnderAttack() || this.koboldState() == com.trolmastercard.sexmod.tribe.KoboldBehavior.ST_SLEEP) {
            return InteractionResult.PASS;
        }
        if (com.trolmastercard.sexmod.tribe.KoboldBehavior.holdsStaff(player)) {
            if (!this.hasMaster()) {
                if (client && this.tribeId().isPresent() && !com.trolmastercard.sexmod.client.TribeClient.hasTribe()) {
                    com.trolmastercard.sexmod.client.ClientHooks.openNameTribe(this.tribeId().get());
                }
                return InteractionResult.sidedSuccess(client);
            }
            if (this.isMaster(player)) {
                if (!client && player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(
                            new SimpleMenuProvider(
                                    (containerId, inventory, ignored) -> new net.minecraft.world.inventory.ChestMenu(
                                            com.trolmastercard.sexmod.registry.ModMenus.COMPANION_STORAGE.get(),
                                            containerId, inventory, this.storage, 3),
                                    this.getDisplayName()),
                            buffer -> buffer.writeVarInt(this.getId()));
                }
                return InteractionResult.sidedSuccess(client);
            }
        }
        if (!client) {
            this.koboldBehavior.interact(player);
        }
        return null;
    }

    private void configureKobold() {
        // Original ff goals: swim, tempt (0.4), door, look-at-player. Everything else is scripted in KoboldBehavior.
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new TemptGoal(
                this, 0.4D, Ingredient.of(Items.EMERALD, Items.DIAMOND, Items.GOLD_INGOT, Items.ENDER_PEARL), false));
        this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(5, new IdleLookGoal());
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
        boolean bee = this.kind == CompanionKind.BEE;
        if (this.goHomeTicks == (bee ? 26 : 19)) {
            ThrownEnderpearl pearl = new ThrownEnderpearl(EntityType.ENDER_PEARL, serverLevel);
            pearl.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());
            Vec3 aim = target.subtract(this.position());
            pearl.shoot(aim.x, aim.y, aim.z, (float) Math.min(4.0D, aim.length() * 0.1D), 0.0F);
            serverLevel.addFreshEntity(pearl);
        } else if (this.goHomeTicks >= (bee ? 34 : 58)) {
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
        if (this.performBeeAction(player, action)) {
            return;
        }
        if (this.pickTalkFollowUp(player, action)) {
            return;
        }
        if (this.isInScene()) {
            return;
        }
        boolean commander = this.canUsePanel(player);
        switch (action) {
            case "followme" -> {
                if (commander) {
                    this.setMaster(player.getUUID());
                }
            }
            case "stopfollowme" -> {
                if (commander) {
                    this.clearMaster();
                    this.sayStopLine();
                }
            }
            case "gohome" -> {
                if (commander) {
                    this.clearMaster();
                    this.sayStopLine();
                    this.startGoHome();
                }
            }
            case "setnewhome" -> {
                if (commander) {
                    this.setHome(this.blockPosition());
                    this.saySetHomeLine();
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

    public boolean isBeeTamed() {
        return this.entityData.get(DATA_BEE_TAMED);
    }

    void setBeeTamed(boolean tamed) {
        this.entityData.set(DATA_BEE_TAMED, tamed);
    }

    public boolean hasBeeChest() {
        return this.entityData.get(DATA_BEE_CHEST);
    }

    /** Original fz: flying navigation, panic, look at players, random flying, and the follow goal. */
    private void configureBee() {
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        net.minecraft.world.entity.ai.navigation.FlyingPathNavigation flying =
                new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this, this.level());
        flying.setCanOpenDoors(false);
        flying.setCanFloat(true);
        flying.setCanPassDoors(true);
        this.navigation = flying;
        this.moveControl = new net.minecraft.world.entity.ai.control.FlyingMoveControl(this, 20, false);
        this.goalSelector.addGoal(0, new BeeFollowGoal());
        this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new IdleLookGoal());
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal(this, 1.0D));
    }

    /** Original fz.func_184645_a / GUI packet f3: the chest lets any player open her 27 slot inventory. */
    public boolean openBeeStorage(ServerPlayer player) {
        if (this.beeBehavior == null || !this.isBeeTamed() || !this.hasBeeChest()
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

    /** The bee screen's buttons (original packets n "master", gg, a6, f3). */
    private boolean performBeeAction(ServerPlayer player, String action) {
        if (this.beeBehavior == null || !action.startsWith("bee_")) {
            return false;
        }
        if (this.isInScene() || !this.isBeeTamed()) {
            return true;
        }
        switch (action) {
            case "bee_follow" -> this.setMaster(player.getUUID());
            case "bee_stop" -> this.clearMaster();
            case "bee_gohome" -> {
                this.clearMaster();
                this.startGoHome();
            }
            case "bee_sethome" -> this.setHome(this.blockPosition());
            case "bee_chest" -> this.openBeeStorage(player);
            default -> {
            }
        }
        return true;
    }

    int slimeState() {
        return this.entityData.get(DATA_SLIME_STATE);
    }

    void setSlimeState(int state) {
        this.entityData.set(DATA_SLIME_STATE, state);
    }

    int slimeHorny() {
        return this.entityData.get(DATA_SLIME_HORNY);
    }

    void setSlimeHorny(int level) {
        this.entityData.set(DATA_SLIME_HORNY, level);
    }

    int slimeBirth() {
        return this.entityData.get(DATA_SLIME_BIRTH);
    }

    void setSlimeBirth(int ticks) {
        this.entityData.set(DATA_SLIME_BIRTH, ticks);
    }

    public int slimeHornyLevel() {
        return this.slimeHorny();
    }

    public boolean isPregnant() {
        return this.slimeBirth() != -1;
    }

    /** How many wishes this lamp has granted including this one (1 = the first-time speech). */
    public int allieUses() {
        return this.entityData.get(DATA_ALLIE_USES);
    }

    /**
     * The lamp's summon (original ap, end of the 95 tick rub): she appears two blocks in front of the player,
     * facing them, and plays the speech for the first wish, a later wish, or the sand phobia scene.
     */
    public static boolean summonAllie(ServerPlayer player, int usesAfter) {
        ServerLevel level = player.serverLevel();
        for (CompanionEntity other : level.getEntitiesOfClass(CompanionEntity.class,
                player.getBoundingBox().inflate(64.0D), entity -> entity.kind() == CompanionKind.ALLIE)) {
            if (other.isMaster(player)) {
                return false; // she is still out for this player
            }
        }
        Vec3 pos = player.position().add(Vec3.directionFromRotation(0.0F, player.getYHeadRot()).scale(2.0D));
        CompanionEntity allie = ModEntities.get(CompanionKind.ALLIE).create(level);
        if (allie == null) {
            return false;
        }
        allie.moveTo(pos.x, pos.y, pos.z, Mth.wrapDegrees(player.getYRot() + 180.0F), player.getXRot());
        allie.entityData.set(DATA_ALLIE_USES, usesAfter);
        allie.setMaster(player.getUUID());
        allie.setNoGravity(true);
        allie.noPhysics = true;
        level.addFreshEntity(allie);
        CompanionScene scene = level.getBlockState(allie.blockPosition().below()).is(net.minecraft.world.level.block.Blocks.SAND)
                ? CompanionScene.ALLIE_SUMMON_SAND
                : usesAfter <= 1 ? CompanionScene.ALLIE_SUMMON : CompanionScene.ALLIE_SUMMON_NORMAL;
        if (!allie.startScene(player, scene)) {
            allie.discard();
            return false;
        }
        return true;
    }

    private void openAllieMenu(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player,
                new com.trolmastercard.sexmod.network.OpenSceneMenuPayload(
                        this.getId(),
                        List.of(CompanionScene.ALLIE_RICH.serializedName(),
                                CompanionScene.ALLIE_DEEPTHROAT.serializedName(),
                                CompanionScene.ALLIE_REVERSE_COWGIRL.serializedName())));
    }

    /** Original ev.a(String, UUID): pick a wish; "rich" grants the drops, the others go through the prepare animation. */
    private boolean pickAllieWish(ServerPlayer player, String action) {
        CompanionScene current = this.currentScene();
        if (current == null
                || !this.scenePartnerId().filter(player.getUUID()::equals).isPresent()
                || !(current.isMenuWaitPhase(this.currentScenePhase())
                || (current == CompanionScene.ALLIE_SUMMON && this.currentScenePhase() == 0 && this.scenePhaseTicks >= 190))) {
            return true;
        }
        boolean first = this.allieUses() <= 1;
        CompanionScene chosen = CompanionScene.byName(action);
        if (chosen == CompanionScene.ALLIE_RICH) {
            this.queuedScene = first ? CompanionScene.ALLIE_RICH : CompanionScene.ALLIE_RICH_NORMAL;
        } else if (chosen == CompanionScene.ALLIE_DEEPTHROAT || chosen == CompanionScene.ALLIE_REVERSE_COWGIRL) {
            this.queuedScene = first ? CompanionScene.ALLIE_PREPARE : CompanionScene.ALLIE_PREPARE_NORMAL;
            this.chainedScene = chosen;
        } else {
            return true;
        }
        this.finishScene(true);
        return true;
    }

    /** Original rich_MSG1 reward: diamond, emerald and gold ingot, 1 to 2 of each, dropped where she stands. */
    private void dropAllieRewards(ServerLevel serverLevel) {
        net.minecraft.world.item.Item[] rewards = {Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT};
        for (net.minecraft.world.item.Item item : rewards) {
            serverLevel.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    serverLevel, this.getX(), this.getY(), this.getZ(),
                    new ItemStack(item, 1 + this.random.nextInt(2))));
        }
    }

    /** Bia's second menu (Anal / Doggy) after Talk: she answers, then walks the chosen scene. */
    private boolean pickTalkFollowUp(ServerPlayer player, String action) {
        if (this.kind == CompanionKind.ALLIE) {
            return this.pickAllieWish(player, action);
        }
        CompanionScene chosen = CompanionScene.byName(action);
        if (this.currentScene() == CompanionScene.ELLIE_HUG
                && this.currentScenePhase() == 1
                && (chosen == CompanionScene.ELLIE_COWGIRL || chosen == CompanionScene.ELLIE_MISSIONARY)
                && this.scenePartnerId().filter(player.getUUID()::equals).isPresent()) {
            // Original HUGSELECTED: a short animation, then she looks for a bed and walks to it.
            this.queuedScene = chosen;
            this.enterScenePhase(2);
            return true;
        }
        if (this.currentScene() != CompanionScene.BIA_TALK
                || this.currentScenePhase() != 1
                || chosen == null
                || (chosen != CompanionScene.BIA_ANAL && chosen != CompanionScene.BIA_PRONE)
                || !this.scenePartnerId().filter(player.getUUID()::equals).isPresent()
                || !(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (this.isSceneEnvironmentValid(serverLevel, player, chosen, true)) {
            this.queuedScene = chosen;
            this.enterScenePhase(2);
        }
        return true;
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
        if (scene == CompanionScene.GALATH_RIDE) {
            return this.kind == CompanionKind.GALATH
                    && !this.isInScene()
                    && this.isMaster(player)
                    && player.distanceToSqr(this) <= 64.0D
                    && this.startRide(player);
        }
        if (!(this.level() instanceof ServerLevel serverLevel)
                || scene == null
                || scene.kind() != this.kind
                || this.isInScene()
                || !this.mayUseSceneActions(player)
                || player.distanceToSqr(this) > (scene.isWaitScene() ? 1024.0D : 64.0D)
                || !this.isSceneEnvironmentValid(serverLevel, player, scene, true)) {
            return false;
        }


        if ((this.kind == CompanionKind.JENNY || this.kind == CompanionKind.LUNA)
                && !this.skipPayment && this.needsPaymentStep(scene)) {
            // Original: every paid Jenny/Luna action starts with her payment animation, then U() runs the chosen scene.
            if (!this.startScene(player, this.kind == CompanionKind.JENNY
                    ? CompanionScene.JENNY_PAYMENT : CompanionScene.LUNA_PAYMENT)) {
                return false;
            }
            this.queuedScene = scene;
            return true;
        }

        if (this.walksToBed() && scene.requiresBed() && this.bedSpotPosition == null) {
            // Like the original: find a bed, walk to it, wait for the player there, then start.
            return this.beginBedWalk(serverLevel, player, scene);
        }

        if (this.kind == CompanionKind.GALATH && !this.skipDelay
                && (scene == CompanionScene.GALATH_COWGIRL || scene == CompanionScene.GALATH_ANAL)) {
            // Original: fade out and lock, the scene itself starts 1200 ms later.
            if (player instanceof ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer, new com.trolmastercard.sexmod.network.TransitionPayload());
            }
            this.delayedScene = scene;
            this.delayedScenePlayer = player.getUUID();
            this.delayedSceneTicks = 24;
            return true;
        }

        this.queuedScene = null;
        if (this.kind == CompanionKind.JENNY
                && (scene == CompanionScene.JENNY_BLOWJOB || scene == CompanionScene.JENNY_BOOBJOB)
                && !this.isNude()) {
            // Like the original: she undresses first, then the chosen scene follows.
            this.queuedScene = scene;
            scene = CompanionScene.JENNY_STRIP;
        }

        SceneAnchor anchor = scene == CompanionScene.ELLIE_HUG
                ? new SceneAnchor(player.position(), Mth.wrapDegrees(player.getYRot() + 180.0F))
                : this.bedSpotPosition != null
                ? new SceneAnchor(this.bedSpotPosition, this.bedSpotYaw)
                : scene.anchorsOnBed() ? this.bedSceneAnchor(serverLevel).orElse(null) : null;
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
        return true;
    }

    /** Original d(): a horny Ellie dashes at the nearest player within 10 blocks, hugs them and offers the bed scenes. */
    private void tickEllieHorny(ServerLevel serverLevel) {
        if (this.kind != CompanionKind.ELLIE || this.isInScene() || this.isDowned()
                || !this.hasEffect(com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT)) {
            return;
        }
        Player nearest = serverLevel.getNearestPlayer(this, 10.0D);
        if (nearest == null || nearest.isSpectator()) {
            return;
        }
        this.removeEffect(com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT);
        if (this.startScene(nearest, CompanionScene.ELLIE_DASH)) {
            this.queuedScene = CompanionScene.ELLIE_HUG;
        }
    }

    private boolean needsPaymentStep(CompanionScene scene) {
        if (this.kind == CompanionKind.LUNA) {
            return scene == CompanionScene.LUNA_SITTING || scene == CompanionScene.LUNA_TOUCH;
        }
        boolean paid = scene == CompanionScene.JENNY_BLOWJOB || scene == CompanionScene.JENNY_BOOBJOB
                || scene == CompanionScene.JENNY_DOGGY || (scene == CompanionScene.JENNY_STRIP && !this.isNude());
        return paid && !this.hasEffect(com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT);
    }

    /** The player's own chat line at the payment animation's 2.12 s keyframe (original paymentMSG2). */
    private void sayPaymentLine(ServerPlayer player) {
        CompanionScene queued = this.queuedScene;
        if (queued == null) {
            return;
        }
        String key = switch (queued) {
            case JENNY_STRIP -> "jenny.dialogue.showBobsandveganapls";
            case JENNY_BLOWJOB -> "jenny.dialogue.giveblowjob";
            case JENNY_DOGGY -> "jenny.dialogue.givesex";
            case JENNY_BOOBJOB -> "jenny.dialogue.givebooba";
            default -> null;
        };
        if (key != null && this.level() instanceof ServerLevel serverLevel) {
            Component line = Component.literal("<").append(player.getDisplayName()).append("> ")
                    .append(Component.translatable(key));
            for (ServerPlayer other : serverLevel.players()) {
                if (other.distanceToSqr(this) <= 40.0D * 40.0D) {
                    other.sendSystemMessage(line);
                }
            }
        }
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

    private boolean sceneFastHeld;
    private int interactiveLoopTicks;

    public void setSceneFastHeld(boolean held) {
        this.sceneFastHeld = held;
    }

    /** Jump with a full meter (original em.f): go to the finishing phase right away. */
    public void finishSceneFromMeter(Player player) {
        this.sceneControlCooldown = 0;
        this.requestSceneFinish(player);
    }

    /**
     * Interactive loops never time out by themselves: holding sneak moves to the fast loop at once, releasing it
     * returns to the slow loop when the fast animation ends (original fastDone), and only the meter ends the scene.
     */
    private void tickInteractiveLoop(CompanionScene scene, CompanionScene.Phase phase) {
        int current = this.currentScenePhase();
        int faster = scene.nextLoopPhase(current);
        int slower = scene.previousLoopPhase(current);
        if (faster >= 0 && this.sceneFastHeld) {
            this.enterScenePhase(faster);
            return;
        }
        if (this.scenePhaseTicks >= phase.durationTicks()) {
            if (slower >= 0 && !this.sceneFastHeld) {
                this.enterScenePhase(slower);
                return;
            }
            this.scenePhaseTicks = 0;
        }
        if (++this.interactiveLoopTicks > 12000) {
            // Safety net so a scene can never run forever (10 minutes).
            this.interactiveLoopTicks = 0;
            int finale = scene.finalePhase();
            if (finale >= 0) {
                this.enterScenePhase(finale);
            } else {
                this.finishScene(true);
            }
        }
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
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return this.slimeBehavior == null && super.causeFallDamage(fallDistance, multiplier, source); // original Slime: no fall damage
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

        if (this.delayedScene != null && --this.delayedSceneTicks <= 0) {
            CompanionScene pending = this.delayedScene;
            this.delayedScene = null;
            ServerPlayer pendingPlayer = serverLevel.getServer().getPlayerList().getPlayer(this.delayedScenePlayer);
            if (pendingPlayer != null && pendingPlayer.isAlive() && !this.isInScene()) {
                this.skipDelay = true;
                this.startScene(pendingPlayer, pending);
                this.skipDelay = false;
            }
        }
        if (this.kind == CompanionKind.GALATH) {
            this.tickGalathRide();
        }
        if (this.galathDespawnTicks >= 0 && --this.galathDespawnTicks < 0) {
            this.discard();
            return;
        }
        this.tickScene(serverLevel);
        if (this.kind == CompanionKind.ALLIE && !this.isInScene() && this.tickCount > 5) {
            this.discard(); // original ev: she vanishes once her state is NULL (also after a reload)
            return;
        }
        if (this.slimeBehavior != null) {
            this.slimeBehavior.tick(serverLevel);
        }
        if (this.beeBehavior != null) {
            this.beeBehavior.tick(serverLevel);
        }
        if (this.koboldBehavior != null) {
            this.koboldBehavior.tick(serverLevel);
        }
        this.tickEllieHorny(serverLevel);
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
                || controller.distanceToSqr(this) > (scene.isWaitScene() ? 1024.0D : 64.0D)) {
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
        if (scene == CompanionScene.JENNY_PAYMENT && this.scenePhaseTicks == 43) {
            this.sayPaymentLine(controller);
        }
        if ((scene == CompanionScene.ALLIE_RICH && this.scenePhaseTicks == 1)
                || (scene == CompanionScene.ALLIE_RICH_NORMAL && this.scenePhaseTicks == 10)) {
            this.dropAllieRewards(serverLevel);
        }
        if (scene == CompanionScene.ALLIE_SUMMON && this.currentScenePhase() == 0 && this.scenePhaseTicks == 194) {
            this.openAllieMenu(controller);
        }
        if (scene.isMenuWaitPhase(this.currentScenePhase())) {
            if (this.scenePhaseTicks > 12000) {
                this.finishScene(false);
            }
            return; // waits for the wish; the client reopens the menu if it is closed
        }
        if (scene.isWaitScene()) {
            this.tickWaitForPartner(controller, scene);
            return;
        }
        if (scene == CompanionScene.ELLIE_DASH && this.sceneAnchorPosition != null) {
            // She dashes onto the player over the 16 ticks.
            Vec3 goal = controller.position();
            this.sceneAnchorPosition = this.sceneAnchorPosition.lerp(goal, 0.3D);
            this.sceneAnchorYaw = (float) (Mth.atan2(goal.z - this.getZ(), goal.x - this.getX()) * 57.29578D) - 90.0F;
        }
        if (scene == CompanionScene.ELLIE_HUG && this.currentScenePhase() == 1) {
            if (this.scenePhaseTicks > 1200) {
                this.finishScene(false); // nobody chose anything
            }
            return; // stays in the hug idle until the menu answer arrives
        }
        if (scene.isClothingToggle() && this.scenePhaseTicks == 60) {
            this.toggleClothing();
        }
        if (phase.loop() && scene.hasInteractivePhases()) {
            this.tickInteractiveLoop(scene, phase);
            return;
        }
        int timeout = phase.durationTicks();
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
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (scene == CompanionScene.ELLIE_HUG && phaseIndex == 1) {
            ServerPlayer controller = this.scenePartnerId()
                    .map(serverLevel.getServer().getPlayerList()::getPlayer)
                    .orElse(null);
            if (controller != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        controller,
                        new com.trolmastercard.sexmod.network.OpenSceneMenuPayload(
                                this.getId(),
                                List.of(CompanionScene.ELLIE_COWGIRL.serializedName(), CompanionScene.ELLIE_MISSIONARY.serializedName())));
            }
        }
        if (scene == CompanionScene.ALLIE_SUMMON_NORMAL && phaseIndex == 1) {
            ServerPlayer owner = this.scenePartnerId()
                    .map(serverLevel.getServer().getPlayerList()::getPlayer)
                    .orElse(null);
            if (owner != null) {
                this.openAllieMenu(owner);
            }
        }
        if (scene == CompanionScene.BIA_TALK && phaseIndex == 1) {
            ServerPlayer controller = this.scenePartnerId()
                    .map(serverLevel.getServer().getPlayerList()::getPlayer)
                    .orElse(null);
            if (controller != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        controller,
                        new com.trolmastercard.sexmod.network.OpenSceneMenuPayload(
                                this.getId(),
                                List.of(CompanionScene.BIA_ANAL.serializedName(), CompanionScene.BIA_PRONE.serializedName())));
            }
        }
    }

    private boolean isSceneEnvironmentValid(
            ServerLevel serverLevel,
            Player controller,
            CompanionScene scene,
            boolean notify
    ) {
        if (scene.requiresBed() && !this.walksToBed() && !this.hasNearbyBed(serverLevel)) {
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

    // ---- Bed walk (original fg.a() / fg.b() and the ANAL_WAIT / SITDOWNIDLE attach) ----

    private record BedWalk(CompanionScene scene, UUID player, Vec3 target, float yaw) {
    }

    /** Bed side tables from the original: direction from the bed to her spot, and her facing there. */
    private static final int[][] BED_SIDES = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private static final float[] BED_SIDE_YAW = {0.0F, 180.0F, -90.0F, 90.0F};
    private static final int BED_SEARCH_RADIUS = 10;
    private static final double[] ELLIE_STAND_X = {0.5D, 0.5D, -0.18D, 1.18D};
    private static final double[] ELLIE_STAND_Z = {-0.18D, 1.18D, 0.5D, 0.5D};

    /** Bia and Jenny find a bed and walk to it before a bed scene (original flow); the others keep the old rule. */
    private boolean walksToBed() {
        return this.kind == CompanionKind.BIA || this.kind == CompanionKind.JENNY || this.kind == CompanionKind.ELLIE
                || this.kind == CompanionKind.LUNA;
    }

    public boolean isBedWalking() {
        return this.bedWalk != null;
    }

    public Vec3 bedWalkTarget() {
        return this.bedWalk == null ? null : this.bedWalk.target();
    }

    /** Searches for a bed and starts walking to it; false (with her refusal line) when none is usable. */
    public boolean beginBedWalk(ServerLevel serverLevel, Player player, CompanionScene scene) {
        BlockPos center = this.blockPosition();
        boolean anyBed = false;
        BedWalk best = null;
        double bestDistance = Double.MAX_VALUE;
        boolean doggy = scene == CompanionScene.BIA_PRONE;
        boolean jenny = this.kind == CompanionKind.JENNY;
        int radius = jenny ? 22 : BED_SEARCH_RADIUS;
        int vertical = jenny ? 3 : 4;

        for (BlockPos bed : BlockPos.betweenClosed(
                center.offset(-radius, -vertical, -radius),
                center.offset(radius, vertical, radius))) {
            if (!serverLevel.getBlockState(bed).is(BlockTags.BEDS)) {
                continue;
            }
            anyBed = true;
            for (int side = 0; side < 4; side++) {
                int dx = BED_SIDES[side][0];
                int dz = BED_SIDES[side][1];
                BlockPos stand = bed.offset(dx, 0, dz);
                if (!serverLevel.getBlockState(stand).isAir() || (!jenny && !serverLevel.getBlockState(stand.above()).isAir())) {
                    continue;
                }
                if (doggy && !serverLevel.getBlockState(bed.offset(-dx, 0, -dz)).is(BlockTags.BEDS)) {
                    continue;
                }
                Vec3 target = this.kind == CompanionKind.ELLIE
                        ? new Vec3(bed.getX() + ELLIE_STAND_X[side], stand.getY(), bed.getZ() + ELLIE_STAND_Z[side])
                        : new Vec3(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
                double distance = this.position().distanceToSqr(target);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new BedWalk(scene, player.getUUID(), target, BED_SIDE_YAW[side]);
                }
            }
        }

        if (best == null) {
            this.refuseBed(serverLevel, anyBed ? "jenny.dialogue.bedobscured" : "jenny.dialogue.nobedinsight");
            return false;
        }

        this.bedWalk = best;
        this.bedWalkTicks = 0;
        this.setTarget(null);
        return true;
    }

    private void refuseBed(ServerLevel serverLevel, String dialogueKey) {
        if (this.kind == CompanionKind.LUNA) {
            this.sayNearby(serverLevel, Component.literal(dialogueKey.endsWith("bedobscured")
                    ? "Heh.. the bed is obscured.. but I already ate the fish so nya~ hehe"
                    : "Heh.. there is no bed nearby.. but I already ate the fish so nya~ hehe"));
            this.playCue("girls.luna.giggle.giggle" + this.random.nextInt(3), 1.0F);
            return;
        }
        if (this.kind == CompanionKind.ELLIE) {
            this.sayNearby(serverLevel, Component.literal("no bed in sight..."));
            this.playCue("girls.ellie.sigh.sigh0", 6.0F);
            return;
        }
        com.trolmastercard.sexmod.registry.ModSounds.find(
                        this.kind == CompanionKind.JENNY ? "girls.jenny.hmph.hmph2" : "girls.bia.breath.breath2")
                .ifPresent(sound -> this.playSound(sound.get(), 1.0F, 1.0F));
        this.sayNearby(serverLevel, Component.translatable(dialogueKey));
    }

    /** The original's x() hook: only Ellie says something when she stops following (or goes home). */
    private void sayStopLine() {
        if (this.kind() == CompanionKind.ELLIE && this.level() instanceof ServerLevel serverLevel) {
            this.sayNearby(serverLevel, Component.literal("stay safe darling~"));
            this.playCue("girls.ellie.sigh.sigh1", 6.0F);
        }
    }

    /** The original's c() hook: each girl's line + sound when a new home is set. */
    private void saySetHomeLine() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        switch (this.kind()) {
            case JENNY -> {
                this.sayNearby(serverLevel, Component.literal("Alright, this is my new Home~"));
                this.playCue("girls.jenny.happyoh.happyoh1", 1.0F);
            }
            case ELLIE -> {
                this.sayNearby(serverLevel, Component.literal("Okay, I will be residing here then.."));
                this.playCue("girls.ellie.huh.huh0", 6.0F);
            }
            case BIA -> {
                this.sayNearby(serverLevel, Component.literal("I am living here now nya~"));
                this.playCue("girls.bia.breath.breath" + this.random.nextInt(4), 1.0F);
            }
            case LUNA -> {
                this.sayNearby(serverLevel, Component.literal("Love it here owo"));
                this.playCue("girls.luna.owo.owo" + this.random.nextInt(3), 1.0F);
            }
            default -> { }
        }
    }

    public void playCue(String path, float volume) {
        com.trolmastercard.sexmod.registry.ModSounds.find(path)
                .ifPresent(sound -> this.playSound(sound.get(), volume, 1.0F));
    }

    /** A line she "says": local chat for players within 40 blocks, like the original's chat broadcast. */
    public void sayNearby(ServerLevel serverLevel, Component line) {
        Component message = Component.literal("<").append(this.getDisplayName()).append("> ").append(line);
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(this) <= 40.0D * 40.0D) {
                player.sendSystemMessage(message);
            }
        }
    }

    private final class BedWalkGoal extends Goal {
        BedWalkGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return CompanionEntity.this.bedWalk != null;
        }

        @Override
        public boolean canContinueToUse() {
            return CompanionEntity.this.bedWalk != null;
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            CompanionEntity.this.bedWalkTicks = 0;
            this.navigate();
        }

        private void navigate() {
            Vec3 target = CompanionEntity.this.bedWalk.target();
            // The original asks for 0.35 on a base speed of 0.5 (absolute 0.175); keep the same pace here.
            double pace = CompanionEntity.this.kind == CompanionKind.LUNA ? 0.1D : 0.175D;
            double modifier = pace / Math.max(0.05D, CompanionEntity.this.getAttributeValue(Attributes.MOVEMENT_SPEED));
            CompanionEntity.this.getNavigation().moveTo(target.x, target.y, target.z, modifier);
        }

        @Override
        public void tick() {
            CompanionEntity self = CompanionEntity.this;
            BedWalk walk = self.bedWalk;
            if (walk == null || !(self.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(walk.player());
            if (player == null || !player.isAlive() || player.level() != self.level() || self.isInScene()) {
                self.bedWalk = null;
                return;
            }

            self.bedWalkTicks++;
            if (self.bedWalkTicks == 60 || self.bedWalkTicks == 120) {
                this.navigate();
            }
            double horizontal = Math.hypot(self.getX() - walk.target().x, self.getZ() - walk.target().z);
            boolean pathEnded = self.bedWalkTicks > 10 && self.getNavigation().isDone();
            if (horizontal < 0.6D || (pathEnded && horizontal < 1.6D) || self.bedWalkTicks > 200) {
                self.bedWalk = null;
                self.arriveAtBed(player, walk);
            }
        }
    }

    /** She is at the bed: pin her there, play the wait animation and remember the scene to start afterwards. */
    private void arriveAtBed(ServerPlayer player, BedWalk walk) {
        this.getNavigation().stop();
        this.bedSpotPosition = walk.target();
        this.bedSpotYaw = walk.yaw();
        this.setPos(walk.target().x, walk.target().y, walk.target().z);
        CompanionScene wait = walk.scene().waitScene();
        if (wait == null || !this.startScene(player, wait)) {
            this.bedSpotPosition = null;
            return;
        }
        this.queuedScene = walk.scene();
        this.waitTicks = 0;
    }

    /**
     * Original state ANAL_WAIT / SITDOWNIDLE: once the wait animation reached its idle loop and the
     * player stands within one block, the transition wipe starts and 22 ticks later the player is
     * attached and the chosen scene begins. The wait is capped so she cannot stand there forever.
     */
    private void tickWaitForPartner(ServerPlayer controller, CompanionScene scene) {
        this.waitTicks++;
        CompanionScene.Phase phase = scene.phase(this.currentScenePhase());
        if (phase != null && this.scenePhaseTicks >= phase.durationTicks()) {
            if (phase.loop()) {
                this.scenePhaseTicks = 0; // keep the idle loop going
            } else if (this.currentScenePhase() + 1 < scene.phases().size()) {
                this.enterScenePhase(this.currentScenePhase() + 1);
            }
        }
        if (this.waitTicks > 2400) {
            this.finishScene(false);
            return;
        }
        if (scene.phases().size() > 1 && this.currentScenePhase() < 1) {
            return;
        }
        if (this.attachCountdown < 0) {
            boolean jenny = this.kind == CompanionKind.JENNY;
            boolean ellie = this.kind == CompanionKind.ELLIE;
            boolean luna = this.kind == CompanionKind.LUNA;
            if (controller.distanceToSqr(this) <= (jenny ? 0.25D : ellie ? 2.25D : luna ? 1.5625D : 1.0D)) {
                this.attachCountdown = jenny ? 1 : ellie ? 20 : luna ? 25 : 22;
                if (!jenny) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            controller, new com.trolmastercard.sexmod.network.TransitionPayload());
                }
            }
        } else if (--this.attachCountdown <= 0) {
            this.attachCountdown = -1;
            this.finishScene(true);
        }
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

            if (!completed && controller != null && this.kind != CompanionKind.ALLIE) {
                controller.displayClientMessage(Component.translatable("message.sexmod.scene_stopped"), true);
            }
        }
        if (completed && this.slimeBehavior != null
                && (scene == CompanionScene.SLIME_BLOWJOB || scene == CompanionScene.SLIME_DOGGY)) {
            this.setSlimeBirth(SlimeBehavior.BIRTH_TICKS); // original: bjcDone / doggyCumDone send "pregnant" 2400
        }
        CompanionScene next = completed ? this.queuedScene : null;
        CompanionScene after = completed ? this.chainedScene : null;
        this.queuedScene = null;
        this.chainedScene = null;

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
        this.attachCountdown = -1;
        this.waitTicks = 0;
        this.sceneFastHeld = false;
        this.interactiveLoopTicks = 0;
        if (next != null && controller != null && controller.isAlive()) {
            this.skipPayment = true;
            this.startScene(controller, next);
            this.skipPayment = false;
            if (after != null && this.currentScene() == next) {
                this.queuedScene = after;
            }
        }
        if (this.kind == CompanionKind.ALLIE && !this.isInScene()) {
            this.discard(); // original: she vanishes when her state returns to NULL
        }
        if (!this.isInScene()) {
            this.bedSpotPosition = null;
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

    /** Drawn-bow counter: counts to 32 then fires; -20 is the cooldown after a shot (original p). */
    private int bowCharge;

    /** Original g.e(): a tipped arrow with Power and Flame from the bow, speed 1.6, inaccuracy 2.0, base damage 4.5. */
    private void fireBow(LivingEntity target, ItemStack bow) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, serverLevel);
        arrow.setOwner(this);
        arrow.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        var enchantments = serverLevel.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        int power = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.POWER), bow);
        int flame = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FLAME), bow);
        arrow.setBaseDamage(4.5D + (power > 0 ? power * 0.5D + 0.5D : 0.0D));
        if (flame > 0) {
            arrow.setRemainingFireTicks(100);
        }
        double dx = target.getX() - this.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2D, dz, 1.6F, 2.0F);
        serverLevel.addFreshEntity(arrow);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.random.nextFloat() * 0.4F + 0.8F));
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
        if (this.beeBehavior != null) {
            return; // the bee flies by navigation, see BeeBehavior
        }
        if (this.galathRider() != null) {
            this.entityData.set(DATA_FLYING, false);
            return; // controlled flight: only the rider's flaps move her
        }
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
        if (this.kind() == CompanionKind.BIA) {
            // Original e0 death: 0..3 brown wool on top of the loot table.
            int wool = this.random.nextInt(4);
            if (wool > 0) {
                this.spawnAtLocation(new ItemStack(net.minecraft.world.item.Items.BROWN_WOOL, wool));
            }
        }
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

        if (this.beeBehavior != null) {
            if (this.isBeeTamed() && !this.hasBeeChest() && heldItem.is(Items.CHEST)) {
                if (!this.level().isClientSide) {
                    this.entityData.set(DATA_BEE_CHEST, true);
                    heldItem.shrink(1); // original: no creative check
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            if (this.isBeeTamed()) {
                if (this.level().isClientSide) {
                    com.trolmastercard.sexmod.client.ClientHooks.openBeeScreen(this);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return InteractionResult.PASS; // untamed: nothing (no gifts in the original)
        }
        if (this.slimeBehavior != null) {
            return InteractionResult.PASS; // original Slime: no menu, scenes start when you walk into her
        }
        if (this.koboldBehavior != null) {
            InteractionResult result = this.koboldInteract(player, heldItem);
            if (result != null) {
                return result;
            }
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

        if (this.kind == CompanionKind.KOBOLD) {
            return Component.literal(this.koboldName());
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
        if (this.kind == CompanionKind.BEE) {
            tag.putBoolean("isTamed", this.isBeeTamed());
            tag.putBoolean("hasChest", this.hasBeeChest());
        }
        if (this.kind == CompanionKind.SLIME) {
            tag.putInt("hornyLevel", this.slimeHorny());
            tag.putInt("ticksUntilBirth", this.slimeBirth());
        }
        if (this.kind == CompanionKind.KOBOLD) {
            tag.putFloat("body_size", this.koboldSize());
            tag.putString("name", this.koboldName());
            tag.putString("bodyColor", this.bodyColor().name());
            tag.putString("eyeColor", this.eyeColor().name());
            this.masterId().ifPresent(owner -> tag.putString("master", owner.toString()));
            this.tribeId().ifPresent(tribe -> {
                tag.putUUID("tribeId", tribe);
                tag.putBoolean("isLeader", this.isTribeLeader());
                tag.putString("tribeName", this.tribeName());
            });
        }
        tag.putBoolean("FapCraftTechnicalProxy", this.technicalProxy);
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
        // Like the original: nothing about who she follows is saved (a Kobold's owner is saved, it is the tribe master).
        this.setMaster(null);
        if (this.kind == CompanionKind.KOBOLD) {
            if (tag.contains("body_size", Tag.TAG_ANY_NUMERIC)) {
                this.setKoboldSize(tag.getFloat("body_size"));
            }
            if (tag.contains("name", Tag.TAG_STRING) && !tag.getString("name").isEmpty()) {
                this.setKoboldName(tag.getString("name"));
            }
            if (tag.contains("bodyColor", Tag.TAG_STRING)) {
                this.setBodyColor(com.trolmastercard.sexmod.tribe.KoboldColor.byName(tag.getString("bodyColor")));
            }
            if (tag.contains("eyeColor", Tag.TAG_STRING)) {
                this.setEyeColor(com.trolmastercard.sexmod.tribe.KoboldColor.byName(tag.getString("eyeColor")));
            }
            if (tag.contains("master", Tag.TAG_STRING) && !tag.getString("master").isEmpty()) {
                try {
                    this.setMaster(UUID.fromString(tag.getString("master")));
                } catch (IllegalArgumentException ignored) {
                    // malformed owner: treat as wild
                }
            }
            if (tag.hasUUID("tribeId")) {
                this.setTribeId(tag.getUUID("tribeId"));
                this.setTribeLeader(tag.getBoolean("isLeader"));
                this.setTribeName(tag.getString("tribeName"));
            }
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10.0D - this.koboldSize() * 25.0D);
            this.setHealth(Math.min(this.getHealth(), this.getMaxHealth()));
            if (this.koboldBehavior != null) {
                this.koboldBehavior.onLoaded();
            }
        }

        // Dressed again after every reload for the girls who can undress (as in the original).
        this.setOutfit(this.kind.hasClothingToggle()
                ? OutfitMode.DEFAULT
                : OutfitMode.byName(tag.getString("FapCraftOutfit")));
        if (this.kind == CompanionKind.BEE) {
            if (tag.contains("isTamed")) {
                this.setBeeTamed(tag.getBoolean("isTamed"));
            }
            this.entityData.set(DATA_BEE_CHEST, tag.getBoolean("hasChest"));
        }
        if (this.kind == CompanionKind.SLIME) {
            this.setSlimeHorny(tag.getInt("hornyLevel"));
            this.setSlimeBirth(tag.contains("ticksUntilBirth", Tag.TAG_ANY_NUMERIC) ? tag.getInt("ticksUntilBirth") : -1);
            if (this.slimeHorny() != 0) {
                this.setOutfit(OutfitMode.ALTERNATE); // original: hornyLevel != 0 means nude
            }
        }
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

        // Running scenes are intentionally not persisted. This prevents a
        // disconnected player from being locked after a server restart.
        this.entityData.set(DATA_SCENE, "");
        this.entityData.set(DATA_SCENE_PHASE, 0);
        this.entityData.set(DATA_SCENE_PARTNER, Optional.empty());
        this.syncEquipmentSlots();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CompanionEntity>(this, "main", 0, this::animationPredicate)
                .setSoundKeyframeHandler(event -> com.trolmastercard.sexmod.client.KeyframeEvents
                        .handle(this, event.getKeyframeData().getSound())));
    }

    private <T extends CompanionEntity> software.bernie.geckolib.animation.PlayState animationPredicate(
            AnimationState<T> state
    ) {
        CompanionScene.Phase sceneAnimation = this.currentSceneAnimation();
        if (sceneAnimation != null) {
            return state.setAndContinue(sceneAnimation.rawAnimation());
        }

        if (this.beeBehavior != null) {
            return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop(
                    this.action() == CompanionAction.THROW_PEARL ? "animation.bee.throw_pearl"
                            : this.hasBeeChest() ? "animation.bee.idle_has_chest" : "animation.bee.idle"));
        }
        if (this.slimeBehavior != null) {
            return state.setAndContinue(switch (this.slimeState()) {
                case SlimeBehavior.JUMP_START -> software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.slime.jumpstart");
                case SlimeBehavior.JUMP_AIR -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.slime.jumpair");
                case SlimeBehavior.JUMP_END -> software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.slime.jumpend");
                case SlimeBehavior.UNDRESS -> software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.slime.undress");
                case SlimeBehavior.GO_ON_BED -> software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.slime.doggygoonbed");
                case SlimeBehavior.WAIT -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.slime.doggywait");
                default -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.slime.idle");
            });
        }
        if (this.kind == CompanionKind.GALATH && this.getFirstPassenger() instanceof Player) {
            return state.setAndContinue(this.entityData.get(DATA_GALATH_BOOST) > 0
                    ? software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.galath.boost")
                    : software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.galath.controlled_flight"));
        }
        if (this.koboldBehavior != null) {
            return state.setAndContinue(this.koboldAnimation());
        }
        if (this.kind.hasMasterAI()) {
            CompanionAction action = this.action();
            if (action != CompanionAction.NONE) {
                return state.setAndContinue(this.kind.stateAnimation(
                        action.animationSuffix(this.entityData.get(DATA_ATTACK_VARIANT))));
            }
            if (this.isWalkingVisibly()) {
                String suffix = switch (this.entityData.get(DATA_SPEED_CLASS)) {
                    case 2 -> "run";
                    case 1 -> "fastwalk";
                    default -> "walk";
                };
                return state.setAndContinue(this.kind.stateAnimation(suffix));
            }
        }

        if (this.isWalkingVisibly()) {
            return state.setAndContinue(this.kind.walkAnimation());
        }

        return state.setAndContinue(this.kind.idleAnimation());
    }

    /** Original ff animation predicate outside scenes: attack / sleep / work states, else idle, walk, run (armed when attacked). */
    private software.bernie.geckolib.animation.RawAnimation koboldAnimation() {
        return switch (this.koboldState()) {
            case com.trolmastercard.sexmod.tribe.KoboldBehavior.ST_ATTACK ->
                    software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.kobold.attack");
            case com.trolmastercard.sexmod.tribe.KoboldBehavior.ST_SLEEP -> this.kind.stateAnimation("sleep");
            case com.trolmastercard.sexmod.tribe.KoboldBehavior.ST_WORK -> this.kind.stateAnimation("fall_tree");
            default -> {
                boolean armed = this.koboldUnderAttack();
                if (this.isWalkingVisibly()) {
                    double speed = Math.abs(this.getX() - this.xo) + Math.abs(this.getZ() - this.zo);
                    yield this.kind.stateAnimation(armed ? "run_armed" : speed > 0.2D ? "run" : "walk");
                }
                yield this.kind.stateAnimation(armed ? "idle_armed" : "idle");
            }
        };
    }

    /**
     * GeckoLib's own isMoving() needs a fairly high limb speed, so a slow idle wander never counted as
     * walking. Any real horizontal movement should play the walk animation, as in the original.
     */
    private boolean isWalkingVisibly() {
        return this.walkAnimation.speed() > 0.02F;
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

    /** The bee's follow goal (original class h): follow at 5+ blocks, keep following for 60 ticks, never fight. */
    private final class BeeFollowGoal extends Goal {
        private int linger;

        private BeeFollowGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return CompanionEntity.this.hasMaster() && CompanionEntity.this.masterPlayer().isPresent()
                    && !CompanionEntity.this.isInScene();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void stop() {
            CompanionEntity.this.getNavigation().stop();
            if (CompanionEntity.this.masterPlayer().isEmpty()) {
                CompanionEntity.this.setMaster(null); // original: the master is lost when the goal ends
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            CompanionEntity self = CompanionEntity.this;
            Player master = self.masterPlayer().orElse(null);
            if (master == null) {
                return;
            }
            float distance = self.distanceTo(master);
            if (distance > 5.0F) {
                this.linger = 60;
            } else if (this.linger-- <= 0) {
                self.getNavigation().stop();
                return;
            }
            if (distance > 24.0F) {
                self.teleportTo(master.getX(), master.getY() + 1.0D, master.getZ());
                return;
            }
            double speed = Math.min(1.5D, 0.5D + Math.floor(distance / 3.0F) * 0.25D);
            self.getNavigation().moveTo(master, speed);
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
            ItemStack bow = self.equipment.getItem(1);
            if (bow.getItem() instanceof net.minecraft.world.item.BowItem
                    && distance > 6.0D && self.hasLineOfSight(target)) {
                // Original: stand and draw for 32 ticks, shoot, then a 20 tick cooldown.
                self.getNavigation().stop();
                if (self.bowCharge >= 0) {
                    self.setAction(CompanionAction.BOW);
                }
                if (++self.bowCharge >= 32) {
                    self.fireBow(target, bow);
                    self.bowCharge = -20;
                    self.setAction(CompanionAction.NONE);
                }
                return;
            }
            if (self.action() == CompanionAction.BOW) {
                self.setAction(CompanionAction.NONE);
            }
            self.bowCharge = 0;
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
