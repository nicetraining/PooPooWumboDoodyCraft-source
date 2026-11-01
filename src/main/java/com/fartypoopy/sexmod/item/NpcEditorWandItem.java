package com.fartypoopy.sexmod.item;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.registry.ModItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Original hj: the NPC editor wand. Right-clicking a girl opens the wardrobe (client), left-clicking her prints and copies
 * her model code; the wand never hurts her or breaks blocks. The wand looks "active" while aimed at a girl.
 */
public final class NpcEditorWandItem extends LegacyPurposeItem {
    public NpcEditorWandItem(Properties properties) {
        super(properties, "npc_editor_wand", 0);
    }

    public static boolean holding(Player player) {
        return player.getMainHandItem().is(ModItems.NPC_EDITOR_WAND.get()) || player.getOffhandItem().is(ModItems.NPC_EDITOR_WAND.get());
    }

    @EventBusSubscriber(modid = FapCraft.MOD_ID)
    public static final class Events {
        private Events() {
        }

        /** Original hj.a(EntityInteract): no girl menu, the wardrobe opens instead. */
        @SubscribeEvent
        public static void onInteract(PlayerInteractEvent.EntityInteract event) {
            if (!(event.getTarget() instanceof CompanionEntity girl) || !holding(event.getEntity())) {
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (event.getLevel().isClientSide) {
                com.fartypoopy.sexmod.client.WandClient.openWardrobe(girl);
            }
        }

        /** Original hj.a(AttackEntityEvent): the wand prints her model code instead of hitting her. */
        @SubscribeEvent
        public static void onAttack(AttackEntityEvent event) {
            if (!(event.getTarget() instanceof CompanionEntity girl) || !holding(event.getEntity())) {
                return;
            }
            event.setCanceled(true);
            if (event.getEntity().level().isClientSide) {
                com.fartypoopy.sexmod.client.WandClient.copyModelCode(girl);
            }
        }

        /** Original hj.a(LeftClickBlock): no block breaking with the wand. */
        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            if (holding(event.getEntity())) {
                event.setCanceled(true);
                if (event.getLevel().isClientSide && event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
                    com.fartypoopy.sexmod.client.WandClient.noPlayerGirl();
                }
            }
        }
    }
}
