package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.registry.ModEntities;
import com.trolmastercard.sexmod.registry.ModPotions;
import com.trolmastercard.sexmod.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The original slime girl's scripted life (class fn): no goals, a 100 tick hop cycle, a horny level that grows by
 * itself, scenes that start when a player walks into her, and pregnancy that ends in a baby slime.
 */
final class SlimeBehavior {
    static final int IDLE = 0;
    static final int JUMP_START = 1;
    static final int JUMP_AIR = 2;
    static final int JUMP_END = 3;
    static final int UNDRESS = 4;
    static final int GO_ON_BED = 5;
    static final int WAIT = 6;

    private static final double HOP_SPEED = 0.7D;
    private static final double HOP_POWER = 0.9D;
    static final int BIRTH_TICKS = 2400;

    private final CompanionEntity slime;
    private int groundTicks;
    private boolean wasOnGround = true;
    private boolean growThisCycle;
    private int stateTicks;
    private float hopYaw;

    SlimeBehavior(CompanionEntity slime) {
        this.slime = slime;
        this.hopYaw = slime.getYRot();
    }

    void tick(ServerLevel level) {
        if (this.slime.isInScene()) {
            return; // the scene owns her
        }
        int state = this.slime.slimeState();
        int horny = this.slime.slimeHorny();
        int birth = this.slime.slimeBirth();

        if (birth != -1) {
            birth--;
            this.slime.setSlimeBirth(birth);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, this.slime.getX(), this.slime.getY() + 0.5D,
                    this.slime.getZ(), 1, 0.3D, 0.3D, 0.3D, 0.0D); // original fn.d(): spell particles while pregnant
            if (birth == 0) {
                ModSounds.find("misc.plob.plob0").ifPresent(
                        sound -> this.slime.playSound(sound.get(), 1.0F, 1.0F));
            }
            if (birth < 0) {
                this.giveBirth(level);
            }
        }

