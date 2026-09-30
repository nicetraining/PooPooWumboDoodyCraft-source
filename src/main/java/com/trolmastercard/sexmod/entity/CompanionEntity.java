package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.data.CompanionOwnershipData;
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
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_FOLLOWING =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_MODE =
            SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
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
    private CompanionMode scenePreviousMode;
    private String lastAnimationKey = "";
    private int rangedAttackCooldown;
    private boolean richRewardGranted;
    private boolean ownershipTracked;

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
                    companion -> companion.ownerId().isEmpty() && !companion.isTechnicalProxy()
            ).size() < 2;
        }
        return true;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FollowOwnerGoal());
        this.goalSelector.addGoal(2, new ReturnHomeGoal());
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(5, new ModeWanderGoal());
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(
                2,
                new NearestAttackableTargetGoal<>(
                        this,
                        Monster.class,
                        10,
                        true,
                        false,
                        target -> this.mode() == CompanionMode.GUARD && !this.isAlliedTo(target)
                )
        );
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_FOLLOWING, false);
        builder.define(DATA_MODE, CompanionMode.WANDER.serializedName());
        builder.define(DATA_OUTFIT, OutfitMode.DEFAULT.serializedName());
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_SCALE, 1.0F);
        builder.define(DATA_HOME, Optional.empty());
        builder.define(DATA_SCENE, "");
        builder.define(DATA_SCENE_PHASE, 0);
        builder.define(DATA_SCENE_PARTNER, Optional.empty());
        builder.define(DATA_FLYING, false);
    }

    public Optional<UUID> ownerId() {
        return this.entityData.get(DATA_OWNER);
    }

    public boolean isFollowing() {
        return this.mode() == CompanionMode.FOLLOW;
    }

    public void setFollowing(boolean following) {
        this.setMode(following ? CompanionMode.FOLLOW : CompanionMode.STAY);
    }

    public CompanionMode mode() {
        return CompanionMode.byName(this.entityData.get(DATA_MODE));
    }

    public void setMode(CompanionMode mode) {
        CompanionMode safeMode = mode == null ? CompanionMode.STAY : mode;
        this.entityData.set(DATA_MODE, safeMode.serializedName());
        this.entityData.set(DATA_FOLLOWING, safeMode == CompanionMode.FOLLOW);
        if (safeMode == CompanionMode.STAY || safeMode == CompanionMode.WANDER) {
            this.getNavigation().stop();
            if (safeMode == CompanionMode.STAY) {
                this.setTarget(null);
            }
        }
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

    public boolean isOwnedBy(Player player) {
        return this.ownerId().map(player.getUUID()::equals).orElse(false);
    }

    public boolean mayBeCommandedBy(Player player) {
        return this.isOwnedBy(player) || player.hasPermissions(2);
    }

    public boolean bondTo(Player player, boolean following) {
        return this.bondTo(player.getUUID(), following);
    }

    public boolean bondTo(UUID owner, boolean following) {
        if (!this.technicalProxy && this.level() instanceof ServerLevel serverLevel) {
            CompanionOwnershipData ownership = CompanionOwnershipData.get(serverLevel.getServer());
            Optional<UUID> previousOwner = this.ownerId();
            if (previousOwner.isEmpty() || !previousOwner.get().equals(owner)) {
                if (!ownership.canClaim(owner, this.kind, 1)) {
                    return false;
                }
                previousOwner.ifPresent(oldOwner -> ownership.release(oldOwner, this.getUUID()));
            }
            if (!ownership.claim(owner, this.getUUID(), this.kind)) {
                return false;
            }
            this.ownershipTracked = true;
        }

        this.entityData.set(DATA_OWNER, Optional.of(owner));
        this.setPersistenceRequired();
        if (this.homePos().isEmpty()) {
            this.setHome(this.blockPosition());
        }
        this.setMode(following ? CompanionMode.FOLLOW : CompanionMode.STAY);
        return true;
    }

    public void bondToReserved(UUID owner, boolean following) {
        this.entityData.set(DATA_OWNER, Optional.of(owner));
        this.ownershipTracked = true;
        this.setPersistenceRequired();
        if (this.homePos().isEmpty()) {
            this.setHome(this.blockPosition());
        }
        this.setMode(following ? CompanionMode.FOLLOW : CompanionMode.STAY);
    }

    public void markSummonedByGalathCoin() {
        this.summonedByGalathCoin = true;
    }

    public boolean openControlMenu(ServerPlayer player) {
        if (!this.mayBeCommandedBy(player) || player.distanceToSqr(this) > 64.0D) {
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

    public boolean startScene(Player player, CompanionScene scene) {
        if (!(this.level() instanceof ServerLevel serverLevel)
                || scene == null
                || scene.kind() != this.kind
                || this.isInScene()
                || !this.mayBeCommandedBy(player)
                || player.distanceToSqr(this) > 64.0D
                || !this.isSceneEnvironmentValid(serverLevel, player, scene, true)) {
            return false;
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
        this.scenePreviousMode = this.mode();
        this.scenePreviousNoAi = this.isNoAi();
        this.scenePreviousNoGravity = this.isNoGravity();
        this.sceneAnchorPosition = anchor.position();
        this.sceneAnchorYaw = anchor.yaw();
        this.setMode(CompanionMode.STAY);
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
                && this.mayBeCommandedBy(player)
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

        if (other instanceof Player player && this.isOwnedBy(player)) {
            return true;
        }

        if (other instanceof CompanionEntity companion) {
            Optional<UUID> owner = this.ownerId();
            return owner.isPresent() && owner.equals(companion.ownerId());
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

        if (!this.ownershipTracked && !this.technicalProxy) {
            this.ownerId().ifPresent(owner -> {
                CompanionOwnershipData.get(serverLevel.getServer())
                        .trackExisting(owner, this.getUUID(), this.kind);
                this.setPersistenceRequired();
            });
            this.ownershipTracked = true;
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
        this.tickOwnerCombat();
        this.tickRangedCombat(serverLevel);
        this.tickFlight();

        if (this.tickCount % 20 == 0) {
            this.syncEquipmentSlots();
        }

        if (this.tickCount % 160 == 0
                && this.getHealth() < this.getMaxHealth()
                && this.getTarget() == null
                && !this.isInScene()) {
            this.heal(1.0F);
        }

        if (this.kind == CompanionKind.GALATH
                && this.summonedByGalathCoin
                && (this.tickCount == 1 || this.tickCount % 20 == 0)) {
            Optional<UUID> owner = this.ownerId();
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

            if (completed && scene != null && controller != null) {
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
        if (this.scenePreviousMode != null) {
            CompanionMode previousMode = this.scenePreviousMode;
            this.scenePreviousMode = null;
            this.setMode(previousMode);
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
                || !this.isOwnedBy(player)
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

    private void tickOwnerCombat() {
        if (this.isInScene()) {
            this.setTarget(null);
            return;
        }

        if (this.getTarget() != null
                && (!this.getTarget().isAlive() || this.isAlliedTo(this.getTarget()))) {
            this.setTarget(null);
        }

        if (this.getTarget() != null || this.mode() == CompanionMode.STAY) {
            return;
        }

        Player owner = this.ownerPlayer().orElse(null);
        if (owner == null) {
            return;
        }

        LivingEntity candidate = owner.getLastHurtByMob();
        if (candidate == null || !candidate.isAlive() || this.isAlliedTo(candidate)) {
            candidate = owner.getLastHurtMob();
        }

        if (candidate != null
                && candidate.isAlive()
                && !this.isAlliedTo(candidate)
                && this.distanceToSqr(candidate) <= 1024.0D) {
            this.setTarget(candidate);
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
        if (destination == null && this.mode() == CompanionMode.FOLLOW) {
            destination = this.ownerPlayer().orElse(null);
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
        for (int slot = 0; slot < this.equipment.getContainerSize(); slot++) {
            ItemStack stack = this.equipment.removeItemNoUpdate(slot);
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
            this.ownerId().ifPresent(owner -> CompanionOwnershipData
                    .get(serverLevel.getServer())
                    .release(owner, this.getUUID()));
            if (this.kind == CompanionKind.GALATH && this.summonedByGalathCoin) {
                this.ownerId().ifPresent(owner -> GalathBindingData
                        .get(serverLevel.getServer())
                        .clearIfMatches(owner, this.getUUID()));
            }
        }

        super.remove(reason);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (isGift(heldItem)) {
            if (!this.level().isClientSide) {
                if (this.ownerId().isPresent()
                        && !this.isOwnedBy(player)
                        && !player.hasPermissions(2)) {
                    player.displayClientMessage(
                            Component.translatable("message.sexmod.already_bonded", this.getDisplayName()),
                            true
                    );
                    return InteractionResult.CONSUME;
                }

                if (!this.bondTo(player, true)) {
                    player.displayClientMessage(
                            Component.translatable(
                                    "message.sexmod.companion_limit",
                                    CompanionOwnershipData.MAX_TOTAL
                            ),
                            true
                    );
                    return InteractionResult.CONSUME;
                }

                if (!player.getAbilities().instabuild) {
                    heldItem.shrink(1);
                }

                this.heal(4.0F);
                this.playSound(SoundEvents.PLAYER_LEVELUP, 0.45F, 1.6F);

                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(
                            ParticleTypes.HEART,
                            this.getX(),
                            this.getY() + this.getBbHeight() * 0.75D,
                            this.getZ(),
                            7,
                            0.35D,
                            0.45D,
                            0.35D,
                            0.02D
                    );
                }

                player.displayClientMessage(
                        Component.translatable("message.sexmod.gift_accepted", this.getDisplayName()),
                        true
                );
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (heldItem.isEmpty()) {
            if (player.isShiftKeyDown()) {
                if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    this.openControlMenu(serverPlayer);
                }

                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            if (!this.level().isClientSide) {
                if (this.ownerId().isEmpty() && !player.hasPermissions(2)) {
                    player.displayClientMessage(
                            Component.translatable("message.sexmod.gift_required", this.getDisplayName()),
                            true
                    );
                } else if (this.isOwnedBy(player) || player.hasPermissions(2)) {
                    boolean following = !this.isFollowing();
                    this.setFollowing(following);
                    player.displayClientMessage(
                            Component.translatable(
                                    following ? "message.sexmod.following" : "message.sexmod.staying",
                                    this.getDisplayName()
                            ),
                            true
                    );
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.sexmod.already_bonded", this.getDisplayName()),
                            true
                    );
                }
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    private static boolean isGift(ItemStack stack) {
        return stack.is(Items.DIAMOND)
                || stack.is(Items.EMERALD)
                || stack.is(Items.GOLD_INGOT)
                || stack.is(Items.GOLD_BLOCK);
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
        this.ownerId().ifPresent(owner -> tag.putUUID("FapCraftOwner", owner));
        tag.putString("FapCraftMode", this.mode().serializedName());
        tag.putBoolean("FapCraftFollowing", this.mode() == CompanionMode.FOLLOW);
        tag.putString("FapCraftOutfit", this.outfit().serializedName());
        tag.putInt("FapCraftVariant", this.appearanceVariant());
        tag.putFloat("FapCraftScale", this.appearanceScale());
        this.homePos().ifPresent(home -> {
            tag.putInt("FapCraftHomeX", home.getX());
            tag.putInt("FapCraftHomeY", home.getY());
            tag.putInt("FapCraftHomeZ", home.getZ());
        });
        tag.put("FapCraftEquipment", this.equipment.createTag(this.registryAccess()));
        tag.putBoolean("FapCraftGalathCoinSummon", this.summonedByGalathCoin);
        tag.putBoolean("FapCraftTechnicalProxy", this.technicalProxy);
        tag.putBoolean("FapCraftRichReward", this.richRewardGranted);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.hasUUID("FapCraftOwner")) {
            this.entityData.set(DATA_OWNER, Optional.of(tag.getUUID("FapCraftOwner")));
        } else {
            readLegacyOwner(tag).ifPresent(owner ->
                    this.entityData.set(DATA_OWNER, Optional.of(owner)));
        }
        if (this.ownerId().isPresent()) {
            this.setPersistenceRequired();
        }
        this.ownershipTracked = false;

        if (tag.contains("FapCraftMode", Tag.TAG_STRING)) {
            this.setMode(CompanionMode.byName(tag.getString("FapCraftMode")));
        } else {
            this.setMode(tag.getBoolean("FapCraftFollowing")
                    ? CompanionMode.FOLLOW
                    : CompanionMode.STAY);
        }

        this.setOutfit(OutfitMode.byName(tag.getString("FapCraftOutfit")));
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
        } else if (tag.contains("homeX", Tag.TAG_DOUBLE)) {
            this.setHome(BlockPos.containing(
                    tag.getDouble("homeX"),
                    tag.getDouble("homeY"),
                    tag.getDouble("homeZ")
            ));
        }

        if (tag.contains("FapCraftEquipment", Tag.TAG_LIST)) {
            this.equipment.fromTag(
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

    private static Optional<UUID> readLegacyOwner(CompoundTag tag) {
        for (String key : List.of("Owner", "owner", "master", "Master")) {
            if (tag.hasUUID(key)) {
                return Optional.of(tag.getUUID(key));
            }

            if (tag.contains(key, Tag.TAG_STRING)) {
                try {
                    return Optional.of(UUID.fromString(tag.getString(key)));
                } catch (IllegalArgumentException ignored) {
                    // Try the next known legacy key.
                }
            }
        }

        return Optional.empty();
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

        if (state.getData(DataTickets.ENTITY) != null && state.isMoving()) {
            return state.setAndContinue(this.kind.walkAnimation());
        }

        return state.setAndContinue(this.kind.idleAnimation());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }

    private Optional<Player> ownerPlayer() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }

        return this.ownerId().map(serverLevel::getPlayerByUUID);
    }

    private final class ReturnHomeGoal extends Goal {
        private BlockPos home;
        private int recalculatePathTicks;

        private ReturnHomeGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (CompanionEntity.this.mode() != CompanionMode.HOME
                    || CompanionEntity.this.isInScene()) {
                return false;
            }

            this.home = CompanionEntity.this.homePos().orElse(null);
            return this.home != null
                    && !this.isAtHome();
        }

        @Override
        public boolean canContinueToUse() {
            return CompanionEntity.this.mode() == CompanionMode.HOME
                    && !CompanionEntity.this.isInScene()
                    && this.home != null
                    && !this.isAtHome();
        }

        @Override
        public void start() {
            this.recalculatePathTicks = 0;
            this.moveHome();
        }

        @Override
        public void stop() {
            CompanionEntity.this.getNavigation().stop();
            this.home = null;
        }

        @Override
        public void tick() {
            if (--this.recalculatePathTicks <= 0) {
                this.moveHome();
            }
        }

        private boolean isAtHome() {
            return this.home != null
                    && this.home.distToCenterSqr(CompanionEntity.this.position()) <= 4.0D;
        }

        private void moveHome() {
            if (this.home == null) {
                return;
            }

            this.recalculatePathTicks = 20;
            CompanionEntity.this.getNavigation().moveTo(
                    this.home.getX() + 0.5D,
                    this.home.getY(),
                    this.home.getZ() + 0.5D,
                    1.0D
            );
        }
    }

    private final class ModeWanderGoal extends WaterAvoidingRandomStrollGoal {
        private ModeWanderGoal() {
            super(CompanionEntity.this, 1.0D);
        }

        @Override
        public boolean canUse() {
            return CompanionEntity.this.mode() == CompanionMode.WANDER
                    && !CompanionEntity.this.isInScene()
                    && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return CompanionEntity.this.mode() == CompanionMode.WANDER
                    && !CompanionEntity.this.isInScene()
                    && super.canContinueToUse();
        }
    }

    private final class FollowOwnerGoal extends Goal {
        private Player owner;
        private int recalculatePathTicks;

        private FollowOwnerGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!CompanionEntity.this.isFollowing()) {
                return false;
            }

            this.owner = CompanionEntity.this.ownerPlayer().orElse(null);
            return this.owner != null
                    && !this.owner.isSpectator()
                    && CompanionEntity.this.distanceToSqr(this.owner) > 9.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return CompanionEntity.this.isFollowing()
                    && this.owner != null
                    && this.owner.isAlive()
                    && CompanionEntity.this.distanceToSqr(this.owner) > 4.0D;
        }

        @Override
        public void start() {
            this.recalculatePathTicks = 0;
        }

        @Override
        public void stop() {
            CompanionEntity.this.getNavigation().stop();
            this.owner = null;
        }

        @Override
        public void tick() {
            if (this.owner == null) {
                return;
            }

            CompanionEntity.this.getLookControl().setLookAt(this.owner, 10.0F, 30.0F);
            if (--this.recalculatePathTicks > 0) {
                return;
            }

            this.recalculatePathTicks = 10;
            double distance = CompanionEntity.this.distanceToSqr(this.owner);

            if (distance > 400.0D) {
                CompanionEntity.this.teleportTo(
                        this.owner.getX(),
                        this.owner.getY(),
                        this.owner.getZ()
                );
                CompanionEntity.this.getNavigation().stop();
            } else {
                CompanionEntity.this.getNavigation().moveTo(this.owner, 1.1D);
            }
        }
    }
}
