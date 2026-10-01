package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.data.GalathBindingData;
import com.trolmastercard.sexmod.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/**
 * Original f_$a on PlayerWakeUpEvent: a player who owns a Manglelie is woken by Galath and her daughter when he leaves his
 * bed, provided there is room beside the bed. Galath is replaced by a fresh one that starts the morning scene on the bed.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID)
public final class GalathMorning {
    private GalathMorning() {
    }

    /** Original aS: blocks she can stand in. */
    private static boolean passable(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof AirBlock || block instanceof CarpetBlock || block instanceof BushBlock
                || block instanceof ButtonBlock || block instanceof LadderBlock || block instanceof TorchBlock
                || block instanceof SignBlock || block instanceof BannerBlock;
    }

    /** Original a(World, BlockPos, EnumFacing): two cells (and the cells above them) beside the bed on its right. */
    public static boolean hasSpace(Level level, BlockPos pos, Direction facing) {
        BlockPos side;
        Direction along;
        switch (facing) {
            case NORTH -> {
                side = pos.west();
                along = Direction.SOUTH;
            }
            case WEST -> {
                side = pos.south();
                along = Direction.EAST;
            }
            case SOUTH -> {
                side = pos.east();
                along = Direction.NORTH;
            }
            case EAST -> {
                side = pos.north();
                along = Direction.WEST;
            }
            default -> {
                return false;
            }
        }
        return passable(level, side) && passable(level, side.above())
                && passable(level, side.relative(along)) && passable(level, side.relative(along).above());
    }

    private static float yawFor(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case EAST -> -90.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        GalathBindingData data = GalathBindingData.get(level.getServer());
        if (!data.hasMang(player.getUUID()) || !data.morningReady(player.getUUID(), level.getGameTime())) {
            return;
        }
        BlockPos bed = player.getSleepingPos().orElse(null);
        if (bed == null) {
            return;
        }
        BlockState state = level.getBlockState(bed);
        if (!(state.getBlock() instanceof BedBlock)) {
            return;
        }
        Direction facing = state.getValue(BedBlock.FACING);
        if (!hasSpace(level, bed, facing)) {
            player.sendSystemMessage(Component.translatable("message.sexmod.morning_space",
                    Component.translatable("message.sexmod.morning_space.wake").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("message.sexmod.morning_space.side").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("message.sexmod.morning_space.halves").withStyle(ChatFormatting.DARK_RED))
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        float yaw = yawFor(facing);
        Vec3 bedPos = new Vec3(bed.getX() + 0.5D, bed.getY(), bed.getZ() + 0.5D);
        data.activeFor(player.getUUID()).ifPresent(id -> {
            if (level.getEntity(id) instanceof CompanionEntity old) {
                ManglelieBehavior.removeDaughter(level, old);
                old.discard();
            }
            data.clear(player.getUUID());
        });
        CompanionEntity galath = ModEntities.get(CompanionKind.GALATH).create(level);
        if (galath == null) {
            return;
        }
        galath.moveTo(player.getX(), player.getY(), player.getZ(), yaw, 0.0F);
        galath.markSummonedByGalathCoin();
        galath.setMaster(player.getUUID());
        level.addFreshEntity(galath);
        data.bind(player.getUUID(), galath.getUUID());
        ManglelieBehavior.spawnDaughter(level, galath);
        galath.setForcedSceneAnchor(bedPos, yaw);
        if (!galath.startWildScene(player, CompanionScene.GALATH_MORNING)) {
            ManglelieBehavior.removeDaughter(level, galath);
            galath.discard();
            data.clear(player.getUUID());
            return;
        }
        // original: 500 ms later the player is placed on the bed and looks at them (pitch -10, yaw facing + 185)
        player.getServer().execute(() -> player.teleportTo(level, player.getX(), player.getY(), player.getZ(),
                yaw + 185.0F, -10.0F));
    }
}
