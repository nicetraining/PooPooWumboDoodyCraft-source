package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.data.GalathBindingData;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public final class GalathCoinItem extends LegacyPurposeItem {
    public GalathCoinItem(Properties properties) {
        super(properties, "galath_coin", 1);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel) {
            GalathBindingData bindings = GalathBindingData.get(serverLevel.getServer());
            Optional<UUID> activeGalath = bindings.activeFor(player.getUUID());

            if (activeGalath.isPresent()) {
                bindings.clear(player.getUUID());
                discardLoadedGalath(serverLevel, activeGalath.get());
                player.getCooldowns().addCooldown(this, 200);
                player.displayClientMessage(
                        Component.translatable("message.sexmod.galath_dismissed"),
                        true
                );
            } else {
                CompanionEntity galath = CompanionItemSupport.spawn(
                        serverLevel,
                        CompanionKind.GALATH,
                        CompanionItemSupport.inFrontOf(player),
                        player,
                        true
                );

                if (galath != null) {
                    galath.markSummonedByGalathCoin();
                    bindings.bind(player.getUUID(), galath.getUUID());
                    player.getCooldowns().addCooldown(this, 200);
                    player.displayClientMessage(
                            Component.translatable("message.sexmod.galath_summoned"),
                            true
                    );
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity target,
            InteractionHand hand
    ) {
        if (!(target instanceof CompanionEntity companion)
                || companion.kind() != CompanionKind.GALATH
                || !companion.isMaster(player)) {
            return InteractionResult.PASS;
        }

        if (!player.level().isClientSide) {
            if (player.level() instanceof ServerLevel serverLevel) {
                GalathBindingData.get(serverLevel.getServer()).clearIfMatches(
                        player.getUUID(),
                        companion.getUUID()
                );
            }
            companion.discard();
            player.getCooldowns().addCooldown(this, 200);
            player.displayClientMessage(
                    Component.translatable("message.sexmod.galath_dismissed"),
                    true
            );
        }

        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private static void discardLoadedGalath(ServerLevel currentLevel, UUID entityId) {
        currentLevel.getServer().getAllLevels().forEach(level -> {
            if (level.getEntity(entityId) instanceof CompanionEntity companion
                    && companion.kind() == CompanionKind.GALATH) {
                companion.discard();
            }
        });
    }
}
