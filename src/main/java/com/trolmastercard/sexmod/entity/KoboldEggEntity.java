package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class KoboldEggEntity extends PathfinderMob implements GeoEntity {
    public static final int MAX_HATCH_AGE = 12_000;
    private static final String DEFAULT_COLOR = "PURPLE";
    private static final EntityDataAccessor<Integer> DATA_AGE =
            SynchedEntityData.defineId(KoboldEggEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_COLOR =
            SynchedEntityData.defineId(KoboldEggEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> DATA_TRIBE =
            SynchedEntityData.defineId(KoboldEggEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(KoboldEggEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private boolean hatching;

    public KoboldEggEntity(EntityType<? extends KoboldEggEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AGE, 0);
        builder.define(DATA_COLOR, DEFAULT_COLOR);
        builder.define(DATA_TRIBE, Optional.empty());
        builder.define(DATA_OWNER, Optional.empty());
    }

    public int hatchAge() {
        return this.entityData.get(DATA_AGE);
    }

    public void setHatchAge(int age) {
        this.entityData.set(DATA_AGE, Math.clamp(age, 0, MAX_HATCH_AGE));
    }

    public String eggColor() {
        return this.entityData.get(DATA_COLOR);
    }

    public void setEggColor(String color) {
        this.entityData.set(DATA_COLOR, color == null ? DEFAULT_COLOR : color);
    }

    public Optional<UUID> tribeId() {
        return this.entityData.get(DATA_TRIBE);
    }

    public void setTribeId(UUID tribeId) {
        this.entityData.set(DATA_TRIBE, Optional.ofNullable(tribeId));
    }

    public Optional<UUID> ownerId() {
        return this.entityData.get(DATA_OWNER);
    }

    public void setOwnerId(UUID ownerId) {
        this.entityData.set(DATA_OWNER, Optional.ofNullable(ownerId));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.hatching) {
            return;
        }

        int nextAge = Math.min(MAX_HATCH_AGE, this.hatchAge() + 1);
        this.setHatchAge(nextAge);
        if (nextAge >= MAX_HATCH_AGE) {
            this.hatch((ServerLevel) this.level());
        }
    }

    /** Original i.a(): a new Kobold of the tribe, owned by the tribe master, with the tribe's name. */
    private void hatch(ServerLevel level) {
        this.hatching = true;
        com.trolmastercard.sexmod.tribe.TribeData data = com.trolmastercard.sexmod.tribe.TribeData.get(level.getServer());
        java.util.UUID tribeId = this.tribeId().orElse(UUID.randomUUID());
        com.trolmastercard.sexmod.tribe.KoboldColor color = com.trolmastercard.sexmod.tribe.KoboldColor.byName(this.eggColor());
        com.trolmastercard.sexmod.tribe.Tribe tribe = data.getOrCreate(tribeId, color);
        CompanionEntity kobold = com.trolmastercard.sexmod.tribe.TribeManager.newKobold(
                level, tribeId, (float) (Math.random() * 0.25D), tribe.color);
        if (kobold == null) {
            this.hatching = false;
            return;
        }
        kobold.moveTo(this.getX() + 0.5D, this.getY(), this.getZ() + 0.5D, this.getYRot(), 0.0F);
        tribe.members.put(kobold.getUUID(), kobold.blockPosition());
        if (tribe.master != null) {
            kobold.setMaster(tribe.master);
        }
        for (CompanionEntity member : com.trolmastercard.sexmod.tribe.TribeManager.loadedMembers(level.getServer(), tribe)) {
            if (!"null".equals(member.tribeName())) {
                kobold.setTribeName(member.tribeName());
                break;
            }
        }
        data.touch();
        if (!level.addFreshEntity(kobold)) {
            this.hatching = false;
            return;
        }
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(),
                30, 0.35D, 0.35D, 0.35D, 0.08D);
        level.playSound(null, this.blockPosition(), SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.5F, 1.0F);
        if (tribe.master != null) {
            net.minecraft.server.level.ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tribe.master);
            if (owner != null) {
                owner.sendSystemMessage(Component.literal(kobold.koboldName() + " ").withStyle(tribe.color.chat())
                        .append(Component.literal("has become a ").withStyle(net.minecraft.ChatFormatting.WHITE))
                        .append(Component.literal("new tribe member").withStyle(net.minecraft.ChatFormatting.RED))
                        .append(Component.literal("!").withStyle(net.minecraft.ChatFormatting.WHITE)));
                owner.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0F, 1.0F);
                owner.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        }
        this.discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.tribeId().ifPresent(tribe -> tag.putString("tribeID", tribe.toString()));
        tag.putString("egg_color", this.eggColor());
        tag.putInt("eggAge", this.hatchAge());
        this.ownerId().ifPresent(owner -> tag.putUUID("FapCraftOwner", owner));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setTribeId(readUuid(tag, "tribeID").orElse(null));
        if (tag.contains("egg_color", Tag.TAG_STRING)) {
            this.setEggColor(tag.getString("egg_color"));
        }
        if (tag.contains("eggAge", Tag.TAG_INT)) {
            this.setHatchAge(tag.getInt("eggAge"));
        }
        this.setOwnerId(readUuid(tag, "FapCraftOwner").orElse(null));
        this.setNoAi(true);
    }

    private static Optional<UUID> readUuid(CompoundTag tag, String key) {
        try {
            if (tag.hasUUID(key)) {
                return Optional.of(tag.getUUID(key));
            }
        } catch (RuntimeException ignored) {
            // Malformed UUID arrays from old saves are treated as absent.
        }

        if (tag.contains(key, Tag.TAG_STRING)) {
            try {
                return Optional.of(UUID.fromString(tag.getString(key)));
            } catch (IllegalArgumentException ignored) {
                // Invalid legacy UUID strings are treated as absent.
            }
        }
        return Optional.empty();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, this::animationPredicate));
    }

    private PlayState animationPredicate(AnimationState<KoboldEggEntity> state) {
        int age = this.hatchAge();
        String animation;
        if (MAX_HATCH_AGE - age < 20) {
            animation = "animation.model.hatch";
        } else {
            float progress = (float) age / MAX_HATCH_AGE;
            if (progress > 0.98F) {
                animation = "animation.model.veryfast";
            } else if (progress > 0.85F) {
                animation = "animation.model.fast";
            } else if (progress > 0.75F) {
                animation = "animation.model.medium";
            } else if (progress > 0.5F) {
                animation = "animation.model.slow";
            } else {
                animation = "animation.model.null";
            }
        }
        return state.setAndContinue(RawAnimation.begin().thenLoop(animation));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animationCache;
    }
}
