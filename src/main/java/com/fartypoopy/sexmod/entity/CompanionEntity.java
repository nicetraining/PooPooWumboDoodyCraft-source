package com.fartypoopy.sexmod.entity;

import com.fartypoopy.sexmod.data.GalathBindingData;
import com.fartypoopy.sexmod.menu.CompanionInventoryMenu;
import com.fartypoopy.sexmod.registry.ModEntities;
import com.fartypoopy.sexmod.registry.ModSounds;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
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
 * <p>The original's em / e2 / per-girl classes collapsed into one entity: shared state, the e2 master AI (follow,
 * fight, downed, go home), menus and scenes, with per-kind behaviour delegated to GoblinBehavior, KoboldBehavior,
 * GalathBoss, ManglelieBehavior, LunaFishing and the scene tables.</p>
 */
public final class CompanionEntity extends PathfinderMob implements GeoEntity {
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
    /** Original e2.M: what her "weapon" bone shows - 0 nothing, 1 the weapon (slot 0), 2 the bow (slot 1). */
    private static final EntityDataAccessor<Integer> DATA_WEAPON_MODE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    /** Original eb.az: Luna's stored fishing rod (slot 6), whose enchantments her drawn rod shows. */
    private static final EntityDataAccessor<ItemStack> DATA_LUNA_ROD =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.ITEM_STACK);
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
    /** The player she is walking up to before a scene (original d3.a(false) from the menu choice on). */
    private static final EntityDataAccessor<Optional<UUID>> DATA_APPROACH_PARTNER =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_BEE_TAMED =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    /** What the payment animation shows in her hand (original d_.a(): picked by the scene that follows). */
    private static final EntityDataAccessor<Integer> DATA_PAYMENT_ITEM =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SCARED =
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
    private static final EntityDataAccessor<Optional<UUID>> DATA_FAMILY =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_MANG_RIDING =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_MANG_TARGET =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_MANG_SHOT =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_BOSS_STATE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOSS_TARGET =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOSS_SUMMON =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOSS_FLAGS =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE_SERIAL =
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
    /** Ticks since the scene started (not reset between phases). */
    private int sceneTicks;
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
    private Vec3 sceneAnchorPosition;
    private float sceneAnchorYaw;
    private boolean scenePreviousNoAi;
    private boolean scenePreviousNoGravity;
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
    /** Original e4.M: the model string of a Kobold ("07-01-50-50-..."), eight fields, see {@link #koboldGene(int)}. */
    private static final EntityDataAccessor<String> DATA_KOBOLD_GENES =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_K_LEADER =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_K_FOLLOW =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_K_ATTACKED =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_FISH_STATE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FISH_CAST =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> DATA_FISH_ITEM =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_K_STATE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_K_CHOP =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_G_STATE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_G_CARRIER =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<ItemStack> DATA_G_ITEM =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_G_TAMED =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_G_QUEEN =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_G_MODEL =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    /** Original ei.ai: the player this girl is the form of (player-as-girl), empty for every normal girl. */
    private static final EntityDataAccessor<Optional<UUID>> DATA_FORM_OWNER =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** Player form waiting on a bed / the ground: 0 none, 1 intro, 2 waiting loop (original states STARTDOGGY / WAITDOGGY ...). */
    private static final EntityDataAccessor<Integer> DATA_FORM_WAIT =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    /** The form's wait animations "intro|loop" (without the kind prefix), empty when her wait plays nothing. */
    private static final EntityDataAccessor<String> DATA_FORM_WAIT_ANIMS =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    /** A Galath form on her model 0 (original er.c(): ah() == 0); both her models are the same geo, so the bra goes. */
    private static final EntityDataAccessor<Boolean> DATA_FORM_BARE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    /** Original em.b: the model code, the custom models she wears ("hat#shoes#"). */
    private static final EntityDataAccessor<String> DATA_MODEL_CODE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_G_FADE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_G_PHASE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_G_PREGGO =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private Vec3 forcedSceneAnchor;
    private float forcedSceneAnchorYaw;
    int attackAnimTicks;
    private boolean pinned;
    private Vec3 pinPosition;
    private float pinYaw;
    private int goHomeTicks = -1;
    /** Scene the player asked for while she was still dressed; it starts once the strip has finished. */
    private SlimeBehavior slimeBehavior;
    private BeeBehavior beeBehavior;
    private com.fartypoopy.sexmod.tribe.KoboldBehavior koboldBehavior;
    private LunaFishing lunaFishing;
    private GoblinBehavior goblinBehavior;
    private ManglelieBehavior manglelieBehavior;
    private GalathBoss boss;
    /** Client: Manglelie's head turn towards the nearest player or her arrow target (original f8.af in radians / W). */
    public float mangLookYaw;
    public float mangLookPitch;
    /** Client: eased head turn (original f8.T / ai), arm blend (V), last "had no target" flag (aj) and target point (R). */
    public float mangSmoothYaw;
    public float mangSmoothPitch;
    public float mangBlend;
    public boolean mangNoTargetPrev = true;
    public net.minecraft.world.phys.Vec3 mangTargetPos;
    /** Client: Galath's head pitch, body yaw and body scale bones as last animated (original f_.aE / bw / bm). */
    public float galathHeadPitch;
    public float galathBodyYaw;
    public float galathBodyScale;
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
            this.koboldBehavior = new com.fartypoopy.sexmod.tribe.KoboldBehavior(this);
            this.configureKobold();
        }
        if (kind == CompanionKind.LUNA && !technicalProxy) {
            this.lunaFishing = new LunaFishing(this);
        }
        if (kind == CompanionKind.GOBLIN && !technicalProxy) {
            this.goblinBehavior = new GoblinBehavior(this);
            this.configureGoblin();
        }
        if ((kind == CompanionKind.GALATH || kind == CompanionKind.MANGLELIE) && !technicalProxy) {
            this.manglelieBehavior = new ManglelieBehavior(this);
        }
        if (kind == CompanionKind.GALATH && !technicalProxy) {
            this.boss = new GalathBoss(this);
        }
        if (this.getNavigation() instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(true);
        }
        if (!technicalProxy && (kind == CompanionKind.JENNY || kind == CompanionKind.ELLIE
                || kind == CompanionKind.BIA || kind == CompanionKind.LUNA)) {
            // Original e2 constructor: iron sword in slot 0, bow in slot 1 (Luna also a fishing rod in slot 6).
            this.equipment.setItem(0, new ItemStack(kind == CompanionKind.LUNA ? Items.IRON_AXE : Items.IRON_SWORD));
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
                .add(Attributes.FOLLOW_RANGE, switch (kind) {
                    case BEE -> 16.0D;
                    case GALATH -> 50.0D;
                    default -> 30.0D;
                })
                .add(Attributes.ATTACK_DAMAGE, switch (kind) {
                    case GALATH, MANGLELIE -> 7.0D;
                    case KOBOLD, GOBLIN -> 4.0D;
                    // Jenny, Ellie, Bia and Luna hit for exactly what their weapon adds (as in the original).
                    case JENNY, ELLIE, BIA, LUNA -> 0.0D;
                    default -> 3.0D;
                })
                .add(Attributes.FLYING_SPEED, kind == CompanionKind.GALATH ? 0.6D : 0.4D);
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
        if (type.getDescriptionId().endsWith("manglelie")
                && (spawnType == MobSpawnType.NATURAL || spawnType == MobSpawnType.CHUNK_GENERATION)) {
            // original f8.getCanSpawnHere: no Galath or Manglelie spawn point within 700 blocks, then this one is recorded
            var data = GalathBindingData.get(level.getLevel().getServer());
            if (data.spawnNear(position, 700.0D, true, true)) {
                return false;
            }
            data.addSpawn(position, true);
        }
        return true;
    }

    /** Manglelie and the wild Galath boss run entirely on their own scripts. */
    boolean noGenericAi() {
        return this.kind == CompanionKind.MANGLELIE || this.kind == CompanionKind.GALATH;
    }

    public boolean wildBoss() {
        return this.boss != null && !this.technicalProxy && !this.hasMaster();
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
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.35D) {
            @Override
            public boolean canUse() {
                // original em.z (wander 0.35) is also Manglelie's (f8 only adds bt); not while she rides her mother
                boolean wanders = CompanionEntity.this.kind == CompanionKind.MANGLELIE
                        ? !CompanionEntity.this.isPassenger() : !CompanionEntity.this.noGenericAi();
                return !CompanionEntity.this.fishingHoldsHer() && wanders && super.canUse();
            }
        });

        if (this.kind == CompanionKind.MANGLELIE) {
            // original bt (AvoidEntity for players, 20 blocks, 1.0 / 1.2): off with a mother or near a tamed Galath
            this.goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.AvoidEntityGoal<>(
                    this, Player.class, 20.0F, 1.0D, 1.2D) {
                private boolean suppressed() {
                    if (CompanionEntity.this.familyId() != null) {
                        return true;
                    }
                    return !CompanionEntity.this.level().getEntitiesOfClass(CompanionEntity.class,
                            CompanionEntity.this.getBoundingBox().inflate(20.0D),
                            c -> c.kind == CompanionKind.GALATH && c.isAlive() && c.hasMaster()).isEmpty();
                }

                @Override
                public boolean canUse() {
                    return !this.suppressed() && super.canUse();
                }

                @Override
                public boolean canContinueToUse() {
                    return !this.suppressed() && super.canContinueToUse();
                }

                @Override
                public void start() {
                    CompanionEntity.this.entityData.set(DATA_SCARED, true);
                    super.start();
                }

                @Override
                public void stop() {
                    CompanionEntity.this.entityData.set(DATA_SCARED, false);
                    super.stop();
                }
            });
        }

        // Kinds without the master/combat logic keep the old generic fighting until each one is ported.
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.15D, true) {
            @Override
            public boolean canUse() {
                return !CompanionEntity.this.kind.hasMasterAI() && !CompanionEntity.this.noGenericAi() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !CompanionEntity.this.kind.hasMasterAI() && !CompanionEntity.this.noGenericAi() && super.canContinueToUse();
            }
        });
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return !CompanionEntity.this.kind.hasMasterAI() && !CompanionEntity.this.noGenericAi() && super.canUse();
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
        builder.define(DATA_WEAPON_MODE, 0);
        builder.define(DATA_LUNA_ROD, ItemStack.EMPTY);
        builder.define(DATA_OUTFIT, OutfitMode.DEFAULT.serializedName());
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_HOME, Optional.empty());
        builder.define(DATA_SCENE, "");
        builder.define(DATA_SCENE_PHASE, 0);
        builder.define(DATA_SCENE_PARTNER, Optional.empty());
        builder.define(DATA_APPROACH_PARTNER, Optional.empty());
        builder.define(DATA_FLYING, false);
        builder.define(DATA_PHASE_SERIAL, 0);
        builder.define(DATA_GALATH_BOOST, 0);
        builder.define(DATA_BOSS_STATE, 0);
        builder.define(DATA_BOSS_TARGET, -1);
        builder.define(DATA_BOSS_SUMMON, 0);
        builder.define(DATA_BOSS_FLAGS, 0);
        builder.define(DATA_FAMILY, Optional.empty());
        builder.define(DATA_MANG_RIDING, false);
        builder.define(DATA_MANG_TARGET, -1);
        builder.define(DATA_MANG_SHOT, -1L);
        builder.define(DATA_ALLIE_USES, 0);
        builder.define(DATA_BEE_TAMED, false);
        builder.define(DATA_BEE_CHEST, false);
        builder.define(DATA_SCARED, false);
        builder.define(DATA_PAYMENT_ITEM, 0);
        builder.define(DATA_SLIME_STATE, 0);
        builder.define(DATA_SLIME_HORNY, 0);
        builder.define(DATA_SLIME_BIRTH, -1);
        builder.define(DATA_TRIBE, Optional.empty());
        builder.define(DATA_TRIBE_NAME, "null");
        builder.define(DATA_KOBOLD_SIZE, 0.0F);
        builder.define(DATA_BODY_COLOR, "PURPLE");
        builder.define(DATA_EYE_COLOR, com.fartypoopy.sexmod.tribe.KoboldColor.values()[
                this.random.nextInt(com.fartypoopy.sexmod.tribe.KoboldColor.values().length)].name());
        builder.define(DATA_KOBOLD_NAME, com.fartypoopy.sexmod.tribe.KoboldBehavior.randomName(this.random));
        builder.define(DATA_KOBOLD_GENES, randomKoboldGenes(this.random));
        builder.define(DATA_K_LEADER, false);
        builder.define(DATA_K_FOLLOW, false);
        builder.define(DATA_K_ATTACKED, false);
        builder.define(DATA_K_CHOP, false);
        builder.define(DATA_K_STATE, 0);
        builder.define(DATA_FISH_STATE, 0);
        builder.define(DATA_FISH_CAST, false);
        builder.define(DATA_FISH_ITEM, ItemStack.EMPTY);
        builder.define(DATA_G_STATE, 0);
        builder.define(DATA_G_CARRIER, Optional.empty());
        builder.define(DATA_G_ITEM, ItemStack.EMPTY);
        builder.define(DATA_G_TAMED, false);
        builder.define(DATA_G_QUEEN, false);
        builder.define(DATA_G_MODEL, "");
        builder.define(DATA_MODEL_CODE, "");
        builder.define(DATA_FORM_OWNER, Optional.empty());
        builder.define(DATA_FORM_WAIT, 0);
        builder.define(DATA_FORM_WAIT_ANIMS, "");
        builder.define(DATA_FORM_BARE, false);
        builder.define(DATA_G_FADE, 1.0F);
        builder.define(DATA_G_PHASE, 0);
        builder.define(DATA_G_PREGGO, false);
    }

    /** Breeding scene phase mirrored onto the guards (0 intro, 1 slow, 2 fast, 3 finish). */
    public int goblinPhase() {
        return this.entityData.get(DATA_G_PHASE);
    }

    public void setGoblinPhase(int phase) {
        this.entityData.set(DATA_G_PHASE, phase);
    }

    public void setGoblinPreggo(boolean preggo) {
        this.entityData.set(DATA_G_PREGGO, preggo);
    }

    /** The next scene starts here instead of at the usual anchor (queen's breeding spot). */
    public void setForcedSceneAnchor(Vec3 position, float yaw) {
        this.forcedSceneAnchor = position;
        this.forcedSceneAnchorYaw = yaw;
    }

    /** Original e4 model string (ears, hair, colours, crown); empty until the server has rolled one. */
    public String goblinModel() {
        return this.entityData.get(DATA_G_MODEL);
    }

    /** Original ei.m(): the player this girl is the form of. */
    public Optional<UUID> formOwner() {
        return this.entityData.get(DATA_FORM_OWNER);
    }

    public boolean isPlayerForm() {
        return this.entityData.get(DATA_FORM_OWNER).isPresent();
    }

    /** Original ei(World, UUID): a form never thinks, collides, takes damage or despawns; it is moved onto its player. */
    public void setFormOwner(UUID owner) {
        this.entityData.set(DATA_FORM_OWNER, Optional.ofNullable(owner));
        if (owner != null) {
            this.setNoAi(true);
            this.setNoGravity(true);
            this.noPhysics = true;
            this.setInvulnerable(true);
            if (this.boss != null) {
                this.boss.bar().removeAllPlayers(); // the form never runs the boss tick that would hide it
                this.boss.bar().setVisible(false);
            }
        }
        this.refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_BOSS_STATE.equals(key) && this.kind == CompanionKind.GALATH) {
            double feet = this.getY();
            this.refreshDimensions(); // re-centres a grown box (+0.45 up), and paralysed she does not fall back
            if (this.bossState() == GalathBoss.S_KO_GROUND) {
                this.setPos(this.getX(), feet, this.getZ());
            }
        }
        if ((DATA_SCENE_PHASE.equals(key) || DATA_SCENE.equals(key)) && this.level().isClientSide) {
            this.flushKeyframeTail();
            this.flushPreviousPhaseTail();
        }
        if (DATA_ACTION.equals(key) && this.level().isClientSide && this.action() == CompanionAction.ATTACK) {
            this.attackFinished = false; // a new attack (the original sets ATTACK again once attackDone cleared it)
        }
        if (DATA_FORM_OWNER.equals(key)) {
            this.refreshDimensions();
            this.noPhysics = this.isPlayerForm();
        }
    }

    /** Server-side state of a player form (strip timer). */
    public int formStripTicks = -1;
    /** Server-side wait of a player form: where she waits, how long, what follows (Ellie: her choice). */
    public Vec3 formAnchor;
    /** Set by PlayerForms.startAction (original ei.b(UUID)) while the form's scene starts. */
    public boolean formDirectStart;
    public float formAnchorYaw;
    public int formWaitTicks;
    public int formApproachTicks;
    /** The player stepping up to a waiting form (Bia / Luna count down before the scene starts). */
    public UUID formApproacher;
    /** Not saved: false until the form has found her player after being loaded (original e5.as). */
    public boolean formOwnerSeen;
    /** Server-side ticks in the goblin form's current carry state. */
    public int formCarryTicks;
    public CompanionScene formFollowUp;

    public int formWait() {
        return this.entityData.get(DATA_FORM_WAIT);
    }

    public void setFormWait(int state) {
        this.entityData.set(DATA_FORM_WAIT, state);
    }

    public boolean formBare() {
        return this.entityData.get(DATA_FORM_BARE);
    }

    public void setFormBare(boolean bare) {
        this.entityData.set(DATA_FORM_BARE, bare);
    }

    public String formWaitAnims() {
        return this.entityData.get(DATA_FORM_WAIT_ANIMS);
    }

    public void setFormWaitAnims(String anims) {
        this.entityData.set(DATA_FORM_WAIT_ANIMS, anims == null ? "" : anims);
    }

    /**
     * Original ei.b(String, UUID) / b(UUID): a form's scene starts right away on her spot with the other player as
     * partner - no payment, no undressing first, no walk to a bed.
     */
    public boolean startFormScene(ServerPlayer partner, CompanionScene scene) {
        if (!this.isPlayerForm() || this.isInScene() || scene == null || scene.kind() != this.kind) {
            return false;
        }
        Vec3 at = this.formAnchor != null ? this.formAnchor : this.position();
        this.forcedSceneAnchor = at;
        this.forcedSceneAnchorYaw = this.formAnchor != null ? this.formAnchorYaw : this.getYRot();
        this.skipPayment = true;
        this.skipDelay = true;
        this.wildStart = true;
        try {
            return this.startScene(partner, scene);
        } finally {
            this.skipPayment = false;
            this.skipDelay = false;
            this.wildStart = false;
        }
    }

    /** Original em.C(). */
    public String modelCode() {
        return this.entityData.get(DATA_MODEL_CODE);
    }

    /** Original em.f(String); Galath and Manglelie keep theirs per owner (bj) instead of in their own save. */
    public void setModelCode(String code) {
        this.entityData.set(DATA_MODEL_CODE, code == null ? "" : code);
        if (this.usesStaticModelCode() && !this.isPlayerForm() && this.level().getServer() != null) {
            this.staticModelKey().ifPresent(key -> GalathBindingData.get(this.level().getServer())
                    .setModelCode(key, this.kind == CompanionKind.MANGLELIE, this.modelCode()));
        }
    }

    /** Original em.Y(). */
    public java.util.Set<String> wornModels() {
        return com.fartypoopy.sexmod.custommodel.CustomModels.parseCode(this.modelCode());
    }

    private boolean usesStaticModelCode() {
        return this.kind == CompanionKind.GALATH || this.kind == CompanionKind.MANGLELIE;
    }

    /**
     * Original bj.b: Galath's key is her owner (or herself when wild); a Manglelie's is her mother's owner (or herself).
     * Empty while a Manglelie's mother is not loaded yet.
     */
    private Optional<UUID> staticModelKey() {
        if (this.kind == CompanionKind.GALATH) {
            return Optional.of(this.masterId().orElse(this.getUUID()));
        }
        UUID mother = this.familyId();
        if (mother == null || !(this.level() instanceof ServerLevel serverLevel)) {
            return Optional.of(this.getUUID());
        }
        if (serverLevel.getEntity(mother) instanceof CompanionEntity galath) {
            return Optional.of(galath.masterId().orElse(this.getUUID()));
        }
        return this.tickCount > 100 ? Optional.of(this.getUUID()) : Optional.empty();
    }

    private boolean staticModelLoaded;

    /** Original f8.f() / f_ (bK): the stored model code is applied once, on the first ticks. */
    private void tickStaticModelCode(ServerLevel level) {
        if (this.staticModelLoaded || !this.usesStaticModelCode()) {
            return;
        }
        this.staticModelKey().ifPresent(key -> {
            this.staticModelLoaded = true;
            this.entityData.set(DATA_MODEL_CODE, GalathBindingData.get(level.getServer())
                    .modelCode(key, this.kind == CompanionKind.MANGLELIE));
        });
    }

    /** Original em.D(): how many values each per-girl appearance row has (101 = a 0..100 slider). Empty for most girls. */
    public List<Integer> girlSpecificBounds() {
        int colors = com.fartypoopy.sexmod.tribe.KoboldColor.values().length;
        return switch (this.kind) {
            case KOBOLD -> List.of(101, colors, colors, 8, 3, 101, 101, 3, 3, 4, 2);
            case GOBLIN -> java.util.Arrays.stream(GoblinModel.OPTIONS).boxed().toList();
            default -> List.of();
        };
    }

    /** Original em.h(UUID): the current per-girl values (Kobold: size, body and eye colour, then her genes). */
    public List<Integer> girlSpecificValues() {
        List<Integer> values = new java.util.ArrayList<>();
        if (this.kind == CompanionKind.KOBOLD) {
            values.add(Math.round(this.koboldSize() * 100.0F / 0.25F));
            values.add(this.bodyColor().ordinal());
            values.add(this.eyeColor().ordinal());
            for (int i = 0; i < 8; i++) {
                values.add(this.koboldGene(i));
            }
        } else if (this.kind == CompanionKind.GOBLIN) {
            String[] fields = this.goblinModel().split("-");
            for (int i = 0; i < GoblinModel.OPTIONS.length; i++) {
                int value = 0;
                if (i < fields.length) {
                    try {
                        value = Integer.parseInt(fields[i].trim());
                    } catch (NumberFormatException ignored) {
                        // keep 0
                    }
                }
                values.add(value);
            }
        }
        return values;
    }

    /** Original fw.a: every value must stay below its bound (negative values are refused too). */
    public boolean girlSpecificValid(List<Integer> values) {
        List<Integer> bounds = this.girlSpecificBounds();
        if (values.size() < bounds.size()) {
            return false;
        }
        for (int i = 0; i < bounds.size(); i++) {
            if (values.get(i) < 0 || values.get(i) >= bounds.get(i)) {
                return false;
            }
        }
        return true;
    }

    /** Original ff.a(List) / e3.a(List): write the per-girl values back (a goblin keeps her crown field). */
    public void applyGirlSpecific(List<Integer> values) {
        if (this.kind == CompanionKind.KOBOLD && values.size() >= 11) {
            com.fartypoopy.sexmod.tribe.KoboldColor[] colors = com.fartypoopy.sexmod.tribe.KoboldColor.values();
            this.setKoboldSize(values.get(0) / 100.0F * 0.25F);
            this.setBodyColor(colors[values.get(1)]);
            this.setEyeColor(colors[values.get(2)]);
            StringBuilder genes = new StringBuilder();
            for (int i = 3; i < 11; i++) {
                genes.append(values.get(i)).append(i < 10 ? "-" : "");
            }
            this.entityData.set(DATA_KOBOLD_GENES, genes.toString());
        } else if (this.kind == CompanionKind.GOBLIN && values.size() >= GoblinModel.OPTIONS.length) {
            StringBuilder model = new StringBuilder();
            for (int i = 0; i < GoblinModel.OPTIONS.length; i++) {
                int value = values.get(i);
                model.append(value < 10 ? "0" : "").append(value).append('-');
            }
            String[] old = this.goblinModel().split("-");
            int crown = 0;
            if (old.length > 9) {
                try {
                    crown = Integer.parseInt(old[9].trim());
                } catch (NumberFormatException ignored) {
                    // no crown
                }
            }
            model.append(crown < 10 ? "0" : "").append(crown).append('-');
            this.setGoblinModel(model.toString());
        }
    }

    public void setGoblinModel(String model) {
        this.entityData.set(DATA_G_MODEL, model);
    }

    public float goblinFade() {
        return this.entityData.get(DATA_G_FADE);
    }

    public void setGoblinFade(float fade) {
        this.entityData.set(DATA_G_FADE, fade);
    }

    public GalathBoss boss() {
        return this.boss;
    }

    public int bossState() {
        return this.entityData.get(DATA_BOSS_STATE);
    }

    public void setBossState(int state) {
        this.entityData.set(DATA_BOSS_STATE, state);
    }

    public int bossTargetId() {
        return this.entityData.get(DATA_BOSS_TARGET);
    }

    public void setBossTarget(int id) {
        this.entityData.set(DATA_BOSS_TARGET, id);
    }

    public int summonTick() {
        return this.entityData.get(DATA_BOSS_SUMMON);
    }

    public void setSummonTick(int tick) {
        this.entityData.set(DATA_BOSS_SUMMON, tick);
    }

    public boolean bossFlag(int bit) {
        return (this.entityData.get(DATA_BOSS_FLAGS) & bit) != 0;
    }

    public void setBossFlag(int bit, boolean on) {
        int flags = this.entityData.get(DATA_BOSS_FLAGS);
        this.entityData.set(DATA_BOSS_FLAGS, on ? flags | bit : flags & ~bit);
    }

    public void setBossFlags(int flags) {
        this.entityData.set(DATA_BOSS_FLAGS, flags);
    }

    /** Original em.c(uuid) != null: the player is a scene partner of some companion nearby. */
    public static boolean playerInScene(Player player) {
        return !player.level().getEntitiesOfClass(CompanionEntity.class, player.getBoundingBox().inflate(64.0D),
                c -> c.isInScene() && c.scenePartnerId().filter(player.getUUID()::equals).isPresent()).isEmpty();
    }

    @Override
    public boolean isMultipartEntity() {
        return this.boss != null;
    }

    @Override
    public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
        return this.boss == null ? super.getParts() : this.boss.parts();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (this.boss != null && !this.isPlayerForm()) { // a player's Galath form is no boss: no health bar
            this.boss.bar().addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (this.boss != null) {
            this.boss.bar().removePlayer(player);
        }
    }

    public ManglelieBehavior manglelie() {
        return this.manglelieBehavior;
    }

    /** Manglelie: her mother. Galath: her daughter. */
    public UUID familyId() {
        return this.entityData.get(DATA_FAMILY).orElse(null);
    }

    public void setFamilyId(UUID id) {
        this.entityData.set(DATA_FAMILY, Optional.ofNullable(id));
    }

    public boolean mangRiding() {
        return this.entityData.get(DATA_MANG_RIDING);
    }

    public void setMangRiding(boolean riding) {
        this.entityData.set(DATA_MANG_RIDING, riding);
    }

    public int mangTargetId() {
        return this.entityData.get(DATA_MANG_TARGET);
    }

    public long mangShotStart() {
        return this.entityData.get(DATA_MANG_SHOT);
    }

    public void setMangTarget(int id, long startTime) {
        this.entityData.set(DATA_MANG_TARGET, id);
        this.entityData.set(DATA_MANG_SHOT, startTime);
    }

    public Entity mangTarget() {
        int id = this.mangTargetId();
        return id == -1 ? null : this.level().getEntity(id);
    }

    public GoblinBehavior goblin() {
        return this.goblinBehavior;
    }

    public int goblinState() {
        return this.entityData.get(DATA_G_STATE);
    }

    public void setGoblinState(int state) {
        this.entityData.set(DATA_G_STATE, state);
    }

    public Optional<UUID> goblinCarrier() {
        return this.entityData.get(DATA_G_CARRIER);
    }

    public void setGoblinCarrier(UUID carrier) {
        this.entityData.set(DATA_G_CARRIER, Optional.ofNullable(carrier));
    }

    public ItemStack goblinItem() {
        return this.entityData.get(DATA_G_ITEM);
    }

    public void setGoblinItem(ItemStack stack) {
        this.entityData.set(DATA_G_ITEM, stack.copy());
    }

    public boolean goblinTamed() {
        return this.entityData.get(DATA_G_TAMED);
    }

    public void setGoblinTamed(boolean tamed) {
        this.entityData.set(DATA_G_TAMED, tamed);
    }

    public boolean goblinQueen() {
        return this.entityData.get(DATA_G_QUEEN);
    }

    public void setGoblinQueen(boolean queen) {
        this.entityData.set(DATA_G_QUEEN, queen);
        if (this.goblinBehavior != null) {
            this.goblinBehavior.queen = queen;
        }
    }

    /** Original e3.ap(): pregnant queen (breeding is not ported yet). */
    public boolean isPreggo() {
        return this.entityData.get(DATA_G_PREGGO);
    }

    /** The goblin is held by a player (catch, pick-up, shoulder, throw). */
    public boolean goblinHeld() {
        if (this.goblinBehavior == null) {
            return false;
        }
        int st = this.goblinState();
        return st == GoblinBehavior.CATCH || st == GoblinBehavior.CATCH_BJ || st == GoblinBehavior.CATCH_BJ_IDLE
                || st == GoblinBehavior.PICK_UP || st == GoblinBehavior.SHOULDER || st == GoblinBehavior.THROWING;
    }

    public int fishState() {
        return this.entityData.get(DATA_FISH_STATE);
    }

    public void setFishState(int state) {
        this.entityData.set(DATA_FISH_STATE, state);
    }

    /** Original af: the rod is cast (drives the rod's "cast" model). */
    public boolean fishCast() {
        return this.entityData.get(DATA_FISH_CAST);
    }

    public void setFishCast(boolean cast) {
        this.entityData.set(DATA_FISH_CAST, cast);
    }

    /** Client only: how far the hook and its catch have flown back to Luna, 0 to 1 (original eb.Z). */
    public float hookReel;

    /** Original ag: the item on the hook. */
    public ItemStack fishCaught() {
        return this.entityData.get(DATA_FISH_ITEM);
    }

    public void setFishCaught(ItemStack stack) {
        this.entityData.set(DATA_FISH_ITEM, stack.copy());
    }

    private boolean fishingHoldsHer() {
        return this.lunaFishing != null && this.lunaFishing.isActive();
    }

    public LunaFishing lunaFishing() {
        return this.lunaFishing;
    }

    /** Original ae() != null and friends: a scene, a queued scene or a walk to a bed stops the fishing routine. */
    public boolean isFishingBlocked() {
        return this.isInScene() || this.queuedScene != null || this.chainedScene != null || this.delayedScene != null
                || this.approachScene != null
                || this.bedWalk != null || this.isDowned();
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

    /** Client only: game time at which Allie's fade-out started, or -1 (original ev.U). */
    public long allieFadeStart = -1L;
    /** Client only (original f_.ap / bu): Galath's drawn sword and the dragon-breath trail along its blade. */
    public boolean galathSword;
    public boolean galathBlade;
    /** Client only: the "setNude" keyframe's burst, spawned by the renderer where the clothing bones are. */
    public boolean galathNudeBurst;
    /** Client only (original f_.U): set by the setCamCorrupt keyframe, cleared by enableBoyCam. */
    public boolean galathCorruptCam;
    public long galathBladeTick = -1L;
    /** Client only: original ff.Q, the Kobold's egg bone is shown from the renderEgg keyframe until the scene ends. */
    public boolean koboldEggShown;
    /** Original da.s: the knocked-out Galath's last flight direction (body yaw). */
    public float galathKnockYaw;
    /** Original e2.S, counted on the client: attack animation {@code "attack" + S}, 1, 2, 0, 1, ... one per attackDone. */
    public int attackCycle = 1;
    /** Original attackDone: her attack is over on this client until the next swing or a fresh ATTACK. */
    public boolean attackFinished;
    /** Original eb.Z / eb.aa: Luna's caught item shows from renderItem until eatingDone / throw_away, a third smaller per bite. */
    public boolean lunaItemShown;
    public float lunaItemScale = 1.0F;
    /** Original em.n: the payment items' scale; Luna's eatPay bites shrink it, paymentDone resets it. */
    public float paymentScale = 1.0F;
    /** Original d_.a: how far the drawn bow is pulled, +0.015 per rendered frame while she aims. */
    public float bowDraw;
    /** Client, during her item render only: the bow the vanilla pull / pulling properties should see as in use. */
    public ItemStack renderUseItem;
    public int renderUseRemaining;

    /** Original attackDone: S++ (back to 0 at 3) and her action ends for this client. */
    public void attackDone() {
        if (++this.attackCycle == 3) {
            this.attackCycle = 0;
        }
        this.attackFinished = true;
    }

    public ItemStack lunaStoredRod() {
        return this.entityData.get(DATA_LUNA_ROD);
    }

    public int weaponMode() {
        return this.entityData.get(DATA_WEAPON_MODE);
    }

    void setWeaponMode(int mode) {
        this.entityData.set(DATA_WEAPON_MODE, mode);
    }

    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
        if (this.level().isClientSide) {
            this.attackFinished = false; // the next hit starts the next attack animation
        }
        super.swing(hand, updateSelf);
    }

    @Override
    public ItemStack getUseItem() {
        return this.renderUseItem != null ? this.renderUseItem : super.getUseItem();
    }

    @Override
    public boolean isUsingItem() {
        return this.renderUseItem != null || super.isUsingItem();
    }

    @Override
    public int getUseItemRemainingTicks() {
        return this.renderUseItem != null ? this.renderUseRemaining : super.getUseItemRemainingTicks();
    }

    /** Original d_.a(): the payment she holds is the price of the queued action (Luna's fish: cod / salmon). */
    public ItemStack paymentItem() {
        CompanionScene paid = switch (this.entityData.get(DATA_PAYMENT_ITEM)) {
            case 1 -> CompanionScene.JENNY_DOGGY;
            case 2 -> CompanionScene.JENNY_BLOWJOB;
            case 3 -> CompanionScene.JENNY_STRIP;
            case 4 -> CompanionScene.JENNY_BOOBJOB;
            case 5 -> CompanionScene.LUNA_TOUCH;
            case 6 -> CompanionScene.LUNA_SITTING;
            case 7 -> CompanionScene.KOBOLD_BLOWJOB;
            case 8 -> CompanionScene.KOBOLD_ANAL;
            default -> null;
        };
        return paid == null ? ItemStack.EMPTY : CompanionActions.costOf(paid);
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
        if (this.boss != null && !this.hasMaster() && !this.technicalProxy) {
            return this.boss.adjustLethal(amount); // wild Galath: her health never reaches zero
        }
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
        if (this.kind == CompanionKind.BEE && this.hasMaster() && !this.technicalProxy) {
            // original f.a: a following Bee's death is cancelled
            return Math.max(0.0F, Math.min(amount, this.getHealth() - 1.0F));
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

    private GalathFollowAi galathFollow;
    private int galathDespawnTicks = -1;
    private long galathHurtSoundAt;

    /** Original cc.a(f_): the coin calls her back; she plays the de-summon animation in place and is removed afterwards. */
    public void beginGalathDeSummon(int ticks) {
        if (this.galathDespawnTicks < 0) {
            this.galathDespawnTicks = ticks;
        }
    }

    private void tickGalathCoinStates(ServerLevel serverLevel) {
        if (this.galathDespawnTicks >= 0) {
            this.setAction(CompanionAction.GALATH_DESUMMON);
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
            this.setNoGravity(true);
            return;
        }
        if (this.ambushTicks >= 0) {
            this.tickGalathAmbush(serverLevel);
            return;
        }
        if (this.summonedByGalathCoin && this.tickCount <= 15 && !this.isInScene()) {
            // original GALATH_SUMMON: she materialises for 15 ticks before she acts
            this.setAction(CompanionAction.GALATH_SUMMON);
            this.getNavigation().stop();
            this.setDeltaMovement(Vec3.ZERO);
        } else if (this.action() == CompanionAction.GALATH_SUMMON) {
            this.setAction(CompanionAction.NONE);
        }
        // original f_.ae() (every AI tick): she is removed when her owner is in another dimension or more than 60 blocks
        // away
        if (this.summonedByGalathCoin) {
            this.masterId().map(id -> serverLevel.getServer().getPlayerList().getPlayer(id)).ifPresent(owner -> {
                if (owner.level() != serverLevel || owner.distanceToSqr(this) > 3600.0D) {
                    this.discard();
                }
            });
        }
    }

    private int ambushTicks = -1;

    /**
     * Original coin summon, 10 % of the time: she is masturbating with her back to you and after 8 s ambushes you with
     * the cowgirl scene instead of the summon animation.
     */
    public void beginGalathAmbush(Player owner) {
        this.ambushTicks = 160;
        this.setAction(CompanionAction.GALATH_MASTURBATE);
        this.getNavigation().stop();
        this.setYRot((float) (180.0D - Math.toDegrees(Mth.atan2(owner.getX() - this.getX(), owner.getZ() - this.getZ()))));
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
    }

    private void tickGalathAmbush(ServerLevel serverLevel) {
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        if (--this.ambushTicks > 0) {
            return;
        }
        this.ambushTicks = -1;
        this.setAction(CompanionAction.NONE);
        ServerPlayer owner = this.masterId().map(id -> serverLevel.getServer().getPlayerList().getPlayer(id)).orElse(null);
        if (owner != null && owner.isAlive() && owner.level() == serverLevel && !this.isInScene()) {
            this.setForcedSceneAnchor(owner.position(), Mth.wrapDegrees(owner.getYRot() + 180.0F));
            this.startWildScene(owner, CompanionScene.GALATH_COWGIRL);
        }
    }

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
                this.galathBoostSpeed = 6.0D; // original f_.aw(): al = 6.0, motion = look * al
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

    /** Original f8.attackEntityFrom: while she has a mother, all damage (except the void and /kill) goes to the mother. */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (this.kind == CompanionKind.MANGLELIE && this.level() instanceof ServerLevel serverLevel
                && !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)) {
            CompanionEntity mother = ManglelieBehavior.find(serverLevel, this.familyId());
            if (mother != null) {
                mother.hurt(source, amount);
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
        // 1.12's /kill dealt void damage, so the original's "all but the void" rules let /kill through as well
        boolean voidOrKill = source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                || source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
        if (this.isPlayerForm() && !voidOrKill) {
            return true; // the player takes the damage, not her form
        }
        if (this.goblinHeld() && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
            return true; // a carried goblin takes no damage except /kill
        }
        if (this.kind == CompanionKind.GALATH
                && (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING)
                || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING))) {
            return true; // original: fire, wall, drowning, fall and cramming do nothing to her
        }
        if (this.isInScene() && !voidOrKill) {
            return true; // original ah.b: a girl in a scene takes no damage except the void
        }
        if (this.approachScene != null && source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            return true; // original dc: she glides to the player with noClip, through walls, without suffocating
        }
        return super.isInvulnerableTo(source);
    }

    /** Original gu: the e2 girls' armour is not vanilla equipment, so vanilla armour never reduces their damage. */
    @Override
    protected float getDamageAfterArmorAbsorb(net.minecraft.world.damagesource.DamageSource source, float amount) {
        return GirlArmour.applies(this) ? amount : super.getDamageAfterArmorAbsorb(source, amount);
    }

    /** Only the resistance effect applies to them; their protection enchantments are handled by {@link GirlArmour}. */
    @Override
    protected float getDamageAfterMagicAbsorb(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!GirlArmour.applies(this)) {
            return super.getDamageAfterMagicAbsorb(source, amount);
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_EFFECTS)) {
            return amount;
        }
        if (this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_RESISTANCE)) {
            int resistance = (this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
            amount = Math.max(amount * (25 - resistance) / 25.0F, 0.0F);
        }
        return amount;
    }

    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
        // original f_ / f8.func_70690_d (empty): neither Galath nor Manglelie takes potion effects
        return this.kind != CompanionKind.GALATH && this.kind != CompanionKind.MANGLELIE && super.canBeAffected(effect);
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

    /**
     * Original d_ leash: 1.12's renderLeash ties the lead (24 / 18 + 0.125) above y - (1.6 - height) / 2, and fk shifts
     * that y per girl; 0.4 x width in front of her, as in 1.21.
     */
    @Override
    protected Vec3 getLeashOffset() {
        double shift = switch (this.kind) {
            case JENNY -> -0.15D;
            case ELLIE -> 0.05D;
            case SLIME -> -0.2D;
            case GOBLIN -> -0.6D;
            case GALATH, MANGLELIE -> -0.05D;
            default -> -0.4D; // Kobold, Bia, Allie, Bee, Luna
        };
        return new Vec3(0.0D, 24.0D / 18.0D + 0.125D - (1.6D - this.getBbHeight()) * 0.5D + shift, this.getBbWidth() * 0.4F);
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

    public com.fartypoopy.sexmod.tribe.KoboldBehavior koboldBehavior() {
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

    public com.fartypoopy.sexmod.tribe.KoboldColor bodyColor() {
        return com.fartypoopy.sexmod.tribe.KoboldColor.byName(this.entityData.get(DATA_BODY_COLOR));
    }

    public void setBodyColor(com.fartypoopy.sexmod.tribe.KoboldColor color) {
        this.entityData.set(DATA_BODY_COLOR, color.name());
    }

    public com.fartypoopy.sexmod.tribe.KoboldColor eyeColor() {
        return com.fartypoopy.sexmod.tribe.KoboldColor.byName(this.entityData.get(DATA_EYE_COLOR));
    }

    public void setEyeColor(com.fartypoopy.sexmod.tribe.KoboldColor color) {
        this.entityData.set(DATA_EYE_COLOR, color.name());
    }

    public String koboldName() {
        return this.entityData.get(DATA_KOBOLD_NAME);
    }

    public void setKoboldName(String name) {
        this.entityData.set(DATA_KOBOLD_NAME, name);
    }

    /** Original ff.a(StringBuilder): up horn 0-7, down horn 0-2, two bell-shaped 00-99 values, freckles 0-2 twice, 0-1 twice. */
    private static String randomKoboldGenes(net.minecraft.util.RandomSource random) {
        StringBuilder out = new StringBuilder();
        out.append(random.nextInt(8)).append('-').append(random.nextInt(3)).append('-');
        for (int i = 0; i < 2; i++) {
            double x = -2.5D + 5.0D * random.nextDouble();
            String formatted = String.format(java.util.Locale.ROOT, "%.2f", Math.exp(-(x * x)));
            out.append(formatted.substring(formatted.indexOf('.') + 1)).append('-');
        }
        out.append(random.nextInt(3)).append('-').append(random.nextInt(3)).append('-')
                .append(random.nextInt(2)).append('-').append(random.nextInt(2));
        return out.toString();
    }

    /**
     * Field i of the Kobold model string: 0 up horn, 1 down horn, 2 breast size, 3 eye size, 4 and 5 freckles (arms side
     * and head side), 6 backpack / tailpack (0 backpack, 1 both, 2 tailpack, 3 neither), 7 mouth offset.
     */
    public int koboldGene(int index) {
        String[] fields = this.entityData.get(DATA_KOBOLD_GENES).split("-");
        if (index < 0 || index >= fields.length) {
            return 0;
        }
        try {
            return Integer.parseInt(fields[index].trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
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
        if (this.isMaster(player) && held.is(Items.NAME_TAG)) { // original ff: any name tag, before the attack and sleep checks
            if (!client) {
                this.setKoboldName(held.getHoverName().getString());
                held.shrink(1);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (this.koboldUnderAttack() || this.koboldState() == com.fartypoopy.sexmod.tribe.KoboldBehavior.ST_SLEEP) {
            return InteractionResult.PASS;
        }
        if (com.fartypoopy.sexmod.tribe.KoboldBehavior.holdsStaff(player)) {
            if (!this.hasMaster()) {
                if (client && this.tribeId().isPresent() && !com.fartypoopy.sexmod.client.TribeClient.hasTribe()) {
                    com.fartypoopy.sexmod.client.ClientHooks.openNameTribe(this.tribeId().get());
                }
                return InteractionResult.sidedSuccess(client);
            }
            if (this.isMaster(player)) {
                if (!client && player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(
                            new SimpleMenuProvider(
                                    (containerId, inventory, ignored) -> new net.minecraft.world.inventory.ChestMenu(
                                            com.fartypoopy.sexmod.registry.ModMenus.COMPANION_STORAGE.get(),
                                            containerId, inventory, this.storage, 3),
                                    this.storageTitle()),
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

    /** Original e3 goals: swim, door, look-at-player only; wandering, running and idling are scripted in GoblinBehavior. */
    private void configureGoblin() {
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(5, new IdleLookGoal());
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

    public int scenePhaseTicks() {
        return this.scenePhaseTicks;
    }

    public int currentScenePhase() {
        return this.entityData.get(DATA_SCENE_PHASE);
    }

    public Optional<UUID> scenePartnerId() {
        return this.entityData.get(DATA_SCENE_PARTNER);
    }

    /** Client menu closed: a frozen Kobold walks on again. */
    public void endKoboldInteraction(Player player) {
        if (this.koboldBehavior != null) {
            this.koboldBehavior.endInteract(player.getUUID());
        }
        // original e0.ac: Bia's talk menu closed without a choice resets the scene
        if (this.kind == CompanionKind.BIA && this.currentScene() == CompanionScene.BIA_TALK && this.currentScenePhase() == 1
                && this.scenePartnerId().filter(player.getUUID()::equals).isPresent()) {
            this.finishScene(false);
        }
    }

    /** Goblin breeding jump: the player is the partner for the camera and wipe keyframes before the scene object exists. */
    public void setJumpPartner(UUID player) {
        this.entityData.set(DATA_SCENE_PARTNER, Optional.ofNullable(player));
    }

    public boolean isInScene() {
        return this.currentScene() != null;
    }

    /**
     * Original f8.a(fp,...) + f_.ak()/a(): Galath's threesome state follows her daughter's. Slow -> fast (double_holding_soft)
     * turns her from licking to sitting (pussy_licking_back, then masterbating_sitting); fast -> slow (double_holding_back)
     * back to licking (pussy_licking_forward, then pussy_licking); the daughter's finish plays masterbating_sitting_cum.
     */
    private software.bernie.geckolib.animation.RawAnimation threesomeAnimation() {
        var raw = software.bernie.geckolib.animation.RawAnimation.begin();
        UUID daughterId = this.familyId();
        CompanionEntity daughter = null;
        if (daughterId != null) {
            for (CompanionEntity candidate : this.level().getEntitiesOfClass(CompanionEntity.class, this.getBoundingBox().inflate(4.0D),
                    c -> daughterId.equals(c.getUUID()))) {
                daughter = candidate;
            }
        }
        CompanionScene.Phase phase = daughter == null ? null : daughter.currentSceneAnimation();
        String name = phase == null ? "" : phase.animation();
        return switch (name) {
            case "animation.shared.double_holding_soft" -> raw.thenPlay("animation.galath.pussy_licking_back")
                    .thenLoop("animation.galath.masterbating_sitting");
            case "animation.shared.double_holding_hard" -> raw.thenLoop("animation.galath.masterbating_sitting");
            case "animation.shared.double_holding_back" -> raw.thenPlay("animation.galath.pussy_licking_forward")
                    .thenLoop("animation.galath.pussy_licking");
            case "animation.shared.double_holding_cum" -> raw.thenPlayAndHold("animation.galath.masterbating_sitting_cum");
            default -> raw.thenLoop("animation.galath.pussy_licking");
        };
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
        if (this.kind == CompanionKind.ELLIE || this.kind == CompanionKind.LUNA) {
            return true; // original el / eb: no master check, never a busy line
        }
        return !this.hasMaster() || this.isMaster(player);
    }

    /** Kobolds sell their scene menu to anyone. */
    public boolean mayUseSceneActions(Player player) {
        return this.kind.opensMenuToStrangers() || this.canUsePanel(player)
                || (this.wildBoss() && this.currentScene() == CompanionScene.GALATH_WILD_CORRUPT)
                || (this.kind == CompanionKind.MANGLELIE && this.currentScene() == CompanionScene.MANGLELIE_THREESOME);
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

    /** The pearl of the running go-home throw (original em.q). */
    private ThrownEnderpearl goHomePearl;

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
            this.goHomePearl = pearl;
        } else if (this.goHomePearl != null && this.goHomePearl.isRemoved()) {
            // original ho.onImpact: landing within 5 blocks of home takes her to the landing spot at once, and the
            // EnderTeleportEvent handler ends the routine (her last-keyframe teleport home then never comes)
            Vec3 landed = this.goHomePearl.position();
            this.goHomePearl = null;
            if (landed.distanceTo(Vec3.atLowerCornerOf(home)) < 5.0D) {
                this.stopRiding();
                this.pinned = false;
                this.goHomeTicks = -1;
                this.teleportTo(landed.x, landed.y, landed.z);
                this.fallDistance = 0.0F;
                this.setAction(CompanionAction.NONE);
                this.sayStopLine();
            }
        } else if (this.goHomeTicks >= (bee ? 34 : 58)) {
            for (int burst = 0; burst < 32; burst++) {
                // original gg: 32 calls of 32 portal particles, random height 0..2, speed = gaussian
                serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + this.random.nextDouble() * 2.0D,
                        this.getZ(), 32, 0.2D, 0.2D, 0.2D, this.random.nextGaussian());
            }
            this.pinned = false;
            this.goHomeTicks = -1;
            this.goHomePearl = null;
            this.teleportTo(target.x, target.y, target.z);
            this.fallDistance = 0.0F;
            this.setAction(CompanionAction.NONE);
            this.clearMaster();
            this.sayStopLine(); // original x() also runs when the go-home routine ends
        }
    }

    public void markSummonedByGalathCoin() {
        this.summonedByGalathCoin = true;
    }

    public boolean openControlMenu(ServerPlayer player) {
        if (this.action() == CompanionAction.GALATH_MASTURBATE || !this.canUsePanel(player) || player.distanceToSqr(this) > 64.0D) {
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
                    com.fartypoopy.sexmod.registry.ModSounds.find("girls.jenny.sadoh.sadoh1").ifPresent(sound ->
                            player.level().playSound(null, player.blockPosition(), sound.get(),
                                    net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F));
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
                                com.fartypoopy.sexmod.registry.ModMenus.COMPANION_STORAGE.get(),
                                containerId, inventory, this.storage, 3),
                        this.storageTitle()
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

    /** Original cr.f: STARTDOGGY and WAITDOGGY show the bed slime too, not only the doggy scene. */
    public boolean slimeOnBed() {
        int state = this.slimeState();
        return state == SlimeBehavior.GO_ON_BED || state == SlimeBehavior.WAIT;
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
                new com.fartypoopy.sexmod.network.OpenSceneMenuPayload(
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
                && this.currentScenePhase() <= 1 // the menu opens at hugDone, 4 ticks before HUGIDLE
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
        this.queuedScene = chosen;
        this.enterScenePhase(2);
        return true;
    }

    /** Original em.c(): the type name ("Jenny", "Bee", ...), for a Kobold her own name. Ignores name tags. */
    public String originalName() {
        if (this.kind == CompanionKind.KOBOLD) {
            return this.koboldName();
        }
        String name = this.kind.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Original ek: the chest is titled em.c(). */
    private Component storageTitle() {
        return Component.literal(this.originalName());
    }

    // ---------------------------------------------------------------- wild Galath scenes

    /** Original f_.c(boolean): no drain when it would leave 1 HP or less, never on creative players; the heal is optional. */
    private void drainPlayer(ServerPlayer player, boolean heal) {
        if (0.0F >= player.getHealth() - 1.0F || player.isCreative()) {
            return;
        }
        player.hurt(GalathBoss.succubusSource(this), 1.0F);
        if (heal) {
            this.setHealth(Math.min(this.getMaxHealth(), this.getHealth() + 1.5F));
        }
    }

    /** Original h8 rape: three drains in the intro, then one per 16 tick loop until one more would kill, then the finish. */
    private void tickWildScene(ServerLevel level, CompanionScene scene, ServerPlayer player, CompanionScene.Phase phase) {
        int index = this.currentScenePhase();
        int ticks = this.scenePhaseTicks;
        switch (index) {
            case 0 -> {
                if (ticks == 3 || ticks == 19 || ticks == 38) {
                    this.drainPlayer(player, ticks != 3); // rapeHurt (no heal), then poundRape twice
                }
                if (ticks >= phase.durationTicks()) {
                    this.enterScenePhase(1); // rapeIntroDone: rape{b1}, the loop she was last on
                }
            }
            case 1, 2, 3 -> {
                if (ticks == 7) {
                    this.drainPlayer(player, true);
                }
                if (ticks >= phase.durationTicks()) {
                    // rape_switch: b1 = a different one of the three, then RAPE_CUM once the player is at 1 health
                    int previous = this.galathRapeVariant;
                    do {
                        this.galathRapeVariant = this.random.nextInt(3);
                    } while (this.galathRapeVariant == previous);
                    this.enterScenePhase(player.getHealth() - 1.0F <= 0.0F ? 4 : 1 + this.galathRapeVariant);
                }
            }
            case 4 -> {
                if (ticks == 15) {
                    player.hurt(GalathBoss.succubusSource(this), 2.14748365E9F);
                }
                if (ticks >= phase.durationTicks()) {
                    // original o(): after 28 cum ticks she lets go and the player (if still alive) is lifted clear
                    this.finishScene(true);
                    if (player.isAlive()) {
                        player.teleportTo(player.getX(), Math.ceil(player.getY()) + 1.0D, player.getZ());
                    }
                }
            }
            default -> this.finishScene(true);
        }
    }

    /** Original cd packet: the player mashed free, she lets go and flies off. */
    public void galathEscape(ServerPlayer player) {
        if (this.currentScene() != CompanionScene.GALATH_WILD_RAPE
                || !this.scenePartnerId().filter(player.getUUID()::equals).isPresent()
                || this.currentScenePhase() > 3) {
            return;
        }
        this.finishScene(false);
        if (this.boss != null && this.level() instanceof ServerLevel level) {
            this.setNoGravity(true);
            this.boss.flyAway(level);
        }
    }

    /** Original: the finished corruption leaves the winner holding her coin, and she is bound to it. */
    private void awardGalathCoin(ServerPlayer player) {
        ItemStack coin = new ItemStack(com.fartypoopy.sexmod.registry.ModItems.GALATH_COIN.get());
        ItemStack old = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, coin);
        if (!old.isEmpty() && !player.getInventory().add(old)) {
            player.drop(old, false);
        }
        player.sendSystemMessage(Component.translatable("message.sexmod.galath_coin_lore")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private boolean tryWildCorrupt(Player player) {
        return this.wildBoss()
                && player instanceof ServerPlayer serverPlayer
                && this.boss.knocked()
                && this.bossState() == GalathBoss.S_KO_GROUND
                && !this.isInScene()
                && player.distanceToSqr(this) <= 36.0D
                && this.startWildScene(serverPlayer, CompanionScene.GALATH_WILD_CORRUPT);
    }

    /** The Manglelie this Galath carries (server side only), or null. */
    public CompanionEntity daughter() {
        if (this.kind != CompanionKind.GALATH || this.familyId() == null || !(this.level() instanceof ServerLevel level)) {
            return null;
        }
        CompanionEntity found = ManglelieBehavior.find(level, this.familyId());
        return found != null && found.mangRiding() ? found : null;
    }

    private boolean wildStart;

    /** Scenes the wild Galath starts herself (catch or corruption): no ownership, distance or room checks. */
    public boolean startWildScene(ServerPlayer player, CompanionScene scene) {
        this.wildStart = true;
        try {
            boolean started = this.startScene(player, scene);
            if (started) {
                this.wildTickCounter = 0;
                if (scene == CompanionScene.GALATH_WILD_RAPE) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            player, new com.fartypoopy.sexmod.network.GalathEscapePayload(this.getId()));
                    // original h8 RAPE: a look packet turns the player to her yaw + 180 (as a byte angle), pitch byte -14
                    float yaw = Mth.floor((this.getYRot() + 180.0F) * 256.0F / 360.0F) * 360.0F / 256.0F;
                    player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), java.util.Set.of(),
                            Mth.wrapDegrees(yaw), -14.0F * 360.0F / 256.0F);
                }
            }
            return started;
        } finally {
            this.wildStart = false;
        }
    }

    private int wildTickCounter;

    public boolean startScene(Player player, CompanionScene scene) {
        this.pendingFinish = false;
        if (scene == CompanionScene.GOBLIN_USE_HER || scene == CompanionScene.GOBLIN_TAKE_BACK) {
            if (this.goblinBehavior == null) {
                return false;
            }
            this.goblinBehavior.choose(player, scene == CompanionScene.GOBLIN_TAKE_BACK ? 0 : 1);
            return true;
        }
        if (scene == CompanionScene.GALATH_RIDE) {
            return this.kind == CompanionKind.GALATH
                    && !this.isInScene()
                    && this.isMaster(player)
                    && player.distanceToSqr(this) <= 64.0D
                    && this.startRide(player);
        }
        if (scene == CompanionScene.GALATH_THREESOME && this.daughter() == null) {
            return false;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)
                || scene == null
                || scene.kind() != this.kind
                || this.isInScene()
                || (!this.wildStart
                && (!this.mayUseSceneActions(player)
                || (scene != CompanionScene.GOBLIN_BREEDING && player.distanceToSqr(this) > (scene.isWaitScene() ? 1024.0D : 64.0D))))) {
            return false;
        }
        if (this.approachScene != null) {
            return false; // already on her way to the player
        }
        if (!this.approachDone && !this.chainStart && !this.chainedNoApproach && !this.isPlayerForm() && this.forcedSceneAnchor == null
                && this.bedSpotPosition == null && this.approachesPlayer(scene)) {
            // original dc / bh.b(): she glides over 40 ticks to 1 block in front of the player, who steps 0.35 forward
            // and is held; then she turns to his yaw + 180 and the action begins (U(), or Jenny's PAYMENT)
            double yaw = Math.toRadians(player.getYRot());
            Vec3 step = player.position().add(-Math.sin(yaw) * 0.35D, 0.0D, Math.cos(yaw) * 0.35D);
            player.teleportTo(step.x, step.y, step.z);
            player.setDeltaMovement(Vec3.ZERO);
            this.approachScene = scene;
            this.approachPlayer = player.getUUID();
            this.entityData.set(DATA_APPROACH_PARTNER, Optional.of(player.getUUID()));
            this.approachTicks = 0;
            this.approachHold = step;
            this.approachTarget = step.add(-Math.sin(yaw) * 0.65D, 0.0D, Math.cos(yaw) * 0.65D);
            this.approachYaw = Mth.wrapDegrees(player.getYRot() + 180.0F); // the same yaw as the step and her spot
            this.getNavigation().stop();
            return true;
        }


        if ((this.kind == CompanionKind.JENNY || this.kind == CompanionKind.LUNA || this.kind == CompanionKind.KOBOLD)
                && !this.skipPayment && this.needsPaymentStep(scene, player)) {
            // Original: every paid Jenny/Luna action starts with her payment animation, then U() runs the chosen scene.
            if (!this.startScene(player, switch (this.kind) {
                case JENNY -> CompanionScene.JENNY_PAYMENT;
                case KOBOLD -> CompanionScene.KOBOLD_PAYMENT;
                default -> CompanionScene.LUNA_PAYMENT;
            })) {
                return false;
            }
            this.queuedScene = scene;
            this.entityData.set(DATA_PAYMENT_ITEM, switch (scene) {
                case JENNY_DOGGY -> 1;
                case JENNY_BLOWJOB -> 2;
                case JENNY_STRIP -> 3;
                case JENNY_BOOBJOB -> 4;
                case LUNA_TOUCH -> 5;
                case LUNA_SITTING -> 6;
                case KOBOLD_BLOWJOB -> 7;
                case KOBOLD_ANAL -> 8;
                default -> 0;
            });
            return true;
        }

        // original ex.U() after the payment: boobjob and doggy strip a dressed Jenny first (stripDone runs U() again),
        // the blowjob starts straight away in her clothes
        boolean jennyStripFirst = this.kind == CompanionKind.JENNY && !this.isPlayerForm()
                && (scene == CompanionScene.JENNY_BOOBJOB || scene == CompanionScene.JENNY_DOGGY) && !this.isNude();
        if (this.walksToBed() && !this.isPlayerForm() && scene.requiresBed() && this.bedSpotPosition == null
                && !jennyStripFirst) {
            // Like the original: find a bed, walk to it, wait for the player there, then start.
            return this.beginBedWalk(serverLevel, player, scene);
        }

        if (this.kind == CompanionKind.GALATH && !this.skipDelay
                && (scene == CompanionScene.GALATH_COWGIRL || scene == CompanionScene.GALATH_ANAL
                || scene == CompanionScene.GALATH_THREESOME)) {
            // Original: fade out and lock, the scene itself starts 1200 ms later.
            if (player instanceof ServerPlayer serverPlayer) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer, new com.fartypoopy.sexmod.network.TransitionPayload());
            }
            this.delayedScene = scene;
            this.delayedScenePlayer = player.getUUID();
            this.delayedSceneTicks = 24;
            return true;
        }

        this.queuedScene = null;
        if (jennyStripFirst) {
            this.queuedScene = scene;
            scene = CompanionScene.JENNY_STRIP;
        }

        if (scene == CompanionScene.GALATH_THREESOME) {
            this.forcedSceneAnchor = player.position();
            this.forcedSceneAnchorYaw = Mth.wrapDegrees(player.getYRot());
        }
        // original em.a(true, true, uuid) -> dc: she is put 1 block in front of the player, facing him, and the player
        // steps 0.35 forward (Jenny, Luna, Bia talk / headpat, Kobold)
        boolean dcStart = !this.isPlayerForm() && this.forcedSceneAnchor == null && this.bedSpotPosition == null
                && this.startsInFrontOfPlayer(scene);
        boolean keepPartner = dcStart || this.chainStart || this.approachDone || scene == CompanionScene.ELLIE_HUG;
        double playerYaw = Math.toRadians(player.getYRot());
        // original fz (wild bee within 1.5): the same, but facing his body yaw - 180 and he steps 0.2
        boolean bee = scene == CompanionScene.BEE_SEX;
        if (dcStart) {
            this.forcedSceneAnchor = player.position().add(-Math.sin(playerYaw), 0.0D, Math.cos(playerYaw));
            this.forcedSceneAnchorYaw = Mth.wrapDegrees(bee ? player.getYRot() - 180.0F : player.getYHeadRot() + 180.0F);
        }
        SceneAnchor anchor = this.forcedSceneAnchor != null
                ? new SceneAnchor(this.forcedSceneAnchor, this.forcedSceneAnchorYaw)
                : this.kind == CompanionKind.GALATH && scene == CompanionScene.GALATH_COWGIRL
                // original f_.a(String, UUID): the tamed scenes start on the player's spot, cowgirl facing him
                ? new SceneAnchor(player.position(), Mth.wrapDegrees(player.getYRot() + 180.0F))
                : this.kind == CompanionKind.GALATH && scene == CompanionScene.GALATH_ANAL
                ? new SceneAnchor(player.position(), 0.0F) // original: b(0.0F)
                : scene == CompanionScene.GALATH_WILD_CORRUPT
                // original f_ KNOCK_OUT_GROUND click: on her own spot, with the player's yaw
                ? new SceneAnchor(this.position(), Mth.wrapDegrees(player.getYRot()))
                : scene == CompanionScene.ELLIE_HUG
                // original el.n(): she ends the dash 0.5 behind the player, facing the way he faces
                ? new SceneAnchor(player.position().add(Math.sin(playerYaw) * 0.5D, 0.0D, -Math.cos(playerYaw) * 0.5D),
                        Mth.wrapDegrees(player.getYRot()))
                : scene == CompanionScene.GOBLIN_PAIZURI || scene == CompanionScene.GOBLIN_NELSON
                ? new SceneAnchor(player.position(), Mth.wrapDegrees(player.getYRot() + 180.0F))
                : this.bedSpotPosition != null
                ? new SceneAnchor(scene == CompanionScene.BIA_PRONE
                // original e0: the doggy intro moves her one block towards the bed (ck.a(0, 0, 1, yaw))
                ? this.bedSpotPosition.add(-Math.sin(Math.toRadians(this.bedSpotYaw)), 0.0D,
                        Math.cos(Math.toRadians(this.bedSpotYaw)))
                : this.bedSpotPosition, this.bedSpotYaw)
                : scene.anchorsOnBed() ? this.bedSceneAnchor(serverLevel).orElse(null) : null;
        if (anchor == null) {
            float yaw = Mth.wrapDegrees(this.getYRot());
            if (scene == CompanionScene.BIA_TALK || scene.isPayment()) {
                // she faces the player while she talks / takes the payment
                yaw = Mth.wrapDegrees((float) (Mth.atan2(player.getZ() - this.getZ(), player.getX() - this.getX())
                        * 57.2957763671875D) - 90.0F);
            }
            // original fn.a(): the slime catches mid-hop and stays at that height (no gravity in the scene)
            anchor = new SceneAnchor(scene == CompanionScene.SLIME_BLOWJOB ? this.position()
                    : this.findGroundedSceneAnchor(serverLevel), yaw);
        }

        this.forcedSceneAnchor = null;
        this.getNavigation().stop();
        this.setTarget(null);
        this.scenePreviousNoAi = this.isNoAi();
        this.scenePreviousNoGravity = this.isNoGravity();
        this.sceneAnchorPosition = anchor.position();
        this.partnerView = null;
        this.partnerMoved = null;
        this.partnerMoveBase = null;
        this.partnerMoveYaw = Float.NaN;
        this.partnerCamSide = false;
        this.introShiftDone = false;
        this.sceneAnchorYaw = anchor.yaw();
        this.setNoAi(true);
        this.setNoGravity(true);
        this.entityData.set(DATA_FLYING, false);
        this.scenePhaseTicks = -firstFrameHold(null, scene.phase(0));
        this.sceneTicks = 0;
        this.entityData.set(DATA_SCENE, scene.serializedName());
        this.entityData.set(DATA_SCENE_PARTNER, Optional.of(player.getUUID()));

        if (dcStart) {
            if (scene == CompanionScene.ALLIE_DEEPTHROAT) {
                // ev: the camera move right after takes B on the client before the server's 0.35 step arrived
                this.partnerMoveBaseAtStart = player.position();
            }
            double stepLength = bee ? 0.2D : 0.35D;
            Vec3 step = player.position().add(-Math.sin(playerYaw) * stepLength, 0.0D, Math.cos(playerYaw) * stepLength);
            player.teleportTo(step.x, step.y, step.z);
            player.setDeltaMovement(Vec3.ZERO);
        }
        if (keepPartner) {
            this.partnerMoved = player.position(); // held where he stands until a camera move or view
        }
        if (this.formDirectStart && scene.hasPartner() && player instanceof ServerPlayer formPartner
                && formPartner.level() instanceof ServerLevel formLevel) {
            // original ei.b(UUID): the partner is put a block south of her (z + 1), facing 180; then e9 steps him
            // 0.2 back, eq.m() (paizuri) 0.6 north and 0.5 down, eq.q() (anal) onto her spot
            Vec3 at = anchor.position().add(0.0D, 0.0D, 1.0D);
            if (scene == CompanionScene.BEE_SEX) {
                at = at.add(0.0D, 0.0D, 0.2D);
            } else if (scene == CompanionScene.GOBLIN_PAIZURI) {
                at = at.add(0.0D, -0.5D, -0.6D);
            } else if (scene == CompanionScene.GOBLIN_NELSON) {
                at = at.add(0.0D, 0.0D, -1.0D);
            }
            formPartner.teleportTo(formLevel, at.x, at.y, at.z, java.util.Set.of(), 180.0F, formPartner.getXRot());
            this.partnerMoved = at;
            keepPartner = true;
        }
        this.lockSceneTransforms(player, scene.hasPartner(), !keepPartner);
        this.enterScenePhase(0);
        if ((scene == CompanionScene.ELLIE_COWGIRL || scene == CompanionScene.ELLIE_MISSIONARY
                || scene == CompanionScene.BIA_ANAL_WAIT || scene == CompanionScene.BIA_PRONE) && !this.isPlayerForm()) {
            // original el: both set currentModel 0 (nude); e0.func_70619_bc: ANAL_PREPARE with f(0) on reaching the
            // bed, e0.d: f(0) with PRONE_DOGGY_INTRO (the nude model carries her anal pillow). She stays nude after.
            this.setOutfit(OutfitMode.ALTERNATE);
        }
        if ((scene == CompanionScene.GALATH_THREESOME || scene == CompanionScene.GALATH_MORNING)
                && player instanceof ServerPlayer serverPlayer) {
            CompanionEntity daughter = this.daughter();
            if (daughter != null) {
                boolean morning = scene == CompanionScene.GALATH_MORNING;
                daughter.setForcedSceneAnchor(this.sceneAnchorPosition,
                        morning ? this.sceneAnchorYaw : Mth.wrapDegrees(player.getYRot() + 180.0F));
                if (!daughter.startWildScene(serverPlayer,
                        morning ? CompanionScene.MANGLELIE_BED : CompanionScene.MANGLELIE_THREESOME)) {
                    this.finishScene(false);
                    return false;
                }
            } else if (scene == CompanionScene.GALATH_THREESOME) {
                this.finishScene(false);
                return false;
            }
        }
        return true;
    }

    /** Original d(): a horny Ellie dashes at the nearest player within 10 blocks, hugs them and offers the bed scenes. */
    private void tickEllieHorny(ServerLevel serverLevel) {
        if (this.kind != CompanionKind.ELLIE || this.isInScene() || this.isDowned()
                || !this.hasEffect(com.fartypoopy.sexmod.registry.ModPotions.HORNY_EFFECT)) {
            return;
        }
        Player nearest = serverLevel.getNearestPlayer(this, 10.0D);
        if (nearest == null || nearest.isSpectator()) {
            return;
        }
        this.removeEffect(com.fartypoopy.sexmod.registry.ModPotions.HORNY_EFFECT);
        if (this.startScene(nearest, CompanionScene.ELLIE_DASH)) {
            this.queuedScene = CompanionScene.ELLIE_HUG;
        }
    }

    private boolean needsPaymentStep(CompanionScene scene, Player player) {
        if (this.kind == CompanionKind.KOBOLD) {
            // original ff.U: a stranger without the horny effect pays before blowjob / anal; the owner does not
            return (scene == CompanionScene.KOBOLD_BLOWJOB || scene == CompanionScene.KOBOLD_ANAL)
                    && !this.isMaster(player)
                    && !this.hasEffect(com.fartypoopy.sexmod.registry.ModPotions.HORNY_EFFECT);
        }
        if (this.kind == CompanionKind.LUNA) {
            return scene == CompanionScene.LUNA_SITTING || scene == CompanionScene.LUNA_TOUCH;
        }
        boolean paid = scene == CompanionScene.JENNY_BLOWJOB || scene == CompanionScene.JENNY_BOOBJOB
                || scene == CompanionScene.JENNY_DOGGY || (scene == CompanionScene.JENNY_STRIP && !this.isNude());
        return paid && !this.hasEffect(com.fartypoopy.sexmod.registry.ModPotions.HORNY_EFFECT);
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

        Vec3 target = this.partnerTarget(partner);
        if (partner.position().distanceToSqr(target) > 1.0E-4D) {
            partner.teleportTo(target.x, target.y, target.z);
        }
        partner.setDeltaMovement(Vec3.ZERO);
        partner.fallDistance = 0.0F;

        if (initial) {
            // Turn the partner to face the scene; the pitch stays theirs unless the scene's view sets one (like 1.12).
            partner.setYRot(Mth.wrapDegrees(this.sceneAnchorYaw + 180.0F));
        }
    }

    private CompanionScene.PartnerView partnerView;

    /** Ticks since the current scene started. */
    public int sceneTicks() {
        return this.sceneTicks;
    }

    /** Where the partner is held: the anchor, or the last scripted camera offset rotated by the scene yaw + 180. */
    private Vec3 partnerTarget(Player partner) {
        if (this.partnerMoved != null) {
            return this.partnerMoved;
        }
        CompanionScene.PartnerView view = this.partnerView;
        if (view == null) {
            return this.sceneAnchorPosition;
        }
        double theta = Math.toRadians(this.sceneAnchorYaw + 180.0F);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        // original ck.a / e3.b: the offset is rotated with its x mirrored
        double dx = -view.x() * cos - view.z() * sin;
        double dz = -view.x() * sin + view.z() * cos;
        double eye = view.feet() ? 0.0D : partner.getEyeHeight(net.minecraft.world.entity.Pose.STANDING);
        return this.sceneAnchorPosition.add(dx, view.eyeY() - eye, dz);
    }

    /** Original em.B: where the partner stood at the first camera move since the last reset. */
    private Vec3 partnerMoveBase;
    /** Where the last camera move put the partner (held there until the next move or view). */
    private Vec3 partnerMoved;
    /** Original em.r while it differs from the scene yaw (NaN = the scene yaw). */
    private float partnerMoveYaw = Float.NaN;
    /** Original ex.ae / es.as: the boobjob side view is on. */
    private boolean partnerCamSide;

    /**
     * Original em.a(x, y, z, yaw, pitch): the partner goes to where they stood at the first move (em.B) plus x to the
     * side and z ahead of the facing r, y up, and looks at r + yaw with the given pitch.
     */
    private void movePartner(double x, double y, double z, float yaw, float pitch) {
        ServerPlayer partner = this.level() instanceof ServerLevel level
                ? this.scenePartnerId().map(level.getServer().getPlayerList()::getPlayer).orElse(null) : null;
        if (partner == null || !(partner.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.partnerMoveBase == null) {
            this.partnerMoveBase = partner.position();
        }
        float r = Float.isNaN(this.partnerMoveYaw) ? this.sceneAnchorYaw : this.partnerMoveYaw;
        double side = Math.toRadians(r + 90.0F);
        double ahead = Math.toRadians(r);
        Vec3 at = this.partnerMoveBase.add(-Math.sin(side) * x - Math.sin(ahead) * z, y, Math.cos(side) * x + Math.cos(ahead) * z);
        this.partnerMoved = at;
        this.partnerView = null;
        partner.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), Mth.wrapDegrees(r + yaw), pitch);
        partner.setDeltaMovement(Vec3.ZERO);
    }

    /** The player forms' own partner placements where they differ from the girl's (ee, eq). */
    private static CompanionScene.PartnerView formPartnerView(CompanionScene scene, int phase, int ticks) {
        return switch (scene) {
            // original ee: missionary - 0.1 below her spot, turned to her yaw, 60 down; cowgirl - 1.8 ahead of her
            // and 0.65 down, turned to her yaw + 180, 30 up
            case ELLIE_MISSIONARY -> phase == 0 && ticks == 1
                    ? new CompanionScene.PartnerView(0.0D, -0.1D, 0.0D, 0.0F, 60.0F, true) : null;
            case ELLIE_COWGIRL -> phase == 0 && ticks == 1
                    ? new CompanionScene.PartnerView(0.0D, -0.65D, -1.8D, 180.0F, -30.0F, true) : null;
            case GOBLIN_PAIZURI, GOBLIN_NELSON -> null; // placed when the scene starts (eq.m() / q())
            case SLIME_DOGGY, SLIME_BLOWJOB -> null; // ec: only ei.b(UUID) (and the doggy camera move)
            default -> scene.partnerView(phase, ticks);
        };
    }

    /** Set once the blowjob intro's end step (ex.b) is done; reset at scene start. */
    private boolean introShiftDone;

    /** Moves the partner by ck.a((0, 0, d), scene yaw + 180) from where they are, keeping their rotation. */
    private void shiftPartner(double d) {
        ServerPlayer partner = this.level() instanceof ServerLevel level
                ? this.scenePartnerId().map(level.getServer().getPlayerList()::getPlayer).orElse(null) : null;
        if (partner == null) {
            return;
        }
        double yaw = Math.toRadians(this.sceneAnchorYaw + 180.0F);
        Vec3 at = partner.position().add(-Math.sin(yaw) * d, 0.0D, Math.cos(yaw) * d);
        this.partnerMoved = at;
        this.partnerView = null;
        partner.teleportTo(at.x, at.y, at.z);
    }

    /** Original eb / ed setPosition(o() + ck.a(offset, I() + 180)): from her spot, the partner keeps their rotation. */
    private void placePartner(double x, double y, double z) {
        ServerPlayer partner = this.level() instanceof ServerLevel level
                ? this.scenePartnerId().map(level.getServer().getPlayerList()::getPlayer).orElse(null) : null;
        if (partner == null || this.sceneAnchorPosition == null) {
            return;
        }
        double yaw = Math.toRadians(this.sceneAnchorYaw + 180.0F);
        Vec3 at = this.sceneAnchorPosition.add(-Math.cos(yaw) * x - Math.sin(yaw) * z, y, -Math.sin(yaw) * x + Math.cos(yaw) * z);
        this.partnerMoved = at;
        this.partnerView = null;
        partner.teleportTo(at.x, at.y, at.z);
        partner.setDeltaMovement(Vec3.ZERO);
    }

    /** The original scenes' partner camera moves (em.a calls in ex / es / eb / ed / ec / ev / e5). */
    private void tickPartnerCamera(CompanionScene scene, int phase, int ticks) {
        boolean form = this.isPlayerForm();
        switch (scene) {
            case JENNY_BLOWJOB -> {
                if (!form && phase == 0 && ticks == 1 && Float.isNaN(this.partnerMoveYaw)) {
                    // ex bjiMSG1: r = her yaw + 180, the player's facing; the side camera (bjiMSG10) and the cum view
                    // build on it (on her own yaw they came out mirrored and the cum view faced away from her)
                    this.partnerMoveYaw = this.sceneAnchorYaw + 180.0F;
                }
                if (phase == 0 && ticks == 723) { // bjiMSG10, 36.12 s into the intro
                    if (form) {
                        this.movePartner(-0.4D, -0.8D, -0.2D, 60.0F, -3.0F);
                    } else {
                        this.movePartner(-0.65D, -0.8D, -0.25D, 60.0F, -3.0F);
                    }
                } else if (phase == 3 && ticks == 1) { // a(fp): sucking / thrusting -> cum
                    this.movePartner(0.0D, 0.0D, 0.0D, 0.0F, 70.0F);
                } else if (phase == 1 && ticks == 1 && !form && !this.introShiftDone) {
                    // original ex.b(fp) leaving STARTBLOWJOB: the player steps ck.a((0, 0, 0.2), yaw + 180)
                    this.introShiftDone = true;
                    this.shiftPartner(0.2D);
                }
            }
            case JENNY_BOOBJOB -> {
                if (phase == 0 && (ticks == 30 || ticks == 61)) { // paizuri_startStep: her footstep on the block below
                    BlockPos below = this.blockPosition().below();
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            this.level().getBlockState(below).getSoundType(this.level(), below, this).getStepSound(),
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                }
                double side = form ? -0.2D : 0.2D;
                boolean introStep = !form && phase == 1 && ticks == 1 && !this.introShiftDone && this.partnerMoveBase != null;
                if (phase == 0 && ticks == 67) { // boobjob_camera, 3.33 s into paizuri_start
                    if (form) {
                        if (!this.partnerCamSide) {
                            this.partnerCamSide = true;
                            this.partnerMoveYaw = 180.0F;
                            this.movePartner(-0.7D, -0.6D, side, 60.0F, -3.0F);
                        }
                    } else {
                        ServerPlayer partner = this.level() instanceof ServerLevel level
                                ? this.scenePartnerId().map(level.getServer().getPlayerList()::getPlayer).orElse(null) : null;
                        if (partner != null) {
                            this.partnerMoveYaw = partner.getYRot(); // ex: r = the player's yaw
                        }
                        if (!this.partnerCamSide) {
                            this.partnerCamSide = true;
                            this.movePartner(-0.7D, -0.6D, side, 60.0F, -3.0F);
                        }
                    }
                } else if (phase == 1 && ticks == 1) {
                    // paizuri_cumStart at 0.0 of every slow loop: ex places the side camera only while it is not up (ae)
                    if (!this.partnerCamSide) {
                        this.movePartner(-0.7D, -0.6D, side, 60.0F, -3.0F);
                    }
                    if (introStep) {
                        // ex.b(fp) leaving PAIZURI_START: the player himself steps ck.a((0, 0, 0.2), her yaw + 180);
                        // em.B (the camera base) stays. Measured in 1.12: side view 1.05 -> 1.25 and it stays there
                        // through the slow loops; holding shift then gives B + 0.2 = 1.05, releasing it 1.05 again
                        this.introShiftDone = true;
                        this.shiftPartner(0.2D);
                    }
                } else if (phase == 2 && ticks == 1 && this.partnerCamSide) { // c(fp): slow -> fast
                    this.partnerCamSide = false;
                    this.movePartner(0.0D, 0.0D, form ? 0.0D : 0.2D, 0.0F, 70.0F);
                } else if (phase == 2 && ticks == 21 && !this.partnerCamSide) { // paizuri_fastDone, 1.0 s into fast
                    this.partnerCamSide = true;
                    this.movePartner(-0.7D, -0.6D, side, 60.0F, -3.0F);
                } else if (phase == 3 && ticks == 1 && !this.partnerCamSide) { // paizuri_cumStart at 0.0 of the cum
                    this.movePartner(-0.7D, -0.6D, side, 60.0F, -3.0F);
                }
            }
            case JENNY_DOGGY -> {
                if (phase == 0 && ticks == 1) { // ex / es: on her spot, then 0.4 ahead looking down 60; B reset after
                    this.partnerMoveBase = this.sceneAnchorPosition;
                    this.movePartner(0.0D, 0.0D, 0.4D, 0.0F, 60.0F);
                    this.partnerMoveBase = null;
                }
            }
            case SLIME_DOGGY -> {
                if (form && phase == 0 && ticks == 1) { // ec: on her spot facing her rotation, 0.4 ahead, pitch 60
                    this.partnerMoveBase = this.sceneAnchorPosition;
                    this.movePartner(0.0D, 0.0D, 0.4D, 0.0F, 60.0F);
                }
            }
            case LUNA_SITTING -> {
                if (phase == 0 && ticks == 1) { // eb.f() / ed.a(): on her spot, r = her yaw + 180
                    this.partnerMoveBase = this.sceneAnchorPosition;
                    this.partnerMoveYaw = this.sceneAnchorYaw + 180.0F;
                    this.movePartner(0.0D, -0.075D, -0.7109375D, 0.0F, 0.0F);
                } else if ((phase == 2 || phase == 3) && ticks == 1) { // sitting_fastTp (fast loop and cum)
                    this.placePartner(0.0D, -0.160625D, -0.9925D);
                } else if (phase == 1 && ticks == 1) { // sitting_fastDone back to slow
                    this.placePartner(0.0D, -0.075D, -0.7109375D);
                }
            }
            case ALLIE_DEEPTHROAT -> {
                if (phase == 0 && ticks == 1) { // deepthroat_prepareDone (ev / e5): r = her yaw + 180
                    if (this.partnerMoveBaseAtStart != null) {
                        this.partnerMoveBase = this.partnerMoveBaseAtStart;
                        this.partnerMoveBaseAtStart = null;
                    }
                    this.partnerMoveYaw = this.sceneAnchorYaw + 180.0F;
                    this.movePartner(0.0D, 0.0D, 1.35D, 0.0F, 30.0F);
                }
            }
            default -> {
            }
        }
    }

    private void applyPartnerView(CompanionScene.PartnerView view, Player partner) {
        this.partnerMoved = null;
        this.partnerView = view;
        Vec3 target = this.partnerTarget(partner);
        if (partner instanceof ServerPlayer serverPlayer && serverPlayer.level() instanceof ServerLevel level) {
            serverPlayer.teleportTo(level, target.x, target.y, target.z, java.util.Set.of(),
                    view.keepsRotation() ? serverPlayer.getYRot() : Mth.wrapDegrees(this.sceneAnchorYaw + view.yawAdd()),
                    view.keepsRotation() || Float.isNaN(view.pitch()) ? serverPlayer.getXRot() : view.pitch());
        }
    }

    public boolean canControlScene(Player player) {
        return this.isInScene()
                && this.mayUseSceneActions(player)
                && player.distanceToSqr(this) <= 64.0D
                && this.scenePartnerId().filter(player.getUUID()::equals).isPresent();
    }

    private boolean sceneFastHeld;

    public void setSceneFastHeld(boolean held) {
        this.sceneFastHeld = held;
    }

    /** Jump with a full meter (original em.f): go to the finishing phase right away. */
    public void finishSceneFromMeter(Player player) {
        this.requestSceneFinish(player);
    }

    /**
     * Interactive loops never time out by themselves: holding sneak moves to the fast loop at once, releasing it
     * returns to the slow loop when the fast animation ends (original fastDone), and only the meter ends the scene.
     */
    private void tickInteractiveLoop(CompanionScene scene, CompanionScene.Phase phase) {
        int current = this.currentScenePhase();
        if (scene == CompanionScene.ALLIE_REVERSE_COWGIRL) {
            this.tickAllieReverseCowgirl();
            return;
        }
        int faster = scene.nextLoopPhase(current);
        int slower = scene.previousLoopPhase(current);
        if (this.pendingFinish) {
            if (this.scenePhaseTicks >= phase.durationTicks()) {
                if (faster >= 0) {
                    int via = scene.transitionVia(current, faster);
                    this.enterScenePhase(via >= 0 ? via : faster);
                } else {
                    this.pendingFinish = false;
                    this.enterScenePhase(scene.finalePhase());
                }
            }
            return;
        }
        int keyed = scene.keyframeSwitch(current, this.scenePhaseTicks, this.sceneFastHeld, this.random);
        if (keyed >= 0) {
            this.enterScenePhase(keyed);
            return;
        }
        int mid = scene.loopMidVariant(current, this.scenePhaseTicks, this.sceneFastHeld);
        if (mid >= 0) {
            this.enterScenePhase(mid);
            return;
        }
        boolean atEnd = this.scenePhaseTicks >= phase.durationTicks();
        if (faster >= 0 && this.sceneFastHeld && (atEnd || !scene.switchesAtLoopEnd())
                && scene != CompanionScene.BIA_PRONE // e0: soft -> hard only at doggySwitch
                && scene != CompanionScene.KOBOLD_MATING_PRESS) { // ff: soft -> hard only at mating_press_softReady
            int via = scene.transitionVia(current, faster);
            this.enterScenePhase(via >= 0 ? via : faster);
            return;
        }
        if (this.scenePhaseTicks >= phase.durationTicks()) {
            if (slower >= 0 && (!this.sceneFastHeld || scene.fastAlwaysEndsSlow())) {
                int via = scene.transitionVia(current, slower);
                this.enterScenePhase(via >= 0 ? via : slower);
                return;
            }
            int variant = scene.loopEndVariant(current, this.sceneFastHeld, this.random);
            if (variant >= 0) {
                this.enterScenePhase(variant);
                return;
            }
            if (scene == CompanionScene.KOBOLD_BLOWJOB && !this.sceneFastHeld && this.random.nextBoolean()) {
                // original `switch` keyframe: half the time the slow loop changes sides through a switch animation
                this.enterScenePhase(current == 1 ? 6 : 5);
                return;
            }
            this.scenePhaseTicks = 0;
        }
    }

    /** Original ev.T / ev.L: the slow and "continues" variants (1-3), kept per girl like the original's fields. */
    private int allieSlowVariant = 1;
    /**
     * Original slow-loop variant flags (ff.a4 Kobold blowjob side, e3.aD breeding l / r, e3.aF nelson slow / slow2,
     * e3.aP paizuri slow / slow2): the animation name is built from the flag, so every way back into the slow loop
     * (from the fast loop, or the goblin's next scene of the same kind) plays the variant last chosen. Per entity, not
     * saved, like the original fields.
     */
    private int galathRapeVariant;

    private final java.util.EnumMap<CompanionScene, Integer> slowVariantMemory = new java.util.EnumMap<>(CompanionScene.class);
    private int allieFastVariant = 1;

    /**
     * Original ev: SLOW plays slow{T}, a new T each loop (cowgirlSlowDone); the fast key goes to FAST_START at once
     * (c(fp)); FAST_START goes on to FAST_CONTINUES (fastc{L}) if the key is held at fastSwitch (tick 10), else back to
     * slow after 34 ticks; FAST_CONTINUES restarts with a new L if held at its fastSwitch (tick 20), else slow at 39.
     */
    private void tickAllieReverseCowgirl() {
        int current = this.currentScenePhase();
        int ticks = this.scenePhaseTicks;
        int slow = this.allieSlowVariant == 1 ? 1 : this.allieSlowVariant == 2 ? 4 : 5;
        if (current == 1 || current == 4 || current == 5) {
            if (this.sceneFastHeld) {
                this.enterScenePhase(2);
            } else if (ticks >= 48) {
                int previous = this.allieSlowVariant;
                do {
                    this.allieSlowVariant = 1 + this.random.nextInt(3);
                } while (this.allieSlowVariant == previous);
                this.enterScenePhase(this.allieSlowVariant == 1 ? 1 : this.allieSlowVariant == 2 ? 4 : 5);
            }
        } else if (current == 2) {
            if (this.sceneFastHeld && ticks == 10) {
                this.enterScenePhase(5 + this.allieFastVariant);
            } else if (ticks >= 34) {
                this.enterScenePhase(slow);
            }
        } else if (current >= 6) {
            if (this.sceneFastHeld && ticks == 20) {
                int previous = this.allieFastVariant;
                do {
                    this.allieFastVariant = 1 + this.random.nextInt(3);
                } while (this.allieFastVariant == previous);
                this.enterScenePhase(5 + this.allieFastVariant);
            } else if (ticks >= 39) {
                this.enterScenePhase(slow);
            }
        }
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
        int finale = scene.finalePhase();
        if (scene.transitionVia(0, 1) >= 0 && finale >= 0) {
            // original f_ MORNING_BLOWJOB / f8 THREESOME (S / N set by the jump): the finish waits for the current loop;
            // from the slow loop it plays the soft transition into the fast one, and the fast loop's end starts the cum
            this.pendingFinish = true;
            return true;
        }
        if (finale >= 0 && this.currentScenePhase() != finale) {
            this.enterScenePhase(finale);
        } else {
            this.finishScene(true);
        }
        return true;
    }

    /** Original f_.S / f8.N: the jump asked for the finish; it starts when the current loop ends. */
    private boolean pendingFinish;

    public void stopScene() {
        this.finishScene(false);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        if (this.goblinBehavior != null) {
            int st = this.goblinState(); // original e3: no fall damage while being thrown
            if (st == GoblinBehavior.THROWN || st == GoblinBehavior.THROWING) {
                return false;
            }
        }
        return this.slimeBehavior == null && super.causeFallDamage(fallDistance, multiplier, source); // original Slime: no fall damage
    }

    /** Original e3.canBeCollidedWith: only a running, waiting or idle goblin that nobody carries can be hit or clicked. */
    @Override
    public boolean isPickable() {
        if (this.isPlayerForm()) {
            return false; // original ei.canBeCollidedWith
        }
        if (this.kind == CompanionKind.MANGLELIE && this.familyId() != null) {
            return false; // original f8.canBeCollidedWith: only while she has no mother
        }
        if (this.goblinBehavior != null) {
            int st = this.goblinState();
            if (st == GoblinBehavior.THROWN) {
                return false;
            }
            if (st == GoblinBehavior.RUN || st == GoblinBehavior.AWAIT_PICK_UP) {
                return super.isPickable();
            }
            if (this.goblinCarrier().isPresent() || st != GoblinBehavior.NULL) {
                return false;
            }
        }
        return super.isPickable();
    }

    @Override
    public void setRemainingFireTicks(int ticks) {
        if (this.goblinBehavior != null && this.goblinCarrier().isPresent()) {
            return; // original e3.setFire: a carried goblin does not burn
        }
        super.setRemainingFireTicks(ticks);
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

    /**
     * Original eye heights (Jenny 1.64, Ellie 1.9, Luna 1.34, Kobold 0.94, Goblin 0.75, Galath 1.9); Bia, Slime, Bee,
     * Allie and Manglelie keep the default 0.85 x height, as in 1.12.
     */
    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        if (this.kind != null && this.isPlayerForm()) {
            return net.minecraft.world.entity.EntityDimensions.fixed(0.01F, 0.01F); // original ei: setSize(0.01, 0.01)
        }
        if (this.kind == CompanionKind.GALATH && this.entityData != null && this.bossState() == GalathBoss.S_KO_GROUND) {
            // 1.21 change: knocked_out lays her body ~1.2 blocks in front of her feet, outside the standing hitbox, so
            // right-clicking what you see missed her; lying down her box covers the body instead
            return net.minecraft.world.entity.EntityDimensions.fixed(3.0F, 0.9F).withEyeHeight(0.5F);
        }
        var dimensions = super.getDefaultDimensions(pose);
        Float eye = this.kind == null ? null : switch (this.kind) {
            case JENNY -> 1.64F;
            case ELLIE -> 1.9F;
            case LUNA -> 1.34F;
            case KOBOLD -> 0.94F;
            case GOBLIN -> 0.75F;
            case GALATH -> 1.9F; // f_.func_70047_e
            default -> null;
        };
        return eye == null ? dimensions : dimensions.withEyeHeight(eye);
    }

    /** Original jump motion: Luna 0.5, Kobold 0.45. */
    @Override
    protected float getJumpPower() {
        return switch (this.kind) {
            case LUNA -> (this.isInWater() ? 1.0F : 0.5F) * this.getBlockJumpFactor() + this.getJumpBoostPower();
            case KOBOLD -> 0.45F * this.getBlockJumpFactor() + this.getJumpBoostPower();
            default -> super.getJumpPower();
        };
    }

    /**
     * 1.21's client copy of a mob pushes the local player away from itself. A goblin riding on (or being picked up or
     * thrown by) her carrier sits on the player's own spot, one interpolation step behind, so she pushed the carrier
     * backwards all the time ("gliding like on ice"). 1.12 had no such push; nothing that holds her pushes. The same
     * push shoved the player forward when the queen and her two guards crowd around him in the lair (user report), so
     * on the client (where 1.21 only pushes the local player) no goblin pushes.
     */
    @Override
    protected void pushEntities() {
        if (this.goblinBehavior != null && (this.goblinCarrier().isPresent() || this.level().isClientSide())) {
            return;
        }
        super.pushEntities();
    }

    @Override
    public boolean isPushable() {
        // original em.canBePushed is false for every girl; only the Kobold (ff) can be pushed
        return this.kind == CompanionKind.KOBOLD && !this.isPlayerForm() && !this.isInScene() && super.isPushable();
    }

    @Override
    public void tick() {
        super.tick();

        if (this.tickCount == 1 && this.level() instanceof ServerLevel duplicateCheck && !this.technicalProxy) {
            // original em.readEntityFromNBT: a girl whose ID is already loaded elsewhere is deleted ("got a duped ...")
            for (net.minecraft.world.entity.Entity other : duplicateCheck.getAllEntities()) {
                if (other != this && other instanceof CompanionEntity girl && !girl.technicalProxy
                        && !girl.isRemoved() && girl.girlId.equals(this.girlId)) {
                    this.discard();
                    return;
                }
            }
        }
        if (this.isPlayerForm()) {
            if (this.level() instanceof ServerLevel formLevel) {
                PlayerForms.tickForm(this, formLevel);
                this.tickScene(formLevel);
            } else if (this.kind == CompanionKind.ALLIE && this.tickCount % 10 == 0) {
                // original e5.a(): a portal particle off one of her tail bones every 10 ticks (no arrival burst)
                com.fartypoopy.sexmod.client.ClientHooks.allieTailParticle(this);
            }
            return;
        }
        if (this.kind == CompanionKind.KOBOLD && this.level().isClientSide) {
            com.fartypoopy.sexmod.client.ClientHooks.koboldGreeting(this);
        }
        if (this.kind == CompanionKind.MANGLELIE && this.level().isClientSide) {
            com.fartypoopy.sexmod.client.ClientHooks.mangLook(this);
        }
        if (this.kind == CompanionKind.ALLIE && this.level().isClientSide) {
            if (this.tickCount == 1) {
                // original ev.d() with cj.a: a ring of 300 portal particles (radius 0.75) rising at up to 1.5 when she arrives
                for (int i = 0; i < 300; i++) {
                    double angle = Math.PI * 2.0D * i / 300.0D;
                    this.level().addParticle(ParticleTypes.PORTAL, this.getX() + Math.sin(angle) * 0.75D, this.getY(),
                            this.getZ() + Math.cos(angle) * 0.75D, 0.0D, this.random.nextFloat() * 1.5D, 0.0D);
                }
            }
            if (this.tickCount % 10 == 0) {
                // original ev.b(): a portal particle off a random one of her tail bones (tail0-7) every 10 ticks
                com.fartypoopy.sexmod.client.ClientHooks.allieTailParticle(this);
            }
        }

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.tickCount >= 1 && this.homePos().isEmpty()
                && (this.kind.hasMasterAI() || this.kind == CompanionKind.BEE)) {
            this.setHome(this.blockPosition()); // original em.updateAITasks: home defaults to where she first stood
        }
        if (this.kind == CompanionKind.LUNA && this.tickCount % 10 == 0) {
            // original eb: speed 1.0 while she has no master, 0.5 with one
            var speedAttribute = this.getAttribute(Attributes.MOVEMENT_SPEED);
            double wanted = this.hasMaster() ? 0.5D : 1.0D;
            if (speedAttribute != null && speedAttribute.getBaseValue() != wanted) {
                speedAttribute.setBaseValue(wanted);
            }
        }

        if (this.tickCount == 1 && this.getHealth() > this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }

        if (this.rangedAttackCooldown > 0) {
            this.rangedAttackCooldown--;
        }

        if (this.approachScene != null) {
            this.tickApproach(serverLevel);
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
        if (this.kind == CompanionKind.GALATH) {
            this.tickGalathCoinStates(serverLevel);
        }
        if (this.galathDespawnTicks >= 0 && --this.galathDespawnTicks < 0) {
            this.discard();
            return;
        }
        if (this.kind == CompanionKind.LUNA && !this.isPlayerForm()) {
            // original eb.m(): Y = distance from her to the end of her path (kept while she has none); she runs from 3 on
            net.minecraft.world.level.pathfinder.Path path = this.getNavigation().getPath();
            if (path != null && path.getEndNode() != null) {
                net.minecraft.world.level.pathfinder.Node end = path.getEndNode();
                BlockPos at = this.blockPosition();
                double dx = end.x - at.getX();
                double dy = end.y - at.getY();
                double dz = end.z - at.getZ();
                this.entityData.set(DATA_SPEED_CLASS, Math.sqrt(dx * dx + dy * dy + dz * dz) < 3.0D ? 0 : 2);
            }
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
        if (this.lunaFishing != null) {
            this.lunaFishing.tick(serverLevel);
        }
        if (this.goblinBehavior != null) {
            this.goblinBehavior.tick(serverLevel);
        }
        if (this.manglelieBehavior != null) {
            this.manglelieBehavior.tick(serverLevel);
        }
        if (this.boss != null) {
            this.boss.tick(serverLevel);
            if (this.hasMaster()) {
                if (this.galathFollow == null) {
                    this.galathFollow = new GalathFollowAi(this);
                }
                this.galathFollow.tick(serverLevel);
                if (!this.isInWater() && !this.isNoGravity() && this.getDeltaMovement().y < 0.0D
                        && this.action() != CompanionAction.GALATH_MASTURBATE) {
                    // original f_.au(): a tamed Galath glides down slowly
                    Vec3 motion = this.getDeltaMovement();
                    this.setDeltaMovement(motion.x, motion.y * 0.4D, motion.z);
                }
                if (this.familyId() != null && !this.isInScene()) {
                    this.boss.tickTamedCombat(serverLevel); // original ao(): a mother with a daughter fights near her
                }
            }
        }
        this.tickEllieHorny(serverLevel);
        this.tickTargetSanity();
        this.tickGoHome(serverLevel);
        this.tickHealing(serverLevel);
        this.tickFlight();
        this.tickStaticModelCode(serverLevel);

        if (this.tickCount % 20 == 0) {
            this.syncEquipmentSlots();
        }

        if (this.kind == CompanionKind.GALATH
                && this.summonedByGalathCoin) { // original v.c() in f_.ae(), every AI tick
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
                .map(id -> this.isPlayerForm() ? scenePlayer(serverLevel, id) : serverLevel.getServer().getPlayerList().getPlayer(id))
                .orElse(null);
        if (controller == null
                || !controller.isAlive()
                || controller.level() != this.level()
                || controller.distanceToSqr(this) > (scene.isWaitScene() ? 1024.0D : 64.0D)) {
            this.finishScene(false);
            return;
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
        this.sceneTicks++;
        CompanionScene.PartnerView view = this.isPlayerForm()
                ? formPartnerView(scene, this.currentScenePhase(), this.scenePhaseTicks)
                : scene.partnerView(this.currentScenePhase(), this.scenePhaseTicks);
        if (view != null && controller != null) {
            this.applyPartnerView(view, controller);
        }
        if (scene == CompanionScene.GALATH_ANAL && this.sceneTicks == 1 && controller != null) {
            // original f_.d(true) (tamed, from NULL) / er.a() (form): the player goes to ck.a((0.5, 0.5 - eye, 0.4), yaw).
            // Not original: the NPC scene's first person views from his in-animation head (boyCam), so he starts
            // facing her (as the scene start turned him) and 50 degrees down onto her instead of at the horizon
            this.applyPartnerView(this.isPlayerForm() ? CompanionScene.PartnerView.place(-0.5D, 0.5D, -0.4D)
                    : new CompanionScene.PartnerView(-0.5D, 0.5D, -0.4D, 180.0F, 50.0F), controller);
        }
        if (this.kind == CompanionKind.GALATH && this.isPlayerForm() && this.currentScenePhase() == 2
                && (scene == CompanionScene.GALATH_COWGIRL && this.scenePhaseTicks == 25
                || scene == CompanionScene.GALATH_ANAL && this.scenePhaseTicks == 27)) {
            // original er: clearcum (rape_cum 1.21 s, corrupt_cum 1.33 s) falls through into reset, which ends it
            this.finishScene(true);
            return;
        }
        if (scene == CompanionScene.GOBLIN_BREEDING && this.sceneTicks == 205 && controller != null) {
            // original e3.w(): 205 ticks after the breeding began the player is moved again (not turned)
            this.applyPartnerView(CompanionScene.PartnerView.place(0.0D, 0.15625D, -0.8859375D), controller);
        }
        this.tickPartnerCamera(scene, this.currentScenePhase(), this.scenePhaseTicks);
        if (scene.isWild()) {
            this.tickWildScene(serverLevel, scene, controller, phase);
            return;
        }
        if (scene == CompanionScene.GALATH_THREESOME) {
            CompanionEntity daughter = this.daughter();
            if (daughter == null || daughter.currentScene() != CompanionScene.MANGLELIE_THREESOME) {
                this.finishScene(true); // her daughter's scene is over
            }
            return;
        }
        if (scene == CompanionScene.MANGLELIE_BED) {
            CompanionEntity mother = ManglelieBehavior.find(serverLevel, this.familyId());
            if (mother == null || mother.currentScene() != CompanionScene.GALATH_MORNING) {
                this.finishScene(true);
            }
            return;
        }
        if (scene.isWildCorrupt() && this.currentScenePhase() == 4) {
            if (this.scenePhaseTicks == 95) {
                // original: 100 dragon-breath particles around her, converging on her (velocity -0.03 x offset)
                for (int i = 0; i < 100; i++) {
                    double ox = (this.random.nextDouble() * 2.0D - 1.0D) * 1.5D;
                    double oy = (this.random.nextDouble() * 2.0D - 1.0D) * 1.5D;
                    double oz = (this.random.nextDouble() * 2.0D - 1.0D) * 1.5D;
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH,
                            this.getX() + ox, this.getY() + 1.5D + oy, this.getZ() + oz, 0,
                            -0.03D * ox, -0.03D * oy, -0.03D * oz, 1.0D);
                }
            }
        }
        if (scene == CompanionScene.ELLIE_HUG && this.currentScenePhase() == 0 && this.scenePhaseTicks == 146) {
            this.openEllieHugMenu(serverLevel);
        }
        if (scene == CompanionScene.JENNY_PAYMENT && this.scenePhaseTicks == 43) {
            this.sayPaymentLine(controller);
        }
        if (scene == CompanionScene.GOBLIN_NELSON && this.currentScenePhase() == 3
                && this.scenePhaseTicks == 1) {
            this.setGoblinPreggo(true); // original e3.b(fp): the belly shows while NELSON_CUM plays
        }
        if (scene == CompanionScene.KOBOLD_PAYMENT && this.scenePhaseTicks == 10) {
            controller.sendSystemMessage(Component.literal("<").append(controller.getDisplayName())
                    .append("> I'd like to use ur services owo")); // original em.a(ae(), msg): partner's name, shown to the partner
        }
        if ((scene == CompanionScene.ALLIE_RICH && this.scenePhaseTicks >= 50)
                || (scene == CompanionScene.ALLIE_RICH_NORMAL && this.scenePhaseTicks >= 51)) {
            this.finishScene(true); // original: the scene ends once her fade-out has run through
            return;
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
        if (scene == CompanionScene.BIA_TALK && this.currentScenePhase() == 1) {
            return; // original TALK_IDLE: she waits until the player picks a scene or closes the menu
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
        if (scene == CompanionScene.ELLIE_COWGIRL && this.isPlayerForm() && this.currentScenePhase() == 2
                && (this.scenePhaseTicks == 25 || this.scenePhaseTicks == 61)) {
            // ee cowgirlfastReady (1.24 s / 3.04 s): released key drops to the slow loop; a held key reloads the
            // animation (clearAnimationCache) three times in four, restarting it
            if (!this.sceneFastHeld) {
                this.enterScenePhase(1);
                return;
            }
            if (this.random.nextInt(4) != 1) {
                this.restartScenePhase();
                return;
            }
        }
        int ready = scene.readySwitch(this.currentScenePhase(), this.scenePhaseTicks, this.sceneFastHeld);
        if (ready >= 0) {
            if (ready == this.currentScenePhase()) {
                this.restartScenePhase();
            } else {
                this.enterScenePhase(ready);
            }
            return;
        }
        if (scene == CompanionScene.BIA_PRONE && this.currentScenePhase() == 0) {
            // original e0.c(PRONE_DOGGY_INTRO) = INSERT: the intro loops until the fast key is pressed
            if (this.sceneFastHeld) {
                this.enterScenePhase(1);
            } else if (this.scenePhaseTicks >= phase.durationTicks()) {
                this.scenePhaseTicks = 0;
            }
            return;
        }
        if (phase.loop() && scene.hasInteractivePhases()) {
            this.tickInteractiveLoop(scene, phase);
            return;
        }
        int timeout = phase.durationTicks();
        if (this.scenePhaseTicks < timeout) {
            return;
        }

        if (scene.transitionVia(0, 1) >= 0 && this.currentScenePhase() == scene.finalePhase()) {
            this.finishScene(true); // original: CUM's end clears the state (f_.b(null)); soft / back come after it in the list
            return;
        }
        int nextPhase = this.currentScenePhase() + 1;
        int transitionTarget = scene.transitionTarget(this.currentScenePhase());
        if (transitionTarget >= 0) {
            nextPhase = transitionTarget;
        }
        if (nextPhase >= scene.phases().size() || scene.isVariantPhase(nextPhase)) {
            this.finishScene(true);
        } else {
            this.enterScenePhase(nextPhase);
        }
    }

    /**
     * Original em.a() / cv: a new animation holds its first frame for its state's transitionTick before it plays, and
     * the scene moves on from its keyframes, so the phase clock starts that many ticks early (phase ticks stay animation
     * time). The same animation going on (a loop, an N() restart) holds nothing.
     */
    private static int firstFrameHold(CompanionScene.Phase before, CompanionScene.Phase after) {
        if (after == null || before != null && before.animation().equals(after.animation())
                || TICK_TIMED.stream().anyMatch(after.animation()::startsWith)) {
            return 0;
        }
        return TransitionTicks.forState(after.animation());
    }

    /**
     * Phases of fp states with a length / followUp: the original counts their server ticks from the state change
     * (++ticksPlaying >= length), so the first-frame hold does not lengthen them.
     */
    private static final java.util.Set<String> TICK_TIMED = java.util.Set.of(
            "animation.ellie.carry_intro", "animation.galath.corrupt_intro", "animation.galath.give_coin",
            "animation.bia.prone_doggy_insert", "animation.bia.prone_doggy_hard", "animation.galath.rape_intro",
            "animation.galath.rape_cum", "animation.allie.reverse_cowgirl_start", "animation.allie.reverse_cowgirl_fasts",
            "animation.allie.reverse_cowgirl_fastc", "animation.jenny.wave", "animation.bia.wave",
            "animation.ellie.hug"); // el Y = 150 countdown

    /** Original N(): the current animation starts over (clients see the serial change and reset the controller). */
    private void restartScenePhase() {
        this.scenePhaseTicks = 0;
        this.entityData.set(DATA_PHASE_SERIAL, this.entityData.get(DATA_PHASE_SERIAL) + 1);
    }

    private void enterScenePhase(int phaseIndex) {
        CompanionScene scene = this.currentScene();
        if (scene == null || scene.phase(phaseIndex) == null) {
            this.finishScene(true);
            return;
        }
        CompanionScene.Phase before = scene.phase(this.currentScenePhase());
        if (scene == CompanionScene.GALATH_COWGIRL || scene == CompanionScene.GALATH_WILD_RAPE) {
            // f_ / er b1: the rape loop is built from a per-entity index that both scenes share and keep
            int[] loops = scene == CompanionScene.GALATH_COWGIRL ? new int[] {1, 3, 4} : new int[] {1, 2, 3};
            int previous = this.currentScenePhase();
            boolean fromLoop = previous == loops[0] || previous == loops[1] || previous == loops[2];
            if (phaseIndex == loops[0] && !fromLoop) {
                phaseIndex = loops[this.galathRapeVariant];
            }
            for (int k = 0; k < 3; k++) {
                if (loops[k] == phaseIndex) {
                    this.galathRapeVariant = k;
                }
            }
        }
        int[] slowPair = scene.slowVariantPair();
        if (slowPair != null) {
            int previous = this.currentScenePhase();
            if (scene == CompanionScene.KOBOLD_BLOWJOB && previous == 0) {
                this.slowVariantMemory.remove(scene); // blowjobStartDone: a4 = true (R side)
            } else if (phaseIndex == slowPair[0] && scene.canonicalPhase(previous) != slowPair[0]) {
                phaseIndex = this.slowVariantMemory.getOrDefault(scene, slowPair[0]);
            }
            if (scene.canonicalPhase(phaseIndex) == slowPair[0]) {
                this.slowVariantMemory.put(scene, phaseIndex);
            } else if (scene == CompanionScene.KOBOLD_BLOWJOB && (phaseIndex == 5 || phaseIndex == 6)) {
                this.slowVariantMemory.put(scene, scene.transitionTarget(phaseIndex)); // endSwitch flips a4
            }
        }
        this.scenePhaseTicks = -firstFrameHold(before, scene.phase(phaseIndex));
        this.entityData.set(DATA_SCENE_PHASE, phaseIndex);
        this.onScenePhaseEntered(scene, phaseIndex);
    }

    /** Original el hugDone (7.2 s into the hug, after its 2-tick first-frame hold): the cowgirl / missionary menu. */
    private void openEllieHugMenu(ServerLevel serverLevel) {
        ServerPlayer controller = this.scenePartnerId()
                .map(serverLevel.getServer().getPlayerList()::getPlayer)
                .orElse(null);
        if (controller != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    controller,
                    new com.fartypoopy.sexmod.network.OpenSceneMenuPayload(
                            this.getId(),
                            List.of(CompanionScene.ELLIE_COWGIRL.serializedName(), CompanionScene.ELLIE_MISSIONARY.serializedName())));
        }
    }

    private void onScenePhaseEntered(CompanionScene scene, int phaseIndex) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
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
                        new com.fartypoopy.sexmod.network.OpenSceneMenuPayload(
                                this.getId(),
                                List.of(CompanionScene.BIA_ANAL.serializedName(), CompanionScene.BIA_PRONE.serializedName())));
            }
        }
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
    private static final double[] BED_STAND_X = {0.5D, 0.5D, -0.5D, 1.5D};
    private static final double[] BED_STAND_Z = {-0.5D, 1.5D, 0.5D, 0.5D};
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

    /**
     * Original em.a(pos, n, bed, 22, 3): a square spiral around her block (arm lengths 1 to 21, each step checking the
     * cell one further on and 3 blocks up and down); the n-th bed block met, the first one for n <= 1.
     */
    static BlockPos spiralBed(ServerLevel level, BlockPos start, int n) {
        int found = 0;
        int dir = -1;
        BlockPos cur = start;
        for (int arm = 1; arm < 22; ) {
            for (int turn = 0; turn < 2; turn++, arm++) {
                dir = -dir;
                for (int step = 0; step < arm; step++) {
                    cur = cur.offset(0, 0, dir);
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos check = cur.offset(0, dy, dir);
                        if (level.getBlockState(check).is(BlockTags.BEDS) && ++found >= n) {
                            return check;
                        }
                    }
                }
                for (int step = 0; step < arm; step++) {
                    cur = cur.offset(dir, 0, 0);
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos check = cur.offset(dir, dy, 0);
                        if (level.getBlockState(check).is(BlockTags.BEDS) && ++found >= n) {
                            return check;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Original e0.a(BlockPos): a bed half on one side and a free cell on the opposite side. */
    private boolean biaDoggyBed(ServerLevel level, BlockPos bed) {
        if (bed == null) {
            return false;
        }
        for (int side = 0; side < 4; side++) {
            int dx = BED_SIDES[side][0];
            int dz = BED_SIDES[side][1];
            if (level.getBlockState(bed.offset(dx, 0, dz)).is(BlockTags.BEDS) && isBedStandCell(level, bed.offset(-dx, 0, -dz))) {
                return true;
            }
        }
        return false;
    }

    /** The original wants air; carpets, plants and bottom slabs (common in 1.21 bedrooms) are accepted too. */
    private static boolean isBedStandCell(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
            return true;
        }
        return state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                && state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
                == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM;
    }

    /** Searches for a bed and starts walking to it; false (with her refusal line) when none is usable. */
    public boolean beginBedWalk(ServerLevel serverLevel, Player player, CompanionScene scene) {
        BlockPos center = this.blockPosition();
        // Original per girl: Jenny (ex.a), Luna (eb.a) and Bia's anal (e0.b) take the first bed of the spiral search
        // and any free side; Bia's doggy (e0.a) takes the first bed with a bed half and a free cell on opposite
        // sides, and a free side whose opposite cell is a bed; Ellie (el.g) tries bed after bed until one has a free
        // side whose opposite cell is a bed. Of the free sides the one closest to her block corner wins.
        boolean ellie = this.kind == CompanionKind.ELLIE;
        boolean doggy = scene == CompanionScene.BIA_PRONE;
        boolean needBedBehind = ellie || doggy;
        String refusal = needBedBehind ? "jenny.dialogue.nobedinsight" : "jenny.dialogue.bedobscured";
        BedWalk best = null;
        for (int n = doggy ? 0 : 1; best == null; n++) {
            BlockPos bed = spiralBed(serverLevel, center, n);
            if (doggy) {
                if (n >= 50) {
                    break;
                }
                if (!this.biaDoggyBed(serverLevel, bed)) {
                    continue;
                }
            }
            if (bed == null) {
                refusal = "jenny.dialogue.nobedinsight";
                break;
            }
            double bestDistance = Double.MAX_VALUE;
            for (int side = 0; side < 4; side++) {
                int dx = BED_SIDES[side][0];
                int dz = BED_SIDES[side][1];
                BlockPos stand = bed.offset(dx, 0, dz);
                BlockState standState = serverLevel.getBlockState(stand);
                boolean lowSlab = standState.getBlock() instanceof net.minecraft.world.level.block.SlabBlock
                        && standState.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
                        == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM;
                if (!isBedStandCell(serverLevel, stand)) {
                    continue;
                }
                if (needBedBehind && !serverLevel.getBlockState(bed.offset(-dx, 0, -dz)).is(BlockTags.BEDS)) {
                    continue;
                }
                double x = bed.getX() + (ellie ? ELLIE_STAND_X[side] : BED_STAND_X[side]);
                double z = bed.getZ() + (ellie ? ELLIE_STAND_Z[side] : BED_STAND_Z[side]);
                double distance = center.distToLowCornerSqr(x, bed.getY(), z);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new BedWalk(scene, player.getUUID(),
                            new Vec3(x, stand.getY() + (lowSlab ? 0.5D : 0.0D), z), BED_SIDE_YAW[side]);
                }
            }
            if (!ellie) {
                break; // only Ellie moves on to the next bed
            }
        }

        if (best == null) {
            if (this.lunaFishing != null) {
                this.lunaFishing.markDone(); // original eb.a sets ar before searching: no more fishing after a refusal
            }
            this.refuseBed(serverLevel, refusal);
            return false;
        }

        this.bedWalk = best;
        this.bedWalkTicks = 0;
        if (this.lunaFishing != null) {
            this.lunaFishing.markDone(); // original ar: she stops fishing after walking to a bed
        }
        this.setTarget(null);
        return true;
    }

    private void refuseBed(ServerLevel serverLevel, String dialogueKey) {
        if (this.kind == CompanionKind.LUNA) {
            if (dialogueKey.endsWith("bedobscured")) {
                this.sayNearby(serverLevel, Component.literal("Heh.. the bed is obscured.. but I already ate the fish so nya~ hehe"));
            } else {
                // original eb: this line alone is prefixed with the plain type name and reaches 50 blocks
                Component line = Component.literal("<" + this.originalName() + "> Heh.. there is no bed nearby.. but I already ate the fish so nya~ hehe");
                for (ServerPlayer player : serverLevel.players()) {
                    if (player.distanceToSqr(this) <= 50.0D * 50.0D) {
                        player.sendSystemMessage(line);
                    }
                }
            }
            this.playCue("girls.luna.giggle.giggle" + this.random.nextInt(3), 1.0F);
            return;
        }
        if (this.kind == CompanionKind.ELLIE) {
            this.sayNearby(serverLevel, Component.literal("no bed in sight..."));
            this.playCue("girls.ellie.sigh.sigh0", 6.0F);
            return;
        }
        com.fartypoopy.sexmod.registry.ModSounds.find(
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

    /** Original e3.func_70664_aZ / j(): a fleeing goblin only jumps with no path, at the path's end, or for a one block step. */
    @Override
    public void jumpFromGround() {
        if (this.goblinBehavior != null && this.goblinState() == GoblinBehavior.RUN) {
            net.minecraft.world.level.pathfinder.Path path = this.getNavigation().getPath();
            if (path != null) {
                int index = path.getNextNodeIndex();
                int count = path.getNodeCount();
                if (count != index && count - 1 != index
                        && path.getNode(index + 1).y - path.getNode(index).y != 1) {
                    return;
                }
            }
        }
        super.jumpFromGround();
    }

    /** Original em.func_184639_G: a 1 in 10000 fart per ambient roll; the client adds the "whopa" chat within 10 blocks. */
    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        if (this.random.nextInt(10000) != 0) {
            return null;
        }
        if (this.level().isClientSide) {
            com.fartypoopy.sexmod.client.ClientHooks.whopa(this);
        }
        String path = "misc.fart.fart" + this.random.nextInt(3);
        return com.fartypoopy.sexmod.registry.ModSounds.find(path).map(h -> h.get()).orElse(null);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        if (this.kind == CompanionKind.JENNY) {
            return null; // original ex: no hurt sound
        }
        if (this.kind == CompanionKind.GALATH) {
            long now = this.level().getGameTime(); // original: one of seven UUH sounds, at most once per second
            if (now - this.galathHurtSoundAt < 20) {
                return null;
            }
            this.galathHurtSoundAt = now;
            return com.fartypoopy.sexmod.registry.ModSounds.find("girls.galath.uuh.uuh" + this.random.nextInt(7))
                    .map(holder -> holder.get()).orElse(null);
        }
        if (this.kind == CompanionKind.LUNA) {
            return com.fartypoopy.sexmod.registry.ModSounds.find("girls.luna.ouu.ouu" + this.random.nextInt(13))
                    .map(holder -> holder.get()).orElse(null);
        }
        return super.getHurtSound(source);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        if (this.kind == CompanionKind.JENNY) {
            return com.fartypoopy.sexmod.registry.ModSounds.find("girls.jenny.sigh.sigh" + this.random.nextInt(2))
                    .map(holder -> holder.get()).orElse(null);
        }
        if (this.kind == CompanionKind.LUNA) {
            String path = this.random.nextInt(20) == 0 ? "girls.allie.scawy.scawy2" : "girls.luna.ouu.ouu12";
            return com.fartypoopy.sexmod.registry.ModSounds.find(path).map(holder -> holder.get()).orElse(null);
        }
        return super.getDeathSound();
    }

    public void playCue(String path, float volume) {
        float pitch = this.kind == CompanionKind.KOBOLD && path.startsWith("girls.kobold.") && !path.contains("cum")
                ? 0.9F + 0.2F * (0.25F - this.koboldSize()) / 0.25F : 1.0F;
        com.fartypoopy.sexmod.registry.ModSounds.find(path)
                .ifPresent(sound -> this.playSound(sound.get(), volume, pitch));
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
            // 1.21's moveTo(x, y, z) paths with accuracy 1 and stops a block short; 1.12's tryMoveToXYZ reaches the spot
            var path = CompanionEntity.this.getNavigation().createPath(BlockPos.containing(target), 0);
            this.unreachable = path == null || !path.canReach();
            CompanionEntity.this.getNavigation().moveTo(path, modifier);
        }

        /** The last path stops short of the bed (glass, a closed room): it never gets closer than that. */
        private boolean unreachable;

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
            boolean stuck = this.unreachable && self.bedWalkTicks > 20 && self.getNavigation().isDone();
            if (self.bedWalkTicks == 60 || self.bedWalkTicks == 120
                    || (self.kind == CompanionKind.ELLIE && self.bedWalkTicks > 10 && self.getNavigation().isDone())
                    || (self.bedWalkTicks <= 60 && self.getNavigation().isDone())) {
                // original el: a new path every tick until she is within 1 block. The others path at 0, 60 and 120;
                // 1.21 gives no path while she is not on the ground yet (a scene just let go of her), which left her
                // standing until tick 60 or 120, so until then she asks again every tick while she has none
                this.navigate();
            }
            double horizontal = Math.hypot(self.getX() - walk.target().x, self.getZ() - walk.target().z);
            boolean pathEnded = self.bedWalkTicks > 10 && self.getNavigation().isDone();
            double distance = self.position().distanceTo(walk.target());
            boolean arrived = switch (self.kind) {
                case JENNY -> distance < 0.6D; // original ex: o() within 0.6, else ad > 200
                case LUNA -> distance < 0.5D; // original eb: within 0.5, else ak > 200
                case ELLIE -> distance <= 1.0D; // original el: within 1.0 she is put on the spot and sits down
                default -> horizontal < 0.6D || (pathEnded && horizontal < 1.6D);
            };
            // not in the original (which walks into the glass until tick 200): once she has stopped at the end of a
            // path that cannot reach the bed (checked before this tick's new path), she goes to it after a second
            if (arrived || stuck || self.bedWalkTicks > 200) {
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
                            controller, new com.fartypoopy.sexmod.network.TransitionPayload());
                }
            }
        } else if (--this.attachCountdown <= 0) {
            this.attachCountdown = -1;
            this.finishScene(true);
        }
    }

    /** A scene partner: an online player, or a player entity of this level (fake players in tests). */
    static ServerPlayer scenePlayer(ServerLevel level, UUID id) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        return player != null ? player : level.getPlayerByUUID(id) instanceof ServerPlayer local ? local : null;
    }

    private record SceneAnchor(Vec3 position, float yaw) {
    }

    private CompanionScene approachScene;
    private UUID approachPlayer;
    private int approachTicks;
    private Vec3 approachTarget;
    private Vec3 approachHold;
    private float approachYaw;
    private boolean approachDone;

    /** The actions the original starts with the dc approach (bh.b(): ex.ab, e0.Y, eb.ac, ff.a2). */
    private boolean approachesPlayer(CompanionScene scene) {
        return switch (scene) {
            case JENNY_PAYMENT, JENNY_BLOWJOB, JENNY_BOOBJOB, JENNY_DOGGY -> true;
            case JENNY_STRIP -> !this.isNude();
            case BIA_TALK, BIA_HEADPAT, LUNA_PAYMENT, LUNA_HEADPAT, LUNA_TOUCH, LUNA_SITTING,
                    KOBOLD_PAYMENT, KOBOLD_ANAL, KOBOLD_BLOWJOB, KOBOLD_MATING_PRESS -> true;
            default -> false;
        };
    }

    /**
     * Original ex / e0 / eb / ff AI tick while approaching: each tick she moves (target - pos) / (40 - t) and faces the
     * player (I()); Jenny re-aims at 1 block in front of him every tick. After 40 steps (Jenny and the Kobold always 41
     * ticks, Bia and Luna once she is on the spot) she turns to his yaw + 180 and the action starts.
     */
    private void tickApproach(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.approachPlayer);
        if (player == null || !player.isAlive() || player.level() != level || this.isDowned()) {
            this.approachScene = null;
            this.entityData.set(DATA_APPROACH_PARTNER, Optional.empty());
            return;
        }
        this.approachTicks++;
        boolean onSpot = this.kind != CompanionKind.JENNY && this.kind != CompanionKind.KOBOLD
                && this.position().equals(this.approachTarget);
        if (this.approachTicks > 40 || onSpot) {
            CompanionScene scene = this.approachScene;
            this.approachScene = null;
            this.entityData.set(DATA_APPROACH_PARTNER, Optional.empty());
            this.forcedSceneAnchor = this.approachTarget;
            // original em.j: her yaw (the player's head yaw + 180 when she set off) carries the scene; turning while
            // she came used to rotate the whole scene away from the player
            this.forcedSceneAnchorYaw = this.approachYaw;
            // the mouse stays free while she comes (1.12 locked only movement): put the player back exactly in front
            // of her (0.65, Jenny 1.0), facing her, so the scene's body and the camera line up however he turned
            double herYaw = Math.toRadians(this.approachYaw);
            double gap = this.kind == CompanionKind.JENNY ? 1.0D : 0.65D;
            Vec3 front = this.approachTarget.add(-Math.sin(herYaw) * gap, 0.0D, Math.cos(herYaw) * gap);
            player.teleportTo(player.serverLevel(), front.x, front.y, front.z, java.util.Set.of(),
                    Mth.wrapDegrees(this.approachYaw + 180.0F), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            this.approachDone = true;
            try {
                this.startScene(player, scene);
            } finally {
                this.approachDone = false;
                this.forcedSceneAnchor = null; // unused when the action went on to a bed walk instead
            }
            return;
        }
        if (this.kind == CompanionKind.JENNY) {
            double yaw = Math.toRadians(player.getYRot());
            this.approachTarget = player.position().add(-Math.sin(yaw), 0.0D, Math.cos(yaw));
            this.approachYaw = Mth.wrapDegrees(player.getYRot() + 180.0F); // she keeps facing the spot she heads for
        }
        this.getNavigation().stop();
        int left = 40 - this.approachTicks;
        Vec3 next = left <= 0 ? this.approachTarget
                : this.position().add(this.approachTarget.subtract(this.position()).scale(1.0D / left));
        this.setPos(next.x, next.y, next.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.setYRot(this.approachYaw);
        this.yBodyRot = this.approachYaw;
        this.yHeadRot = this.approachYaw;
        // the player's controls are locked while she comes (original d3.a(false))
        if (player.position().distanceToSqr(this.approachHold) > 1.0E-4D) {
            player.teleportTo(this.approachHold.x, this.approachHold.y, this.approachHold.z);
        }
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** Set while a payment / undressing scene hands over to the chosen scene (same spot, partner stays). */
    private boolean chainStart;
    private Vec3 partnerMoveBaseAtStart;
    private boolean chainedNoApproach;

    /** The scenes the original starts through em.a(bl, true, uuid) (packet dc), outside a bed. */
    private boolean startsInFrontOfPlayer(CompanionScene scene) {
        return switch (scene) {
            case JENNY_PAYMENT, JENNY_BLOWJOB, JENNY_BOOBJOB -> true;
            case JENNY_STRIP -> !this.isNude(); // dressing up is a plain state change
            case BIA_TALK, BIA_HEADPAT, LUNA_PAYMENT, LUNA_HEADPAT, LUNA_TOUCH,
                    KOBOLD_PAYMENT, KOBOLD_ANAL, KOBOLD_BLOWJOB, KOBOLD_MATING_PRESS, BEE_SEX -> true;
            case ALLIE_DEEPTHROAT -> true; // ev deepthroat_prepareDone: dc(her, him, false, true), the same placement
            case ELLIE_CARRY -> true; // el.a("Face fuck"): a(true, true, uuid), measured in 1.12 (her 1 ahead, him 0.35 on)
            default -> false;
        };
    }

    /** Ends a scene without touching the world or the partner: state cleared, NoAI / NoGravity restored. */
    private void dropSceneQuietly() {
        this.queuedScene = null;
        this.chainedScene = null;
        this.entityData.set(DATA_SCENE, "");
        this.entityData.set(DATA_SCENE_PHASE, 0);
        this.entityData.set(DATA_SCENE_PARTNER, Optional.empty());
        this.scenePhaseTicks = 0;
        this.sceneAnchorPosition = null;
        this.partnerView = null;
        this.partnerMoved = null;
        this.partnerMoveBase = null;
        this.setNoAi(this.scenePreviousNoAi);
        this.setNoGravity(this.scenePreviousNoGravity);
        this.sceneFastHeld = false;
        this.bedSpotPosition = null;
    }

    private static void liftToAir(net.minecraft.world.entity.Entity entity) {
        Level level = entity.level();
        Vec3 at = entity.position();
        if (entity instanceof Player && !level.getBlockState(BlockPos.containing(at)).isAir()
                && !level.noCollision(entity, entity.getBoundingBox())) {
            // User request (not in the original, which only lifts): stuck in a wall or furniture, first try a free spot
            // beside the scene at the same height, so the player is not lifted through a ceiling onto the roof
            Vec3 beside = freeSpotBeside(entity, at);
            if (beside != null) {
                entity.teleportTo(beside.x, beside.y, beside.z);
                return;
            }
        }
        while ((!level.getBlockState(BlockPos.containing(at)).isAir()
                || (!(entity instanceof Player) && level.getBlockState(BlockPos.containing(at).above()).isSuffocating(level,
                BlockPos.containing(at).above()))) && at.y < level.getMaxBuildHeight()) {
            // girls also need their head clear (the original checked the feet only, and she suffocated in walls)
            at = at.add(0.0D, 1.0D, 0.0D);
        }
        entity.teleportTo(at.x, at.y, at.z);
    }

    /** The nearest place within 2 blocks at the same height where the entity fits, standing on something solid. */
    private static Vec3 freeSpotBeside(net.minecraft.world.entity.Entity entity, Vec3 at) {
        Level level = entity.level();
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (double dy : new double[]{0.0D, Math.ceil(at.y) - at.y}) {
                    Vec3 spot = new Vec3(Math.floor(at.x) + dx + 0.5D, at.y + dy, Math.floor(at.z) + dz + 0.5D);
                    AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(spot);
                    if (!level.noCollision(entity, box)
                            || level.noCollision(entity, box.move(0.0D, -0.5D, 0.0D).setMaxY(box.minY))) {
                        continue; // blocked, or nothing to stand on
                    }
                    double distance = spot.distanceToSqr(at);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = spot;
                    }
                }
            }
        }
        return best;
    }

    private void finishScene(boolean completed) {
        this.pendingFinish = false;
        CompanionScene scene = this.currentScene();
        ServerPlayer controller = null;

        if (this.level() instanceof ServerLevel serverLevel) {
            controller = this.scenePartnerId()
                    .map(serverLevel.getServer().getPlayerList()::getPlayer)
                    .orElse(null);
            // original s$a (ResetGirl): the partner and the girl stay where the scene left them, each lifted to the
            // first air block at or above their feet
            boolean chaining = completed && this.queuedScene != null;
            if (controller != null && scene != null && scene.hasPartner() && controller.level() == this.level() && !chaining) {
                if (completed && this.kind == CompanionKind.GALATH && !this.isPlayerForm()
                        && (scene == CompanionScene.GALATH_COWGIRL || scene == CompanionScene.GALATH_ANAL)) {
                    // original f_.o() / Y() (tamed): after 28 / 30 cum ticks the player is put on top of his block
                    controller.teleportTo(controller.getX(), Math.ceil(controller.getY()) + 1.0D, controller.getZ());
                } else {
                    liftToAir(controller);
                }
            }
            if (!this.isPlayerForm() && !chaining) {
                liftToAir(this);
            }

        }
        if (scene == CompanionScene.GOBLIN_NELSON && this.goblinBehavior != null) {
            this.setGoblinPreggo(false);
        }
        if (scene == CompanionScene.MANGLELIE_THREESOME && this.level() instanceof ServerLevel level) {
            if (completed && controller != null) {
                GalathBindingData.get(level.getServer()).recordCum(controller.getUUID(), level.getGameTime());
            }
            CompanionEntity mother = ManglelieBehavior.find(level, this.familyId());
            if (mother != null && mother.currentScene() == CompanionScene.GALATH_THREESOME) {
                mother.finishScene(completed);
            }
        }
        if (scene == CompanionScene.GALATH_MORNING && completed && controller != null
                && this.level() instanceof ServerLevel level) {
            GalathBindingData.get(level.getServer()).recordCum(controller.getUUID(), level.getGameTime());
        }
        if (completed && this.slimeBehavior != null && !this.isPlayerForm()
                && (scene == CompanionScene.SLIME_BLOWJOB || scene == CompanionScene.SLIME_DOGGY)) {
            this.setSlimeBirth(SlimeBehavior.BIRTH_TICKS); // original: bjcDone / doggyCumDone send "pregnant" 2400
        }
        if (this.slimeBehavior != null && !this.isPlayerForm()) {
            // original s$a.a -> fn.g(): every scene end leaves her calm (horny 0) and dressed (D = 1); without it she
            // caught the player again at once, and after doggy went straight back onto her slime bed
            this.setSlimeHorny(0);
            this.setSlimeState(SlimeBehavior.IDLE);
            this.setOutfit(OutfitMode.DEFAULT);
        }
        CompanionScene next = completed ? this.queuedScene : null;
        CompanionScene after = completed ? this.chainedScene : null;
        this.queuedScene = null;
        this.chainedScene = null;

        this.entityData.set(DATA_SCENE, "");
        this.entityData.set(DATA_SCENE_PHASE, 0);
        this.entityData.set(DATA_SCENE_PARTNER, Optional.empty());
        this.scenePhaseTicks = 0;
        Vec3 oldAnchor = this.sceneAnchorPosition;
        float oldAnchorYaw = this.sceneAnchorYaw;
        this.sceneAnchorPosition = null;
        this.partnerView = null;
        this.partnerMoved = null;
        this.partnerMoveBase = null;
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoAi(this.scenePreviousNoAi);
        this.setNoGravity(this.scenePreviousNoGravity);
        this.attachCountdown = -1;
        this.waitTicks = 0;
        this.sceneFastHeld = false;
        if (next != null && controller != null && controller.isAlive()) {
            if (oldAnchor != null && scene != null && (scene.isPayment() || scene.isClothingToggle())
                    && !next.requiresBed() && this.bedSpotPosition == null) {
                // original U(): the payment / undressing goes straight on into the chosen scene on the same spot
                this.forcedSceneAnchor = oldAnchor;
                this.forcedSceneAnchorYaw = oldAnchorYaw;
                this.chainStart = true;
            }
            // a chained scene never walks up to the player again (ex stripDone -> U() -> r(): straight to the bed)
            this.chainedNoApproach = true;
            this.skipPayment = true;
            this.startScene(controller, next);
            this.skipPayment = false;
            this.chainStart = false;
            this.chainedNoApproach = false;
            if (after != null && this.currentScene() == next) {
                this.queuedScene = after;
            }
        }
        if (this.kind == CompanionKind.ALLIE && !this.isInScene() && !this.isPlayerForm()) {
            this.discard(); // original: she vanishes when her state returns to NULL
        }
        if (this.isPlayerForm() && !this.isInScene()) {
            PlayerForms.onFormSceneEnded(this);
        }
        if (scene == CompanionScene.GALATH_WILD_CORRUPT && completed) {
            if (controller instanceof ServerPlayer winner) {
                this.awardGalathCoin(winner); // original b(fp) at the end of GIVE_COIN
                winner.teleportTo(winner.getX(), Math.ceil(winner.getY()) + 1.0D, winner.getZ());
            }
            if (this.boss != null) {
                this.boss.bar().removeAllPlayers();
            }
            this.discard(); // original: she is gone, only the coin remains
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
        int punch = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PUNCH), bow);
        if (punch > 0) {
            arrow.getPersistentData().putInt("sexmod:punch", punch); // original setKnockbackStrength(punch)
        }
        arrow.setBaseDamage(4.5D); // original g.e(): setDamage(4.5) overwrites the Power bonus
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

    /** Original g.d(): Sweeping Edge on the main-hand weapon hits other creatures near the target for 50/67/75 percent. */
    private void sweep(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemStack weapon = this.equipment.getItem(0);
        var enchantments = serverLevel.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        int level = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE), weapon);
        if (level <= 0) {
            return;
        }
        float factor = level >= 3 ? 0.75F : level == 2 ? 0.67F : 0.5F;
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Player master = this.masterPlayer().orElse(null);
        for (LivingEntity other : serverLevel.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(1.0D, 0.25D, 1.0D))) {
            if (other == this || other == master || other == target || this.isAlliedTo(other)
                    || other instanceof CompanionEntity || this.distanceToSqr(other) >= 9.0D) {
                continue;
            }
            other.knockback(0.4D, Mth.sin(this.getYRot() * ((float) Math.PI / 180.0F)),
                    -Mth.cos(this.getYRot() * ((float) Math.PI / 180.0F)));
            other.hurt(this.damageSources().mobAttack(this), damage * factor);
        }
    }

    private void tickFlight() {
        if (this.noPhysics) {
            return; // breeding guards are held in place by their queen
        }
        if (this.beeBehavior != null) {
            return; // the bee flies by navigation, see BeeBehavior
        }
        if (this.wildBoss()) {
            return; // the wild Galath moves in GalathBoss
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
        if (this.kind == CompanionKind.LUNA) {
            this.entityData.set(DATA_LUNA_ROD, this.equipment.getItem(6).copy());
        }
    }

    @Override
    protected void dropCustomDeathLoot(
            ServerLevel level,
            DamageSource damageSource,
            boolean recentlyHit
    ) {
        // no super call: vanilla Mob would add random drops of the mirrored equipment slots, which the original never had
        // Original g.a (LivingDeathEvent, e2 girls only: Jenny, Ellie, Bia, Luna): one plain item of each filled slot 0-5
        // (count, damage and enchantments are lost), never the rod slot. Kobold and Bee storage is not dropped at all.
        if (this.kind.hasMasterAI()) {
            for (int slot = 0; slot < 6; slot++) {
                ItemStack stack = this.equipment.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(new ItemStack(stack.getItem(), 1));
                }
            }
            this.syncEquipmentSlots();
        }
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
            if (reason.shouldDestroy()) {
                this.finishScene(false);
            } else {
                // unloaded with her chunk, the world closing or a dimension change: the full ending teleports, reads
                // blocks (which can load chunks while they are being saved) and may discard her from inside remove();
                // here the scene is only dropped (it is not saved either, see readAdditionalSaveData)
                this.dropSceneQuietly();
            }
        }

        if (reason.shouldDestroy() && this.kind == CompanionKind.GALATH && this.level() instanceof ServerLevel serverLevel
                && this.familyId() != null) {
            ManglelieBehavior.removeDaughter(serverLevel, this); // she leaves with her mother
        }
        if (reason.shouldDestroy() && this.goblinBehavior != null) {
            this.goblinBehavior.dropItem();
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

        if (this.wildBoss()) {
            if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
                this.tryWildCorrupt(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (this.kind == CompanionKind.GALATH && this.hasMaster()) {
            // original f_.a(player, hand): only her master, and not while she hugs / runs to Manglelie, (de)summons or masturbates
            ManglelieBehavior seeker = this.manglelie();
            if (!this.isMaster(player)
                    || this.action() == CompanionAction.GALATH_HUG_MANG
                    || this.action() == CompanionAction.GALATH_SUMMON
                    || this.action() == CompanionAction.GALATH_DESUMMON
                    || this.action() == CompanionAction.GALATH_MASTURBATE
                    || (seeker != null && seeker.runningToMangle())) {
                return InteractionResult.PASS;
            }
        }
        if (this.kind == CompanionKind.GALATH
                && (player.getOffhandItem().is(com.fartypoopy.sexmod.registry.ModItems.GALATH_COIN.get())
                || player.getMainHandItem().is(com.fartypoopy.sexmod.registry.ModItems.GALATH_COIN.get()))) {
            return InteractionResult.PASS; // original f_.a(player, hand): no menu while the coin is in a hand
        }
        if (heldItem.is(Items.NAME_TAG) && (this.kind == CompanionKind.LUNA || this.kind == CompanionKind.BIA)) {
            return InteractionResult.PASS; // original eb/e0: a name tag renames them through vanilla
        }
        if (heldItem.is(Items.NAME_TAG) && this.kind != CompanionKind.KOBOLD) {
            // original f4 (EntityInteractSpecific): a name tag in either hand names any other girl with the tag's display
            // name (an unnamed tag included), is used up outside creative, and the click opens no menu
            ItemStack mainTag = player.getMainHandItem();
            if (hand == InteractionHand.MAIN_HAND || !mainTag.is(Items.NAME_TAG)) {
                if (!this.level().isClientSide) {
                    this.setCustomName(heldItem.getHoverName());
                    if (this.kind == CompanionKind.GALATH && this.level().getServer() != null) {
                        // original f_.g: a tamed Galath's name is kept for her owner (gy) and given to every re-summon
                        this.masterId().ifPresent(owner -> com.fartypoopy.sexmod.data.GalathBindingData
                                .get(this.level().getServer()).setGalathName(owner, heldItem.getHoverName().getString()));
                    }
                    if (!player.getAbilities().instabuild) {
                        heldItem.shrink(1);
                    }
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
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
                    com.fartypoopy.sexmod.client.ClientHooks.openBeeScreen(this);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            return InteractionResult.PASS; // untamed: nothing (no gifts in the original)
        }
        if (this.slimeBehavior != null) {
            return InteractionResult.PASS; // original Slime: no menu, scenes start when you walk into her
        }
        if (this.kind == CompanionKind.MANGLELIE) {
            // original f8: no menu of her own (checked in 1.12); her scenes are started through her mother Galath
            return InteractionResult.PASS;
        }
        if (this.goblinBehavior != null) {
            if (!this.level().isClientSide && !this.isInScene()) {
                this.goblinBehavior.interact(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (this.koboldBehavior != null) {
            InteractionResult result = this.koboldInteract(player, heldItem);
            if (result != null) {
                return result;
            }
        }
        // Like the original, whatever the player holds, right-click opens the panel.
        if (this.level().isClientSide && this.kind == CompanionKind.GALATH && this.hasMaster()) {
            // original GIRLS_GALATH_HUH: played client-side only (world.playSound without a player), only the clicker hears it
            com.fartypoopy.sexmod.registry.ModSounds.find("girls.galath.huh.huh" + this.getRandom().nextInt(3)).ifPresent(sound ->
                    this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), sound.get(),
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F, false));
        }
        if (this.level().isClientSide) {
            if (!this.isInScene() && this.mayUseSceneActions(player)) {
                com.fartypoopy.sexmod.client.ClientHooks.openActionPanel(this);
                if (this.kind == CompanionKind.KOBOLD && this.isMaster(player)) {
                    com.fartypoopy.sexmod.client.ClientHooks.koboldMasterSound(this);
                }
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
        Component base = this.baseName();
        if (this.kind == CompanionKind.GALATH) {
            // original f_.ab(): with her master around she is called "<name> [<master>]", the bracket part dark purple
            Player master = this.masterId().map(id -> this.level().getPlayerByUUID(id)).orElse(null);
            if (master != null) {
                return base.copy().append(Component.literal(" [" + master.getGameProfile().getName() + "]")
                        .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
            }
        }
        return base;
    }

    private Component baseName() {
        if (this.hasCustomName()) {
            return super.getName();
        }
        if (this.isPlayerForm()) {
            // original ei.c(): a form speaks under her player's name
            Player owner = this.formOwner().map(id -> this.level().getPlayerByUUID(id)).orElse(null);
            return Component.literal(owner != null ? owner.getGameProfile().getName() : "anonymous horny girl");
        }

        if (this.kind == CompanionKind.KOBOLD) {
            return Component.literal(this.koboldName());
        }
        return Component.literal(this.kind.displayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.isInScene()) {
            // scenes are not saved, so neither is the frozen state a scene puts her in (else she reloads unable to move)
            tag.putBoolean("NoAI", this.scenePreviousNoAi);
            if (this.scenePreviousNoGravity) {
                tag.putBoolean("NoGravity", true);
            } else {
                tag.remove("NoGravity");
            }
        }
        tag.putString("FapCraftKind", this.kind.registryPath());
        if (this.kind == CompanionKind.MANGLELIE && this.manglelieBehavior != null) {
            tag.putString("sexmod:mommy", this.familyId() == null ? "" : this.familyId().toString());
            tag.putBoolean("sexmod:iswild", this.manglelieBehavior.wild());
        }
        tag.putString("girlID", this.girlId.toString());
        if (!this.kind.hasClothingToggle()) {
            tag.putString("FapCraftOutfit", this.outfit().serializedName());
        }
        tag.putInt("FapCraftVariant", this.appearanceVariant());
        tag.putString("FapCraftGenes", this.entityData.get(DATA_KOBOLD_GENES));
        tag.putFloat("FapCraftScale", this.appearanceScale());
        this.homePos().ifPresent(home -> {
            tag.putDouble("homeX", home.getX());
            tag.putDouble("homeY", home.getY());
            tag.putDouble("homeZ", home.getZ());
        });
        tag.put("FapCraftEquipment", SlotNbt.save(this.equipment, this.registryAccess()));
        tag.put("FapCraftStorage", SlotNbt.save(this.storage, this.registryAccess()));
        tag.putBoolean("FapCraftGalathCoinSummon", this.summonedByGalathCoin);
        if (!this.usesStaticModelCode() && !this.isPlayerForm()) {
            tag.putString("sexmod:customModel", this.modelCode()); // original em.writeEntityToNBT (X())
        }
        this.formOwner().ifPresent(owner -> tag.putString("owner", owner.toString())); // original ei.writeEntityToNBT
        if (this.kind == CompanionKind.GALATH) {
            // original f_.writeEntityToNBT: a tamed Galath keeps her master
            this.masterId().ifPresent(owner -> tag.putString("sexmod:master", owner.toString()));
        }
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
        if (this.goblinBehavior != null) {
            tag.putString("model", this.goblinModel());
            tag.putBoolean("isQueen", this.goblinQueen());
            if (this.goblinQueen()) {
                tag.putBoolean("preggo", this.isPreggo());
                tag.putLong("impregnationTick", this.goblinBehavior.impregnationTick);
                for (int i = 0; i < this.goblinBehavior.guards.size(); i++) {
                    tag.putString("guard" + i, this.goblinBehavior.guards.get(i).toString());
                }
            }
            tag.putBoolean("isTamed", this.goblinTamed());
            if (this.goblinBehavior.queenId != null) {
                tag.putUUID("queen", this.goblinBehavior.queenId); // original e3 saves its queen: a reloaded robber vanishes
            }
            tag.putInt("robTicks", this.goblinBehavior.robTicks);
            if (this.goblinBehavior.thronePos != null) {
                tag.putFloat("throneRot", this.goblinBehavior.throneRot);
                tag.putDouble("thronePosX", this.goblinBehavior.thronePos.x);
                tag.putDouble("thronePosY", this.goblinBehavior.thronePos.y);
                tag.putDouble("thronePosZ", this.goblinBehavior.thronePos.z);
            }
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
                this.setBodyColor(com.fartypoopy.sexmod.tribe.KoboldColor.byName(tag.getString("bodyColor")));
            }
            if (tag.contains("eyeColor", Tag.TAG_STRING)) {
                this.setEyeColor(com.fartypoopy.sexmod.tribe.KoboldColor.byName(tag.getString("eyeColor")));
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

        if (this.kind == CompanionKind.MANGLELIE && this.manglelieBehavior != null) {
            String mommy = tag.getString("sexmod:mommy");
            if (!mommy.isEmpty()) {
                try {
                    this.manglelieBehavior.setPendingMother(UUID.fromString(mommy));
                } catch (IllegalArgumentException ignored) {
                    // a corrupt id leaves her wild
                }
            }
            this.manglelieBehavior.setWild(tag.getBoolean("sexmod:iswild"));
        }
        if (this.goblinBehavior != null) {
            this.setGoblinQueen(tag.getBoolean("isQueen"));
            String saved = tag.getString("model");
            int[] fields = GoblinModel.parse(saved);
            // original legacy fix: old goblins with accessory values above 7 get a new look
            if (fields.length >= 10 && fields[3] <= 7 && fields[4] <= 7) {
                this.setGoblinModel(saved);
            }
            this.setGoblinTamed(tag.getBoolean("isTamed"));
            if (tag.hasUUID("queen")) {
                this.goblinBehavior.queenId = tag.getUUID("queen");
            }
            this.setGoblinPreggo(tag.getBoolean("preggo"));
            if (tag.contains("impregnationTick", Tag.TAG_ANY_NUMERIC)) {
                this.goblinBehavior.impregnationTick = tag.getLong("impregnationTick");
            }
            this.goblinBehavior.guards.clear();
            for (int i = 0; tag.contains("guard" + i, Tag.TAG_STRING); i++) {
                try {
                    this.goblinBehavior.guards.add(UUID.fromString(tag.getString("guard" + i)));
                } catch (IllegalArgumentException ignored) {
                    break;
                }
            }
            if (tag.contains("robTicks", Tag.TAG_ANY_NUMERIC)) {
                this.goblinBehavior.robTicks = tag.getInt("robTicks");
            }
            if (tag.contains("thronePosX", Tag.TAG_ANY_NUMERIC)) {
                this.goblinBehavior.thronePos = new Vec3(tag.getDouble("thronePosX"), tag.getDouble("thronePosY"), tag.getDouble("thronePosZ"));
                this.goblinBehavior.throneRot = tag.getFloat("throneRot");
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
        if (tag.contains("FapCraftGenes", Tag.TAG_STRING) && tag.getString("FapCraftGenes").split("-").length == 8) {
            this.entityData.set(DATA_KOBOLD_GENES, tag.getString("FapCraftGenes"));
        }
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
        } else {
            // original e2.readEntityFromNBT: deserializeNBT of the missing "inventory" compound empties the handler, so a
            // /summon-ed girl (NBT always read) has no sword and bow; a naturally spawned or egg-spawned one keeps them
            this.equipment.clearContent();
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
        if (!this.usesStaticModelCode()) {
            this.entityData.set(DATA_MODEL_CODE, tag.getString("sexmod:customModel"));
        }
        if (tag.contains("owner", Tag.TAG_STRING)) {
            try {
                this.setFormOwner(UUID.fromString(tag.getString("owner"))); // original ei.readEntityFromNBT
            } catch (IllegalArgumentException ignored) {
                // not a form
            }
        }
        if (this.kind == CompanionKind.GALATH && tag.contains("sexmod:master", Tag.TAG_STRING)) {
            try {
                UUID owner = UUID.fromString(tag.getString("sexmod:master"));
                this.setMaster(owner);
                // original f_.readEntityFromNBT: the owner's stored name (gy) is applied again
                if (this.level().getServer() != null) {
                    GalathBindingData.get(this.level().getServer()).galathName(owner)
                            .ifPresent(name -> this.setCustomName(Component.literal(name)));
                }
            } catch (IllegalArgumentException ignored) {
                // a corrupt master stays unset, as a wild Galath
            }
        }
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
        if (this.kind == CompanionKind.MANGLELIE) {
            // original f8 second controller: the angry face while she has an arrow target
            controllers.add(new AnimationController<CompanionEntity>(this, "face", 0, state -> this.mangTargetId() == -1
                    ? software.bernie.geckolib.animation.PlayState.STOP
                    : state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.manglelie.angry_face"))));
        }
        String eyes = switch (this.kind) {
            case JENNY -> "animation.jenny.fhappy";
            case ELLIE -> "animation.ellie.eyes";
            case BIA -> "animation.bia.fhappy";
            case SLIME -> "animation.slime.fhappy";
            case LUNA -> "animation.cat.blink";
            case GALATH -> "animation.galath.blink";
            case GOBLIN -> "animation.goblin.blink";
            case KOBOLD -> "animation.kobold.blink";
            default -> null; // Allie's eyes track never plays anything (e5 even names a missing bia.blink); Bee, Manglelie
        };
        if (eyes != null) {
            String none = eyes.substring(0, eyes.lastIndexOf('.')) + ".null";
            // original "eyes" controller (created with 10, but em.a() zeroes it on every call): the blink track only in
            // the states each class allows
            controllers.add(new AnimationController<CompanionEntity>(this, "eyes", 0, state -> {
                if (this.kind == CompanionKind.GALATH && !this.isPlayerForm()) {
                    // f_: while the state has autoBlink (and is not GALATH_DE_SUMMON), else STOP
                    return blinkingGalathAnimation(com.fartypoopy.sexmod.client.ClientHooks.playingAnimation(this))
                            ? state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop(eyes))
                            : software.bernie.geckolib.animation.PlayState.STOP;
                }
                boolean blink = this.kind == CompanionKind.SLIME
                        ? this.slimeBlinks() // fn / ec: only in non-NULL states with autoBlink
                        : this.inNullState(); // the others: only in the NULL state
                return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop(blink ? eyes : none));
            }));
        }
        if (this.kind == CompanionKind.ALLIE) {
            // original ev / e5 "movement" controller (created with 5, zeroed by em.a()): the tail wags all the time
            controllers.add(new AnimationController<CompanionEntity>(this, "movement", 0, state -> {
                if (this.isPlayerForm()) {
                    // e5: the tail wags faster the more she moves, min(1 + 4 * (|dx| + |dy| + |dz|), 4)
                    double moved = Math.abs(this.getX() - this.xo) + Math.abs(this.getY() - this.yo)
                            + Math.abs(this.getZ() - this.zo);
                    state.getController().setAnimationSpeed(Math.min(1.0D + 4.0D * moved, 4.0D));
                }
                return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.allie.tail"));
            }));
        }
        controllers.add(new AnimationController<CompanionEntity>(this, "main", 0, this::animationPredicate)
                .setSoundKeyframeHandler(event -> {
                    var current = event.getController().getCurrentAnimation();
                    this.lastKeyframeAnimation = current == null ? null : current.animation().name();
                    this.lastKeyframeTick = event.getKeyframeData().getStartTick();
                    com.fartypoopy.sexmod.client.KeyframeEvents.handle(this, event.getKeyframeData().getSound());
                }));
    }

    private <T extends CompanionEntity> software.bernie.geckolib.animation.PlayState animationPredicate(
            AnimationState<T> state
    ) {
        if (this.level() != null && this.level().isClientSide) {
            this.trackMainAnimation(state.getController());
        }
        software.bernie.geckolib.animation.PlayState result = this.selectAnimation(state);
        // Original em.a(): every play call sets transitionLengthTicks to 0, so a new animation snaps to its first
        // frame at once; cv then puts the state's transitionTick back after the controllers ran, which only decides how
        // long that first frame is held (GeckoLib leaves the transition once its tick passes the length). Movement
        // animations ran on em.E in the NULL state (transitionTick 0) and switch at once. CompanionModel restores the
        // hold after processing, like cv.
        software.bernie.geckolib.animation.RawAnimation picked = state.getController().getCurrentRawAnimation();
        if (this.restartHold && state.getController().getAnimationState()
                != software.bernie.geckolib.animation.AnimationController.State.TRANSITIONING) {
            this.restartHold = false;
        }
        this.mainHoldTicks = this.restartHold || picked == null || picked.getAnimationStages().isEmpty() ? 0
                : TransitionTicks.forState(picked.getAnimationStages().get(0).animationName());
        state.getController().transitionLength(0);
        return result;
    }

    private <T extends CompanionEntity> software.bernie.geckolib.animation.PlayState selectAnimation(
            AnimationState<T> state
    ) {
        CompanionScene.Phase sceneAnimation = this.currentSceneAnimation();
        // original f_ predicate case RUN: animation.galath.running at controller speed 0.7
        boolean galathSeekRun = sceneAnimation == null && this.kind == CompanionKind.GALATH
                && this.entityData.get(DATA_SPEED_CLASS) == 3;
        state.getController().setAnimationSpeed(galathSeekRun ? 0.7D : 1.0D);
        if (galathSeekRun) {
            return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin()
                    .thenLoop("animation.galath.running"));
        }
        if (sceneAnimation != null && this.currentScene() == CompanionScene.GALATH_THREESOME) {
            return state.setAndContinue(this.threesomeAnimation());
        }
        if (sceneAnimation != null) {
            int serial = this.entityData.get(DATA_PHASE_SERIAL);
            if (serial != this.seenPhaseSerial) {
                this.seenPhaseSerial = serial;
                state.getController().forceAnimationReset(); // a Ready restart of the same animation
                this.restartHold = true; // original N() only rewinds tickOffset: no first-frame hold
            }
            return state.setAndContinue(sceneAnimation.rawAnimation(this.isPlayerForm()));
        }
        if (this.isPlayerForm()) {
            return state.setAndContinue(this.formAnimation(state.getController()));
        }

        if (this.kind == CompanionKind.GALATH && this.bossState() != GalathBoss.S_NONE) {
            var raw = software.bernie.geckolib.animation.RawAnimation.begin();
            if (this.bossState() == GalathBoss.S_KO_FLY) {
                state.getController().setAnimationSpeed(1.5D); // original f_ KNOCK_OUT_FLY
            }
            return state.setAndContinue(switch (this.bossState()) {
                case GalathBoss.S_SUMMON -> raw.thenLoop(this.bossFlag(GalathBoss.F_MIRROR)
                        ? "animation.galath.summon_skeletonMirrored" : "animation.galath.summon_skeleton");
                case GalathBoss.S_SWORD -> raw.thenLoop("animation.galath.attack");
                case GalathBoss.S_KO_FLY -> raw.thenLoop("animation.galath.knockout_air");
                case GalathBoss.S_KO_GROUND -> raw.thenLoop("animation.galath.knocked_out");
                case GalathBoss.S_KO_STAND -> raw.thenPlayAndHold("animation.galath.knocked_out_stand_up");
                case GalathBoss.S_PREPARE -> raw.thenLoop("animation.galath.rape_prepare");
                case GalathBoss.S_CHARGE -> raw.thenLoop("animation.galath.rape_charge");
                default -> raw.thenLoop("animation.galath.idle_flying");
            });
        }
        if (this.beeBehavior != null) {
            return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop(
                    this.action() == CompanionAction.THROW_PEARL ? "animation.bee.throw_pearl"
                            : this.hasBeeChest() ? "animation.bee.idle_has_chest" : "animation.bee.idle"));
        }
        if (this.slimeBehavior != null) {
            // original fn: the NULL-state jumps / idle loop (em.a(S.a, true)); undress and go-on-bed hold
            return state.setAndContinue(switch (this.slimeState()) {
                case SlimeBehavior.JUMP_START -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.slime.jumpstart");
                case SlimeBehavior.JUMP_AIR -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.slime.jumpair");
                case SlimeBehavior.JUMP_END -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.slime.jumpend");
                case SlimeBehavior.UNDRESS -> software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.slime.undress");
                case SlimeBehavior.GO_ON_BED -> software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.slime.doggygoonbed");
                case SlimeBehavior.WAIT -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.slime.doggywait");
                default -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.slime.idle");
            });
        }
        if (this.kind == CompanionKind.LUNA && this.fishState() != LunaFishing.NONE) {
            var raw = software.bernie.geckolib.animation.RawAnimation.begin();
            return state.setAndContinue(switch (this.fishState()) {
                case LunaFishing.START -> raw.thenPlayAndHold("animation.cat.start_fishing");
                case LunaFishing.IDLE -> raw.thenLoop("animation.cat.idle_fishing");
                case LunaFishing.EAT -> raw.thenPlayAndHold("animation.cat.eat_fishing");
                default -> raw.thenPlayAndHold("animation.cat.throw_away");
            });
        }
        if (this.goblinBehavior != null) {
            return state.setAndContinue(this.goblinAnimation());
        }
        if (this.kind == CompanionKind.GALATH && this.action() == CompanionAction.GALATH_SUMMON) {
            return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.galath.summon"));
        }
        if (this.kind == CompanionKind.GALATH && this.action() == CompanionAction.GALATH_DESUMMON) {
            return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold(
                    this.onGround() ? "animation.galath.desummon_standing" : "animation.galath.desummon"));
        }
        if (this.kind == CompanionKind.GALATH && this.getFirstPassenger() instanceof Player) {
            return state.setAndContinue(this.entityData.get(DATA_GALATH_BOOST) > 0
                    ? software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.galath.boost")
                    : software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.galath.controlled_flight"));
        }
        if (this.kind == CompanionKind.GALATH && this.action() == CompanionAction.GALATH_MASTURBATE) {
            return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.galath.masterbate"));
        }
        if (this.kind == CompanionKind.GALATH && this.action() == CompanionAction.GALATH_HUG_MANG) {
            return state.setAndContinue(software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.galath.hug_mang"));
        }
        if (this.kind == CompanionKind.MANGLELIE) {
            // original f8 predicate: running / sitting on her mother's head, else walk or idle
            return state.setAndContinue(switch (this.action()) {
                case MANG_RUN -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.manglelie.running");
                case MANG_RIDE -> software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.manglelie.sit_on_galath");
                default -> this.isWalkingVisibly()
                        ? software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.manglelie.walk")
                        : software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.manglelie.idle");
            });
        }
        if (this.koboldBehavior != null) {
            software.bernie.geckolib.animation.RawAnimation kobold = this.koboldAnimation();
            if (this.koboldState() == com.fartypoopy.sexmod.tribe.KoboldBehavior.ST_NONE && !this.isPassenger()) {
                state.getController().setAnimationSpeed(this.koboldWalkSpeed);
            }
            return state.setAndContinue(kobold);
        }
        if (this.kind.hasMasterAI()) {
            CompanionAction action = this.action();
            if (action != CompanionAction.NONE && !(action == CompanionAction.ATTACK && this.attackFinished)) {
                return state.setAndContinue(this.kind.stateAnimation(
                        action.animationSuffix(this.attackCycle)));
            }
            if (this.isWalkingVisibly() && this.kind == CompanionKind.MANGLELIE && this.entityData.get(DATA_SCARED)) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("animation.manglelie.scared_run"));
            }
            return state.setAndContinue(this.kind.stateAnimation(this.masterMovement()));
        }

        if (this.kind == CompanionKind.GALATH) {
            // original f_ movement controller in NULL: in the air controlled_flight, standing still idle, else run / walk
            if (!this.onGround()) {
                return state.setAndContinue(this.kind.stateAnimation("controlled_flight"));
            }
            if (!this.movedHorizontally()) {
                return state.setAndContinue(this.kind.idleAnimation());
            }
            return state.setAndContinue(this.kind.stateAnimation(this.entityData.get(DATA_SPEED_CLASS) == 2 ? "run" : "walk"));
        }
        if (this.isWalkingVisibly()) {
            return state.setAndContinue(this.kind.walkAnimation());
        }

        return state.setAndContinue(this.kind.idleAnimation());
    }

    /** Original movement test: |x - prevX| + |z - prevZ| > 0. */
    private boolean movedHorizontally() {
        return Math.abs(this.xo - this.getX()) + Math.abs(this.zo - this.getZ()) > 0.0D;
    }

    /**
     * Original ex / el / e0 / eb "movement" controller in the NULL state. Jenny / Bia: sitting while riding, walk /
     * fastwalk / run by the follow speed when moving, else idle. Ellie: the same without sitting, crouched (crouchwalk /
     * crouchidle) when the block two above her feet is not air. Luna: sitting while riding; moving on the ground walk
     * (path end nearer than 3) or run, moving in the air fly; else idle, or idle2 after an idleDone roll of 1 in 10.
     */
    private String masterMovement() {
        if (this.kind != CompanionKind.ELLIE && this.isPassenger()) {
            return "sit";
        }
        boolean moving = this.movedHorizontally();
        if (this.kind == CompanionKind.ELLIE) {
            boolean crouch = this.level().getBlockState(this.blockPosition().above(2)).getBlock()
                    != net.minecraft.world.level.block.Blocks.AIR;
            if (!moving) {
                return crouch ? "crouchidle" : "idle";
            }
            if (crouch) {
                return "crouchwalk";
            }
        }
        if (this.kind == CompanionKind.LUNA) {
            if (!moving) {
                return this.lunaIdle2 ? "idle2" : "idle";
            }
            if (!this.onGround() || Math.abs(this.yo - this.getY()) >= 0.1D) {
                return "fly";
            }
            return this.entityData.get(DATA_SPEED_CLASS) == 2 ? "run" : "walk";
        }
        if (!moving) {
            return "idle";
        }
        return switch (this.entityData.get(DATA_SPEED_CLASS)) {
            case 2 -> "run";
            case 1 -> "fastwalk";
            default -> "walk";
        };
    }

    /** Client only: the Kobold's movement animation speed (original ff: E.setAnimationSpeed while walking). */
    private double koboldWalkSpeed = 1.0D;

    /** Client only: the last phase-restart serial the animation controller followed. */
    private int seenPhaseSerial;

    /** Client only: how long the main controller holds a new animation's first frame (original cv: state transitionTick). */
    private int mainHoldTicks;

    /** Client only: the running restart came from N() (tickOffset rewind), which holds nothing. */
    private boolean restartHold;

    /** The player she is walking up to before a scene starts (client lock), if any. */
    public Optional<UUID> approachPartnerId() {
        return this.entityData.get(DATA_APPROACH_PARTNER);
    }

    public int mainHoldTicks() {
        return this.mainHoldTicks;
    }

    /** Client: the last sound keyframe the main controller fired (its animation and start tick). */
    private String lastKeyframeAnimation;
    private double lastKeyframeTick = -1.0D;

    /**
     * In the original the "...Done" keyframe at the end of an intro is what moved the state on, so it always ran. Here
     * the server moves the phase on by ticks, and the animation lags by its held first frame, so the new phase can
     * replace it just before its last keyframes (e.g. touch_boobs_introDone, which shows the scene HUD). When the phase
     * changes, the unplayed keyframes of a one-shot animation within that lag of its end are fired now.
     */
    private void flushKeyframeTail() {
        var manager = this.getAnimatableInstanceCache().getManagerForId(this.getId());
        var controller = manager == null ? null : manager.getAnimationControllers().get("main");
        var current = controller == null ? null : controller.getCurrentAnimation();
        if (current != null) {
            this.flushKeyframeTail(current);
        }
    }

    private void flushKeyframeTail(software.bernie.geckolib.animation.AnimationProcessor.QueuedAnimation queued) {
        if (queued.loopType() == software.bernie.geckolib.animation.Animation.LoopType.LOOP) {
            return;
        }
        software.bernie.geckolib.animation.Animation animation = queued.animation();
        double fired = animation.name().equals(this.lastKeyframeAnimation) ? this.lastKeyframeTick : -1.0D;
        double from = animation.length() - (this.mainHoldTicks + 4);
        for (var keyframe : animation.keyFrames().sounds()) {
            if (keyframe.getStartTick() > fired && keyframe.getStartTick() >= from) {
                this.lastKeyframeAnimation = animation.name();
                this.lastKeyframeTick = keyframe.getStartTick();
                com.fartypoopy.sexmod.client.KeyframeEvents.handle(this, keyframe.getSound());
            }
        }
    }

    /** Client: the scene animation the synced scene / phase last pointed at, and whether it was a one-shot. */
    private String seenPhaseAnimation;
    private boolean seenPhaseLoop;

    /**
     * Client, on every scene / phase change: the unplayed keyframes at the end of the phase that just ended. The server
     * ends a phase on whole ticks, often a fraction of a tick before its last keyframe (goblin paizuri_startDone at
     * 98.3 of 98, neslon_introDone 38.3 of 38, Jenny doggystartDone, Kobold blowjobStartDone: the events that show the
     * scene HUD), and the controller's own view of "current" can already be the next animation. The keyframes come
     * from the baked animation file itself, so this does not depend on what the controller played.
     */
    private void flushPreviousPhaseTail() {
        String previous = this.seenPhaseAnimation;
        boolean previousLoop = this.seenPhaseLoop;
        CompanionScene.Phase now = this.currentSceneAnimation();
        this.seenPhaseAnimation = now == null ? null : now.animation();
        this.seenPhaseLoop = now != null && now.loop();
        if (previous == null || previousLoop || previous.equals(this.seenPhaseAnimation)) {
            return;
        }
        software.bernie.geckolib.animation.Animation animation =
                com.fartypoopy.sexmod.client.ClientHooks.bakedAnimation(this.kind, previous);
        if (animation == null) {
            return;
        }
        double fired = animation.name().equals(this.lastKeyframeAnimation) ? this.lastKeyframeTick : -1.0D;
        double from = animation.length() - 4.0D;
        for (var keyframe : animation.keyFrames().sounds()) {
            if (keyframe.getStartTick() > fired && keyframe.getStartTick() >= from) {
                this.lastKeyframeAnimation = animation.name();
                this.lastKeyframeTick = keyframe.getStartTick();
                com.fartypoopy.sexmod.client.KeyframeEvents.handle(this, keyframe.getSound());
            }
        }
    }

    /** Client: the main controller's animation as last seen by the predicate (to flush its tail when it is replaced). */
    private software.bernie.geckolib.animation.AnimationProcessor.QueuedAnimation trackedAnimation;

    private void trackMainAnimation(software.bernie.geckolib.animation.AnimationController<?> controller) {
        var current = controller.getCurrentAnimation();
        if (current != this.trackedAnimation) {
            if (this.trackedAnimation != null) {
                this.flushKeyframeTail(this.trackedAnimation); // the phase moved on before its last keyframes played
            }
            this.trackedAnimation = current;
        }
    }

    /** Client only (original eb.ad): idleDone sets it 1 time in 10, idle2Done clears it. */
    public boolean lunaIdle2;

    /** The original's fp.NULL state: no scene, no action and no class-specific state running. */
    private boolean inNullState() {
        if (this.isInScene() || this.action() != CompanionAction.NONE) {
            return false;
        }
        if (this.isPlayerForm()) {
            return this.formWait() == 0;
        }
        return switch (this.kind) {
            case GOBLIN -> this.goblinState() == GoblinBehavior.NULL;
            case KOBOLD -> this.koboldState() == com.fartypoopy.sexmod.tribe.KoboldBehavior.ST_NONE;
            case LUNA -> this.fishState() == LunaFishing.NONE;
            default -> true;
        };
    }

    /** Original fn / ec eyes: fhappy in the non-NULL states with autoBlink (WAITDOGGY, UNDRESS; the form also sit,
     * ride, attack, bow, dress). */
    private boolean slimeBlinks() {
        if (this.isPlayerForm()) {
            String playing = com.fartypoopy.sexmod.client.ClientHooks.playingAnimation(this);
            return java.util.Set.of("animation.slime.attack", "animation.slime.bowcharge", "animation.slime.doggywait",
                    "animation.slime.dress", "animation.slime.ride", "animation.slime.sit", "animation.slime.undress")
                    .contains(playing) || playing.startsWith("animation.slime.attack");
        }
        return this.slimeState() == SlimeBehavior.WAIT || this.slimeState() == SlimeBehavior.UNDRESS;
    }

    /** Original f_ eyes: the states with autoBlink (NULL movement, RUN, FLY, SIT, RIDE, CONTROLLED_FLIGHT, PUSSY_LICKING,
     * MASTERBATE_SITTING, MORNING_BLOWJOB_SLOW / FAST), never in GALATH_DE_SUMMON. */
    private static boolean blinkingGalathAnimation(String playing) {
        return switch (playing) {
            case "animation.galath.idle", "animation.galath.walk", "animation.galath.run", "animation.galath.running",
                    "animation.galath.controlled_flight", "animation.galath.idle_flying", "animation.galath.sit",
                    "animation.galath.pussy_licking", "animation.galath.pussy_licking_back",
                    "animation.galath.pussy_licking_forward", "animation.galath.masterbating_sitting",
                    "animation.shared.bed_back", "animation.shared.bed_soft" -> true;
            default -> false;
        };
    }

    private boolean formFlyVariant;
    private boolean formWasFlying;

    /**
     * Original player-form predicates (es, ee, eg, ec, e9, e5, ed, e7, eq, er): an action (strip, attack, bow) plays over
     * everything; else her player's movement: sitting while riding, flying in the air ("fly" / "fly2", the variant
     * flips on every landing), running when sprinting, walking forwards or backwards, idling; Ellie and Galath crouch.
     */
    private RawAnimation formAnimation(software.bernie.geckolib.animation.AnimationController<?> controller) {
        controller.setAnimationSpeed(1.0D);
        if (this.kind == CompanionKind.GOBLIN && this.goblinState() != GoblinBehavior.NULL) {
            return this.goblinAnimation(); // original eq: PICK_UP, SHOULDER_IDLE, START_THROWING, THROWN, STAND_UP
        }
        CompanionAction action = this.action();
        if (action == CompanionAction.STRIP) {
            return RawAnimation.begin().thenPlayAndHold(this.kind.animationPrefix() + "strip");
        }
        if (action != CompanionAction.NONE && !(action == CompanionAction.ATTACK && this.attackFinished)) {
            return this.kind.stateAnimation(action.animationSuffix(this.attackCycle));
        }
        int wait = this.formWait();
        String waitAnims = this.formWaitAnims();
        if (wait != 0 && !waitAnims.isEmpty()) {
            // original STARTDOGGY / WAITDOGGY (Jenny, Slime), SITDOWN / SITDOWNIDLE (Ellie, Bia doggy),
            // ANAL_PREPARE / ANAL_WAIT (Bia anal), WAIT_CAT (Luna)
            String[] names = waitAnims.split("\\|", 2);
            return wait == 1 ? RawAnimation.begin().thenPlayAndHold(this.kind.animationPrefix() + names[0])
                    : this.kind.stateAnimation(names[1]);
        }
        Player owner = this.formOwner().map(this.level()::getPlayerByUUID).orElse(null);
        if (this.kind == CompanionKind.BEE || owner == null) {
            return this.kind.stateAnimation("idle");
        }
        if (this.kind == CompanionKind.ALLIE) {
            return this.kind.stateAnimation("tail");
        }
        if (owner.isPassenger()) {
            return this.kind.stateAnimation(this.kind == CompanionKind.ELLIE ? "ride" : "sit");
        }
        if (!owner.onGround()) {
            this.formWasFlying = true;
            if (this.kind == CompanionKind.GALATH) {
                return this.kind.stateAnimation("controlled_flight");
            }
            return this.kind.stateAnimation(this.formFlyVariant ? "fly2" : "fly");
        }
        if (this.formWasFlying) {
            this.formWasFlying = false;
            this.formFlyVariant = !this.formFlyVariant;
        }
        boolean crouch = (this.kind == CompanionKind.ELLIE || this.kind == CompanionKind.GALATH) && owner.isCrouching();
        double dx = owner.getX() - owner.xo;
        double dz = owner.getZ() - owner.zo;
        if (Math.abs(dx) + Math.abs(dz) <= 0.0D) {
            return this.kind.stateAnimation(crouch ? "crouchidle" : "idle");
        }
        double yaw = Math.toRadians(owner.getYRot());
        double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
        double[] speeds = switch (this.kind) {
            case ELLIE -> new double[] {1.5D, 2.0D, 1.5D};
            case BIA -> new double[] {1.2D, 1.2D, 1.2D};
            case SLIME -> new double[] {1.0D, 1.0D, 1.0D};
            case LUNA -> new double[] {1.5D, 2.0D, 2.0D};
            case KOBOLD -> new double[] {1.2D, 2.0D, 1.75D};
            case GOBLIN -> new double[] {1.2D, 2.0D, 1.5D};
            case GALATH -> new double[] {1.5D, 2.0D, 1.5D};
            default -> new double[] {1.2D, 1.5D, 1.2D};
        };
        if (owner.isSprinting()) {
            controller.setAnimationSpeed(speeds[0]);
            return this.kind.stateAnimation(crouch ? "crouchwalk" : this.kind == CompanionKind.GOBLIN ? "running" : "run");
        }
        boolean walkName = this.kind == CompanionKind.SLIME || this.kind == CompanionKind.KOBOLD
                || this.kind == CompanionKind.GOBLIN || this.kind == CompanionKind.GALATH;
        if (forward >= -0.1D) {
            controller.setAnimationSpeed(speeds[1]);
            return this.kind.stateAnimation(crouch ? "crouchwalk" : walkName ? "walk" : "fastwalk");
        }
        controller.setAnimationSpeed(speeds[2]);
        return this.kind.stateAnimation(crouch ? "crouchwalk" : "backwards_walk");
    }

    private software.bernie.geckolib.animation.RawAnimation goblinAnimation() {
        var raw = software.bernie.geckolib.animation.RawAnimation.begin();
        String person = this.level().isClientSide && com.fartypoopy.sexmod.client.ClientHooks.isCarrierFirstPerson(this)
                ? "_1person" : "_3person";
        return switch (this.goblinState()) {
            case GoblinBehavior.RUN -> raw.thenLoop(this.onGround() ? "animation.goblin.running" : "animation.goblin.fly");
            case GoblinBehavior.CATCH -> raw.thenPlayAndHold("animation.goblin.catch" + person);
            case GoblinBehavior.CATCH_BJ -> raw.thenPlayAndHold("animation.goblin.catch" + person + "Bj");
            case GoblinBehavior.CATCH_BJ_IDLE -> raw.thenLoop("animation.goblin.catch" + person + "Bj_idle");
            case GoblinBehavior.PICK_UP -> raw.thenPlayAndHold("animation.goblin.pick_up" + person);
            case GoblinBehavior.SHOULDER -> raw.thenLoop("animation.goblin.shoulder_idle");
            case GoblinBehavior.THROWING -> raw.thenPlayAndHold("animation.goblin.throw" + person);
            case GoblinBehavior.THROWN -> raw.thenLoop("animation.goblin.thrown");
            case GoblinBehavior.STAND_UP -> raw.thenPlayAndHold("animation.goblin.stand_up");
            case GoblinBehavior.SIT -> raw.thenLoop("animation.goblin.sit");
            case GoblinBehavior.AWAIT_PICK_UP -> raw.thenLoop("animation.goblin.await_pick_up");
            case GoblinBehavior.JUMP -> raw.thenPlayAndHold("animation.goblin.jump_1");
            case GoblinBehavior.JUMP_A -> raw.thenPlayAndHold("animation.goblin.jump_2");
            case GoblinBehavior.JUMP_B -> raw.thenPlayAndHold("animation.goblin.jump_3");
            case GoblinBehavior.BREED_A -> switch (this.goblinPhase()) {
                case 0 -> raw.thenPlayAndHold("animation.goblin.breeding_intro_2");
                case 3 -> raw.thenPlayAndHold("animation.goblin.breeding_cum_2");
                default -> raw.thenLoop("animation.goblin.breeding_2");
            };
            case GoblinBehavior.BREED_B -> switch (this.goblinPhase()) {
                case 0 -> raw.thenPlayAndHold("animation.goblin.breeding_intro_3");
                case 1 -> raw.thenLoop("animation.goblin.breeding_slow_3");
                case 2 -> raw.thenLoop("animation.goblin.breeding_fast_3");
                default -> raw.thenPlayAndHold("animation.goblin.breeding_cum_3");
            };
            default -> this.isWalkingVisibly()
                    ? raw.thenLoop(this.onGround() && Math.abs(this.getY() - this.yo) < 0.1D ? "animation.goblin.walk" : "animation.goblin.fly")
                    : raw.thenLoop("animation.goblin.idle");
        };
    }

    /** Original ff animation predicate outside scenes: attack / sleep / work states, else idle, walk, run (armed when attacked). */
    private software.bernie.geckolib.animation.RawAnimation koboldAnimation() {
        return switch (this.koboldState()) {
            case com.fartypoopy.sexmod.tribe.KoboldBehavior.ST_ATTACK ->
                    software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.kobold.attack");
            case com.fartypoopy.sexmod.tribe.KoboldBehavior.ST_SLEEP -> this.kind.stateAnimation("sleep");
            case com.fartypoopy.sexmod.tribe.KoboldBehavior.ST_WORK -> this.kind.stateAnimation("fall_tree");
            default -> {
                // original ff "movement" controller in NULL: sitting while riding; moving on the ground crouch_walk /
                // run_armed / run / walk at speed 1 + (0.25 - size) * 2, moving in the air fly; standing crouch_idle /
                // idle_armed / idle (the speed set while walking stays)
                if (this.isPassenger()) {
                    yield this.kind.stateAnimation("sit");
                }
                boolean armed = this.koboldUnderAttack();
                boolean crouch = this.level().getBlockState(this.blockPosition().above()).blocksMotion();
                double speed = Math.abs(this.getX() - this.xo) + Math.abs(this.getZ() - this.zo);
                if (speed > 0.0D) {
                    if (!this.onGround() || Math.abs(this.yo - this.getY()) >= 0.1D) {
                        yield this.kind.stateAnimation("fly");
                    }
                    this.koboldWalkSpeed = 1.0D + (0.25F - this.koboldSize()) * 2.0F;
                    yield this.kind.stateAnimation(crouch ? "crouch_walk" : armed ? "run_armed" : speed > 0.2D ? "run" : "walk");
                }
                yield this.kind.stateAnimation(crouch ? "crouch_idle" : armed ? "idle_armed" : "idle");
            }
        };
    }

    /** Original f_.bT: the follow AI sets it while the owner sprints or is more than 7 blocks away. */
    void setGalathRunning(boolean running) {
        this.entityData.set(DATA_SPEED_CLASS, running ? 2 : 0);
    }

    /** Original f_ state RUN (fp.RUN): running to a wild Manglelie; synced as speed class 3. */
    void setGalathSeekRunning(boolean running) {
        this.entityData.set(DATA_SPEED_CLASS, running ? 3 : 0);
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
            super(CompanionEntity.this, Player.class, CompanionEntity.this.kind == CompanionKind.GOBLIN ? 2.0F : 3.0F, 1.0F);
        }

        @Override
        public boolean canUse() {
            return (!CompanionEntity.this.hasMaster() || CompanionEntity.this.followState == FollowState.IDLE)
                    && !CompanionEntity.this.fishingHoldsHer() && super.canUse();
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
            if (distance > 16.0F) {
                // original f.c(): master + random (0..9, 0..9), up to 20 tries for a free spot, else on the master
                for (int attempt = 0; attempt < 20; attempt++) {
                    double tx = master.getX() + self.getRandom().nextInt(10);
                    double tz = master.getZ() + self.getRandom().nextInt(10);
                    var box = self.getBoundingBox().move(tx - self.getX(), master.getY() - self.getY(), tz - self.getZ());
                    if (self.level().noCollision(self, box)) {
                        self.teleportTo(tx, master.getY(), tz);
                        return;
                    }
                }
                self.teleportTo(master.getX(), master.getY(), master.getZ());
                return;
            }
            self.getNavigation().moveTo(master, 0.5D);
        }
    }

    /**
     * The original follow/fight goal of Jenny, Ellie, Bia and Luna. It runs while she has a master and
     * ends (dropping the master) when that player is gone.
     */
    private final class MasterGoal extends Goal {
        private int repathTicks;
        private FollowState previous = FollowState.IDLE;
        private net.minecraft.world.entity.Entity rideVehicle;

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
            self.setWeaponMode(0); // original g.func_75251_c
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
                case RIDE -> {
                    self.getNavigation().stop();
                    if (self.isPassenger()) {
                        self.setAction(CompanionAction.SIT);
                    } else if (this.rideVehicle != null) {
                        self.setNoGravity(true);
                        self.noPhysics = true;
                        net.minecraft.world.phys.Vec3 look = this.rideVehicle.getLookAngle();
                        net.minecraft.world.phys.Vec3 at = master.position().subtract(look.x * 0.5D, 0.0D, look.z * 0.5D);
                        self.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                        self.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        self.setAction(CompanionAction.RIDE);
                    }
                }
                default -> self.getNavigation().stop();
            }
            this.previous = next;
        }

        private FollowState decide(Player master) {
            CompanionEntity self = CompanionEntity.this;
            if (self.isDowned() || self.isInScene()) {
                return FollowState.DOWNED;
            }
            // Original g.a(): riding along with a master on a vehicle / saddled horse.
            net.minecraft.world.entity.Entity vehicle = master.getVehicle();
            if (vehicle != null) {
                if (self.isPassenger() || self.startRiding(vehicle)
                        || (vehicle instanceof net.minecraft.world.entity.animal.horse.Horse horse && horse.isSaddled())) {
                    this.rideVehicle = vehicle;
                    return FollowState.RIDE;
                }
            } else if (this.previous == FollowState.RIDE) {
                self.setAction(CompanionAction.NONE);
                self.stopRiding();
                self.noPhysics = false;
                self.setNoGravity(false);
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
                    && self.distanceToSqr(target) <= 400.0D;
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
            // Original g.a(ATTACK): the counter p is bumped every tick while the bow is usable (even close up), and again
            // while drawing, so a full draw takes 16 ticks; after a shot it starts at -20 and she chases meanwhile.
            boolean meleeNow = distance < 1.9D && self.swingCooldown <= 0;
            if (!meleeNow && bow.getItem() instanceof net.minecraft.world.item.BowItem && self.hasLineOfSight(target)) {
                self.bowCharge = Math.min(self.bowCharge + 1, 1000);
                if (self.bowCharge > 0 && distance > 6.0D) {
                    self.getNavigation().stop();
                    self.setWeaponMode(2); // original g: e2.M = 2 while she draws the bow
                    self.setAction(CompanionAction.BOW);
                    if (++self.bowCharge >= 32) {
                        self.fireBow(target, bow);
                        self.bowCharge = -20;
                        self.setAction(CompanionAction.NONE);
                    }
                    return;
                }
            }
            if (self.action() == CompanionAction.BOW) {
                self.setAction(CompanionAction.NONE);
            }
            self.setWeaponMode(1); // original g: e2.M = 1 while she chases or hits
            if (distance < 1.9D && self.swingCooldown <= 0) {
                self.setAction(CompanionAction.ATTACK);
                self.attackAnimTicks = 10;
                self.swing(InteractionHand.MAIN_HAND);
                self.doHurtTarget(target);
                self.sweep(target);
                self.swingCooldown = self.weaponSwingDelay();
            }
        }

        private void follow(Player master) {
            CompanionEntity self = CompanionEntity.this;
            self.setWeaponMode(0);
            double distance = self.distanceTo(master);
            double bonus = Math.floor(distance / 5.0D) * 0.2D;
            boolean sprinting = master.isSprinting();
            double speed = (sprinting ? 0.7D : 0.5D) + bonus;
            self.entityData.set(DATA_SPEED_CLASS, sprinting ? 2 : (bonus > 0.0D ? 1 : 0));
            if (self.isInWater()) {
                // original f.b(): in water the speed is multiplied by 60 (swimming scales it by 0.02) and she walks
                speed *= 60.0D;
                self.entityData.set(DATA_SPEED_CLASS, 0);
            }
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
            self.setWeaponMode(0);
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
        return Math.max(1, (int) Math.round(Math.abs(Math.max(speed, 0.5D)) / 3.373494D * 20.0D));
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
