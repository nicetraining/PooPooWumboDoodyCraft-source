package com.fartypoopy.sexmod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Shared tooltip support for the reconstructed legacy utility items.
 */
public class LegacyPurposeItem extends Item {
    private final String tooltipKey;
    private final int tooltipLines;

    public LegacyPurposeItem(Properties properties, String tooltipKey, int tooltipLines) {
        super(properties);
        this.tooltipKey = tooltipKey;
        this.tooltipLines = tooltipLines;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);

        for (int line = 1; line <= this.tooltipLines; line++) {
            tooltip.add(Component.translatable(
                    "tooltip.sexmod." + this.tooltipKey + "." + line
            ).withStyle(line == 1 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}
