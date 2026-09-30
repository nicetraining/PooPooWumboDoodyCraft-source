package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.data.GalathBindingData;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import com.trolmastercard.sexmod.registry.ModEntities;
import com.trolmastercard.sexmod.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * Original cc: using the coin starts a 4 s summon (particle stream from 1 s to 3 s, Galath appears two blocks in front of the
 * eyes at 4 s); right-clicking her with the coin starts a de-summon (animation at 1 s, removal at 3 s, done at 4 s).
 * The timers live in the player's persistent data, in ticks instead of the original's milliseconds.
 */
public final class GalathCoinItem extends LegacyPurposeItem {
    private static final String ACTIVATION = "sexmod:galath_coin_activation_time";
    private static final String DEACTIVATION = "sexmod:galath_coin_deactivation_time";
    private static final String DE_SUMMONING = "sexmod:galath_coin_de_summoning_animation_time";

    public GalathCoinItem(Properties properties) {
        super(properties, "galath_coin", 0);
    }

    private static void sound(Level level, Player player, String path) {
        ModSounds.find(path).ifPresent(sound -> level.playSound(null, player.getX(), player.getY(), player.getZ(),
                sound.get(), SoundSource.PLAYERS, 1.0F, 1.0F));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag data = player.getPersistentData();
        if (data.getLong(DEACTIVATION) != 0L || data.getLong(ACTIVATION) != 0L) {
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel serverLevel) {
            boolean hasGalath = GalathBindingData.get(serverLevel.getServer()).activeFor(player.getUUID()).isPresent();
            if (hasGalath) {
                sound(level, player, "misc.beew.beew0"); // error buzz
            } else {
                sound(level, player, "misc.weoweo.weoweo1");
                data.putLong(ACTIVATION, Math.max(1L, level.getGameTime()));
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Original PlayerInteractEvent.EntityInteract: the coin used on her own Galath starts the de-summon. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof CompanionEntity companion)
                || companion.kind() != CompanionKind.GALATH
                || !companion.isMaster(player)) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            sound(player.level(), player, "misc.weoweo.weoweo0");
            player.getPersistentData().putLong(DEACTIVATION, Math.max(1L, player.level().getGameTime()));
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!(entity instanceof Player player) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = level.getGameTime();
        long activated = data.getLong(ACTIVATION);
        long deactivated = data.getLong(DEACTIVATION);
        GalathBindingData bindings = GalathBindingData.get(serverLevel.getServer());

        if (activated != 0L) {
            long elapsed = now - activated;
            Vec3 eye = player.getEyePosition();
            Vec3 front = eye.add(player.getLookAngle().normalize().scale(2.0D));
            if (elapsed > 20 && elapsed < 60) {
                // the particle stream travels from beside the hand to two blocks in front of the eyes
                Vec3 hand = eye.add(new Vec3(-0.1D, -0.01D + player.getXRot() * 0.0015D, 0.0D).yRot((float) Math.toRadians(-player.yBodyRot)));
                Vec3 at = hand.lerp(front, (elapsed - 20) / 40.0D);
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            if (elapsed > 80) {
                data.putLong(ACTIVATION, 0L);
                for (int i = 0; i < 100; i++) {
                    serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, front.x, front.y, front.z, 0,
                            (2.0F * player.getRandom().nextFloat() - 1.0F) * 0.2D,
                            (2.0F * player.getRandom().nextFloat() - 1.0F) * 0.2D,
                            (2.0F * player.getRandom().nextFloat() - 1.0F) * 0.2D, 1.0D);
                }
                CompanionEntity galath = ModEntities.get(CompanionKind.GALATH).create(serverLevel);
                if (galath != null) {
                    galath.moveTo(front.x, front.y, front.z, player.getYRot() + 180.0F, 0.0F);
                    galath.markSummonedByGalathCoin();
                    galath.setMaster(player.getUUID());
                    serverLevel.addFreshEntity(galath);
                    bindings.bind(player.getUUID(), galath.getUUID());
                }
            }
        }

        if (deactivated != 0L) {
            long elapsed = now - deactivated;
            Optional<UUID> galathId = bindings.activeFor(player.getUUID());
            Entity galath = galathId.map(id -> findGalath(serverLevel, id)).orElse(null);
            if (elapsed > 20 && elapsed < 60 && galath != null) {
                // everything is sucked back into the coin: particles drift from her towards the hand
                Vec3 from = galath.position().add(0.0D, 1.5D, 0.0D);
                Vec3 to = player.getEyePosition();
                Vec3 at = from.lerp(to, (elapsed - 20) / 40.0D);
                serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            if (!data.getBoolean(DE_SUMMONING) && elapsed > 20) {
                data.putBoolean(DE_SUMMONING, true);
                if (galath instanceof CompanionEntity companion) {
                    companion.beginGalathDeSummon(40);
                    Vec3 center = companion.position().add(0.0D, 1.5D, 0.0D);
                    for (int i = 0; i < 100; i++) {
                        Vec3 offset = new Vec3((player.getRandom().nextFloat() * 2.0F - 1.0F) * 1.5D,
                                (player.getRandom().nextFloat() * 2.0F - 1.0F) * 1.5D,
                                (player.getRandom().nextFloat() * 2.0F - 1.0F) * 1.5D);
                        Vec3 at = center.add(offset);
                        Vec3 pull = offset.scale(-0.03D);
                        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH, at.x, at.y, at.z, 0, pull.x, pull.y, pull.z, 1.0D);
                    }
                }
            }
            if (elapsed > 60 && galath instanceof CompanionEntity companion) {
                companion.discard(); // removes her binding too
            }
            if (elapsed > 80) {
                data.putLong(DEACTIVATION, 0L);
                data.putBoolean(DE_SUMMONING, false);
            }
        }
    }

    private static Entity findGalath(ServerLevel level, UUID id) {
        for (ServerLevel candidate : level.getServer().getAllLevels()) {
            Entity found = candidate.getEntity(id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
