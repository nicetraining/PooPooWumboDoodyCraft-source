package com.trolmastercard.sexmod.item;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

public final class DragonStaffItem extends LegacyPurposeItem {
    private static final double COMMAND_RANGE = 48.0D;
    private static final String MODE_KEY = "FapCraftDragonStaffMode";
    /** Placeholder commands until the original's tribe radial menu is ported. */
    private enum StaffCommand {
        FOLLOW, STAY, HOME;

        String serializedName() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final List<StaffCommand> COMMAND_MODES = List.of(StaffCommand.values());

    public DragonStaffItem(Properties properties) {
        super(properties, "dragon_staff", 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level instanceof ServerLevel serverLevel) {
            if (player.isShiftKeyDown()) {
                int next = Math.floorMod(player.getPersistentData().getInt(MODE_KEY) + 1, COMMAND_MODES.size());
                player.getPersistentData().putInt(MODE_KEY, next);
                player.displayClientMessage(
                        Component.translatable(
                                "message.sexmod.staff_mode",
                                Component.translatable("mode.sexmod." + COMMAND_MODES.get(next).serializedName())
                        ),
                        true
                );
                return InteractionResultHolder.success(stack);
            }

            StaffCommand selectedMode = COMMAND_MODES.get(Math.floorMod(
                    player.getPersistentData().getInt(MODE_KEY),
                    COMMAND_MODES.size()
            ));
            List<CompanionEntity> kobolds = serverLevel.getEntitiesOfClass(
                    CompanionEntity.class,
                    player.getBoundingBox().inflate(COMMAND_RANGE),
                    companion -> companion.kind() == CompanionKind.KOBOLD
                            && companion.canUsePanel(player)
            );

            for (CompanionEntity kobold : kobolds) {
                switch (selectedMode) {
                    case FOLLOW -> kobold.setMaster(player.getUUID());
                    case STAY -> kobold.clearMaster();
                    case HOME -> kobold.startGoHome();
                }
            }

            player.displayClientMessage(
                    Component.translatable(
                            kobolds.isEmpty() ? "message.sexmod.staff_no_kobolds" : "message.sexmod.staff_applied",
                            kobolds.size(),
                            Component.translatable("mode.sexmod." + selectedMode.serializedName())
                    ),
                    true
            );

            if (!kobolds.isEmpty()) {
                serverLevel.playSound(
                        null,
                        player.blockPosition(),
                        SoundEvents.ENCHANTMENT_TABLE_USE,
                        SoundSource.PLAYERS,
                        0.7F,
                        1.0F + selectedMode.ordinal() * 0.08F
                );
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
