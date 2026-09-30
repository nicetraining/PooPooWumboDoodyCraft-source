package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class AlliesLampItem extends LegacyPurposeItem {
    private static final int MAX_WISHES = 3;

    public AlliesLampItem(Properties properties) {
        super(properties, "allies_lamp", 1);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.getAbilities().instabuild && stack.getDamageValue() >= MAX_WISHES) {
            if (!level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.sexmod.lamp_empty"),
                        true
                );
            }
            return InteractionResultHolder.fail(stack);
        }

        if (level instanceof ServerLevel serverLevel) {
            CompanionEntity allie = CompanionItemSupport.spawn(
                    serverLevel,
                    CompanionKind.ALLIE,
                    CompanionItemSupport.inFrontOf(player),
                    player,
                    true
            );

            if (allie == null) {
                return InteractionResultHolder.fail(stack);
            }

            if (!player.getAbilities().instabuild) {
                stack.setDamageValue(Math.min(MAX_WISHES, stack.getDamageValue() + 1));
            }

            player.displayClientMessage(
                    Component.translatable(
                            "message.sexmod.lamp_summoned",
                            Math.max(0, MAX_WISHES - stack.getDamageValue())
                    ),
                    true
            );
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
                "tooltip.sexmod.allies_lamp.wishes",
                Math.max(0, MAX_WISHES - stack.getDamageValue())
        ).withStyle(ChatFormatting.AQUA));
    }
}