        if (this.slime.hasEffect(ModPotions.HORNY_EFFECT) && state == IDLE && this.slime.slimeBirth() == -1) {
            this.slime.removeEffect(ModPotions.HORNY_EFFECT);
            this.slime.setSlimeHorny(2);
            horny = 2;
            if (!this.slime.isNude()) {
                this.startUndress();
                state = UNDRESS;
            }
        }
        if (horny >= 2 && this.slime.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.HEART, this.slime.getX(), this.slime.getY() + 1.2D, this.slime.getZ(),
                    1, 0.3D, 0.3D, 0.3D, 0.02D);
        }

        switch (state) {
            case UNDRESS -> {
                this.pin();
                this.stateTicks++;
                if (this.stateTicks == 19) {
                    this.slime.setOutfit(OutfitMode.ALTERNATE); // the undress animation's keyframe
                }
                if (this.stateTicks >= 21) {
                    this.slime.setSlimeState(IDLE);
                    this.groundTicks = 0;
                }
                return;
            }
            case GO_ON_BED -> {
                this.pin();
                if (++this.stateTicks >= 34) {
                    this.slime.setSlimeState(WAIT);
                }
                return;
            }
            case WAIT -> {
                this.pin();
                Player player = this.touchingPlayer(level);
                if (player != null && this.slime.startScene(player, CompanionScene.SLIME_DOGGY)) {
                    this.slime.setSlimeState(IDLE);
                }
                return;
            }
            default -> {
            }
        }

        if (horny >= 2) {
            if (horny >= 4 && this.slime.onGround()) {
                this.slime.getNavigation().stop();
                this.slime.setSlimeState(GO_ON_BED);
                this.stateTicks = 0;
                this.pin();
                return;
            }
            Player player = this.touchingPlayer(level);
            if (player != null && this.slime.onGround() && this.slime.startScene(player, CompanionScene.SLIME_BLOWJOB)) {
                return;
            }
        }
        this.hop(level, state, horny);
    }

    /** Original: the hop cycle counts ticks on the ground; aim at 85, crouch at 90, jump at 100. */
    private void hop(ServerLevel level, int state, int horny) {
        boolean onGround = this.slime.onGround();
        if (onGround) {
            if (!this.wasOnGround) {
                this.groundTicks = 0;
                this.stateTicks = 0;
                this.growThisCycle = this.slime.slimeBirth() == -1 && this.slime.getRandom().nextFloat() < 0.1F;
                this.slime.setSlimeState(JUMP_END);
                this.slime.setDeltaMovement(0.0D, this.slime.getDeltaMovement().y, 0.0D);
                state = JUMP_END;
            }
            this.groundTicks++;
            if (state == JUMP_END && ++this.stateTicks >= 44) {
                this.slime.setSlimeState(IDLE);
            }
            if (this.groundTicks == 50 && this.growThisCycle) {
                this.slime.setSlimeHorny(horny + 1);
                if (horny + 1 == 1 && !this.slime.isNude()) {
                    this.startUndress();
                    this.wasOnGround = true;
                    return;
                }
            }
            if (this.groundTicks == 85) {
                this.hopYaw = this.chooseYaw(level, horny);
            }
            if (this.groundTicks >= 85 && this.groundTicks < 100) {
                float turned = Mth.rotLerp(0.3F, this.slime.getYRot(), this.hopYaw);
                this.slime.setYRot(turned);
                this.slime.yBodyRot = turned;
                this.slime.yHeadRot = turned;
            }
            if (this.groundTicks == 90) {
                this.slime.setSlimeState(JUMP_START);
                this.stateTicks = 0;
            }
            if (this.groundTicks >= 100) {
                this.jump();
            }
        } else if (state == JUMP_START || state == IDLE) {
            this.stateTicks++;
            if (this.stateTicks >= 43 || state == IDLE) {
                this.slime.setSlimeState(JUMP_AIR);
            }
        }
        this.wasOnGround = onGround;
    }

    private void jump() {
        this.slime.setYRot(this.hopYaw);
        this.slime.yBodyRot = this.hopYaw;
        this.slime.yHeadRot = this.hopYaw;
        double radians = this.hopYaw * (Math.PI / 180.0D);
        this.slime.setDeltaMovement(-Math.sin(radians) * HOP_SPEED, HOP_POWER, Math.cos(radians) * HOP_SPEED);
        this.slime.hasImpulse = true;
        this.groundTicks = 0;
        this.stateTicks = 0;
        this.wasOnGround = false;
    }

    /** Random while pregnant or not yet horny, else towards the nearest free player within 30 blocks. */
    private float chooseYaw(ServerLevel level, int horny) {
        float random = this.slime.getRandom().nextFloat() * 360.0F;
        if (this.slime.slimeBirth() != -1 || horny < 2) {
            return random;
        }
        Player nearest = null;
        double best = 30.0D * 30.0D;
        for (Player player : level.players()) {
            double distance = player.distanceToSqr(this.slime);
            if (distance < best && !player.isSpectator() && !this.isPartnerElsewhere(level, player)) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest == null) {
            return random;
        }
        return (float) (Mth.atan2(nearest.getZ() - this.slime.getZ(), nearest.getX() - this.slime.getX()) * 57.29578D) - 90.0F;
    }

    private boolean isPartnerElsewhere(ServerLevel level, Player player) {
        return !level.getEntitiesOfClass(CompanionEntity.class, player.getBoundingBox().inflate(64.0D),
                other -> other != this.slime && other.scenePartnerId().filter(player.getUUID()::equals).isPresent()).isEmpty();
    }

    /** The nearest player within a block, who is not already somebody's partner. */
    private Player touchingPlayer(ServerLevel level) {
        Player player = level.getNearestPlayer(this.slime, 1.0D);
        return player != null && player.onGround() && !this.isPartnerElsewhere(level, player) ? player : null;
    }

    private void startUndress() {
        this.slime.setSlimeState(UNDRESS);
        this.stateTicks = 0;
    }

    private void pin() {
        this.slime.getNavigation().stop();
        this.slime.setDeltaMovement(0.0D, Math.min(0.0D, this.slime.getDeltaMovement().y), 0.0D);
    }

    private void giveBirth(ServerLevel level) {
        FriendlySlimeEntity baby = ModEntities.FRIENDLY_SLIME.get().create(level);
        if (baby != null) {
            baby.moveTo(this.slime.getX(), this.slime.getY(), this.slime.getZ(), this.slime.getYRot(), 0.0F);
            baby.setSize(1, true);
            level.addFreshEntity(baby);
        }
        this.slime.setSlimeBirth(-1);
    }
}
