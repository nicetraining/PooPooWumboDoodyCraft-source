package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.data.CompanionOwnershipData;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import com.trolmastercard.sexmod.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;

final class CompanionItemSupport {
    private CompanionItemSupport() {
    }

    static BlockPos inFrontOf(Player player) {
        return player.blockPosition().relative(player.getDirection(), 2);
    }

    static boolean hasCapacity(
            ServerLevel level,
            Player owner,
            CompanionKind kind,
            int amount
    ) {
        return CompanionOwnershipData.get(level.getServer())
                .canClaim(owner.getUUID(), kind, amount);
    }

    static void showCapacityLimit(Player owner) {
        owner.displayClientMessage(
                Component.translatable(
                        "message.sexmod.companion_limit",
                        CompanionOwnershipData.MAX_TOTAL
                ),
                true
        );
    }

    static CompanionEntity spawn(
            ServerLevel level,
            CompanionKind kind,
            BlockPos position,
            Player owner,
            boolean following
    ) {
        if (!hasCapacity(level, owner, kind, 1)) {
            showCapacityLimit(owner);
            return null;
        }

        CompanionEntity companion = ModEntities.get(kind).spawn(
                level,
                position,
                MobSpawnType.SPAWN_EGG
        );

        if (companion == null) {
            return null;
        }

        if (!companion.bondTo(owner, following)) {
            companion.discard();
            showCapacityLimit(owner);
            return null;
        }
        level.sendParticles(
                ParticleTypes.PORTAL,
                companion.getX(),
                companion.getY() + companion.getBbHeight() * 0.5D,
                companion.getZ(),
                24,
                0.35D,
                0.6D,
                0.35D,
                0.08D
        );
        level.playSound(
                null,
                companion.blockPosition(),
                SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS,
                0.8F,
                1.2F
        );
        return companion;
    }
}
