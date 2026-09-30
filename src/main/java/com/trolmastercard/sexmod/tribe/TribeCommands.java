package com.trolmastercard.sexmod.tribe;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.network.TribeCommandPayload;
import com.trolmastercard.sexmod.network.TribeUiPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server side of the staff / claim packets (original g9, h6, fj, au, fc, e6, b3). */
public final class TribeCommands {
    private TribeCommands() {
    }

    public static void handle(ServerPlayer player, TribeCommandPayload payload) {
        ServerLevel level = player.serverLevel();
        TribeData data = TribeData.get(player.server);
        if (payload.op() == TribeCommandPayload.CLAIM) {
            try {
                TribeManager.claim(player.server, player.getUUID(), player.getGameProfile().getName(), player.position(),
                        UUID.fromString(payload.a()), payload.b());
            } catch (IllegalArgumentException ignored) {
                // bad uuid
            }
            return;
        }
        Tribe tribe = data.ofMaster(player.getUUID());
        if (tribe == null) {
            if (payload.op() == TribeCommandPayload.REQUEST) {
                PacketDistributor.sendToPlayer(player, new TribeUiPayload(false, List.of()));
            }
            return;
        }
        switch (payload.op()) {
            case TribeCommandPayload.REQUEST -> sendUi(player, level, tribe);
            case TribeCommandPayload.FOLLOW -> tribe.follow = "1".equals(payload.a());
            case TribeCommandPayload.REGISTER -> register(player, level, data, tribe, payload.pos());
            case TribeCommandPayload.CANCEL -> cancel(player, level, data, tribe, payload.pos());
            case TribeCommandPayload.FELL -> fell(player, level, data, tribe, payload.pos());
            case TribeCommandPayload.MINE -> mine(player, level, data, tribe, payload.pos(), Direction.byName(payload.a()));
            default -> { }
        }
    }

    public static void sendUi(ServerPlayer player, ServerLevel level, Tribe tribe) {
        List<int[]> members = new ArrayList<>();
        Set<UUID> seen = new java.util.HashSet<>();
        for (CompanionEntity member : TribeManager.loadedMembers(player.server, tribe)) {
            seen.add(member.getUUID());
            members.add(entry(member.blockPosition(), member.bodyColor().woolMeta()));
        }
        tribe.members.forEach((uuid, pos) -> {
            if (!seen.contains(uuid)) {
                members.add(entry(pos, tribe.color.woolMeta()));
            }
        });
        PacketDistributor.sendToPlayer(player, new TribeUiPayload(tribe.follow, members));
    }

    private static int[] entry(BlockPos pos, int wool) {
        return new int[]{pos.getX(), pos.getY(), pos.getZ(), wool};
    }

    private static void register(ServerPlayer player, ServerLevel level, TribeData data, Tribe tribe, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean bed = state.getBlock() instanceof BedBlock;
        boolean chest = state.getBlock() instanceof ChestBlock;
        if (!bed && !chest) {
            return;
        }
        Set<BlockPos> halves = TribeManager.halves(level, pos, bed);
        if (bed && halves.size() < 2) {
            return; // a bed needs both halves
        }
        Set<BlockPos> set = bed ? tribe.beds : tribe.chests;
        boolean remove = set.contains(pos);
        if (remove) {
            set.removeAll(halves);
        } else {
            set.addAll(halves);
        }
        data.touch();
        TribeManager.markers(level, tribe, halves, !remove);
    }

    private static void cancel(ServerPlayer player, ServerLevel level, TribeData data, Tribe tribe, BlockPos pos) {
        TribeTask task = tribe.taskAt(pos);
        if (task == null) {
            return;
        }
        for (UUID worker : new ArrayList<>(task.workers)) {
            CompanionEntity kobold = TribeManager.find(player.server, worker);
            if (kobold != null && kobold.koboldBehavior() != null) {
                kobold.koboldBehavior().releaseFromTask(tribe);
            }
        }
        tribe.tasks.remove(task);
        data.touch();
        TribeManager.markers(level, tribe, task.blocks, false);
    }

    /** Every member needs a bed before the tribe works for you (original message, in two lines). */
    private static boolean bedsMissing(ServerPlayer player, Tribe tribe, boolean typo) {
        int members = tribe.members.size();
        int beds = tribe.beds.size() / 2;
        if (members <= beds) {
            return false;
        }
        player.sendSystemMessage(Component.literal(typo ? "sUr Tribe will only work for you, if " : "Ur Tribe will only work for you, if ")
                .append(Component.literal("everyone").withStyle(ChatFormatting.RED))
                .append(Component.literal(" of them has a bed").withStyle(ChatFormatting.WHITE)));
        player.sendSystemMessage(Component.literal(beds + "/" + members + " Beds").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    private static void fell(ServerPlayer player, ServerLevel level, TribeData data, Tribe tribe, BlockPos pos) {
        if (!level.getBlockState(pos).is(BlockTags.LOGS) || tribe.taskAt(pos) != null || bedsMissing(player, tribe, false)) {
            return;
        }
        TribeTask task = KoboldWork.treeTask(level, pos, tribe);
        tribe.tasks.add(task);
        data.touch();
        TribeManager.markers(level, tribe, task.blocks, true);
    }

    private static void mine(ServerPlayer player, ServerLevel level, TribeData data, Tribe tribe, BlockPos start, Direction dir) {
        if (dir == null || bedsMissing(player, tribe, true)) {
            return;
        }
        Set<BlockPos> blocks = tunnel(start, dir, level);
        for (BlockPos pos : blocks) {
            if (level.getBlockState(pos).getDestroySpeed(level, pos) < 0.0F) {
                player.displayClientMessage(Component.literal("This area contains Bedrock and cannot be mined"), true);
                return;
            }
        }
        if (blocks.isEmpty()) {
            return;
        }
        TribeTask task = new TribeTask(TribeTask.Kind.MINE, start, blocks, dir);
        tribe.tasks.add(task);
        data.touch();
        TribeManager.markers(level, tribe, blocks, true);
    }

    /** 30 cross sections of 3 x 3 blocks starting at {@code start}; air is skipped. */
    public static Set<BlockPos> tunnel(BlockPos start, Direction dir, Level level) {
        Set<BlockPos> blocks = new LinkedHashSet<>();
        int px = dir.getStepZ();
        int pz = -dir.getStepX();
        for (int i = 0; i < TribeTask.TUNNEL_LENGTH; i++) {
            BlockPos base = start.relative(dir, i);
            for (int height = 0; height < 3; height++) {
                for (int side = -1; side <= 1; side++) {
                    BlockPos pos = base.offset(px * side, height, pz * side);
                    if (!level.getBlockState(pos).isAir()) {
                        blocks.add(pos);
                    }
                }
            }
        }
        return blocks;
    }

    /** Original e(): can a tunnel start at the block being looked at? Returns the start position or null. */
    public static BlockPos mineStart(Level level, BlockPos target, Direction digDirection, double playerY) {
        BlockState state = level.getBlockState(target);
        boolean diggable = (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL))
                && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && state.getDestroySpeed(level, target) >= 0.0F;
        if (!diggable || playerY > target.getY() + 0.99D || digDirection.getAxis().isVertical()) {
            return null;
        }
        BlockPos start = target.relative(digDirection.getOpposite());
        if (!level.getBlockState(start).isAir()) {
            return null;
        }
        int drop = 0;
        BlockPos below = start.below();
        while (level.getBlockState(below).isAir() && drop <= 3) {
            drop++;
            below = below.below();
        }
        return drop <= 3 ? start : null;
    }
}
