package com.fartypoopy.sexmod.tribe;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.network.TribeMarkersPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/** Original ff.c / ax.b / hy.a event handlers. */
@EventBusSubscriber(modid = FapCraft.MOD_ID)
public final class TribeEvents {
    private record PendingTribe(ServerLevel level, int x, int z) {
    }

    private static final Queue<PendingTribe> PENDING = new ArrayDeque<>();

    private TribeEvents() {
    }

    /** Original g3: 0.004 of newly generated chunks get a wild tribe at the chunk centre (not in flat worlds). */
    @SubscribeEvent
    public static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (!com.fartypoopy.sexmod.Config.SHOULD_GEN_BUILDINGS.get() || !event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level) || level.isFlat()
                || level.dimensionType().hasCeiling() || level.dimension() == net.minecraft.world.level.Level.END) {
            return;
        }
        if (level.getRandom().nextDouble() > 0.004D) {
            return;
        }
        var chunk = event.getChunk().getPos();
        PENDING.add(new PendingTribe(level, chunk.getMinBlockX() + 8, chunk.getMinBlockZ() + 8));
    }

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        PendingTribe next = PENDING.poll();
        if (next == null || !next.level().hasChunk(next.x() >> 4, next.z() >> 4)) {
            return;
        }
        int y = next.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, next.x(), next.z());
        BlockPos ground = new BlockPos(next.x(), y - 1, next.z());
        if (next.level().getBlockState(ground).liquid() || !next.level().getBlockState(ground).isSolid()) {
            return;
        }
        TribeManager.spawnTribe(next.level(), new net.minecraft.world.phys.Vec3(next.x() + 0.5D, y, next.z() + 0.5D));
    }

    /** Original aa: monsters go for owned Kobolds (zombie, skeleton every tick; a spider only in the dark, one check in ten). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Monster monster)) {
            return;
        }
        boolean zombie = monster instanceof Zombie;
        boolean skeleton = monster instanceof AbstractSkeleton;
        boolean spider = monster instanceof Spider;
        if (!zombie && !skeleton && !spider) {
            return;
        }
        monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, CompanionEntity.class, spider ? 10 : 0, true, false,
                target -> target instanceof CompanionEntity kobold && kobold.kind() == CompanionKind.KOBOLD && kobold.isOwnedKobold()
                        && (!spider || monster.getLightLevelDependentMagicValue() < 0.5F)));
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof CompanionEntity kobold) || kobold.koboldBehavior() == null
                || !(kobold.level() instanceof ServerLevel level)) {
            return;
        }
        if (event.getSource().is(DamageTypes.IN_WALL)) {
            event.setCanceled(true); // working Kobolds clip into walls: cancel and lift them out
            kobold.setPos(kobold.getX(), kobold.getY() + 1.0D, kobold.getZ());
            return;
        }
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            kobold.koboldBehavior().attacked(level, attacker);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof CompanionEntity kobold && kobold.koboldBehavior() != null
                && kobold.level() instanceof ServerLevel level) {
            kobold.koboldBehavior().died(level);
        }
    }

    /** A bed a Kobold is sleeping in cannot be used. */
    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        for (Tribe tribe : TribeData.get(level.getServer()).all()) {
            if (tribe.bedAssigned(pos)) {
                event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
                return;
            }
        }
    }

    /** With a Dragon Staff in hand beds and chests are not used: the staff registers them instead. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        com.fartypoopy.sexmod.item.KoboldEggItem.rightClickBlock(event);
        if (!KoboldBehavior.holdsStaff(event.getEntity())) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof BedBlock || state.getBlock() instanceof ChestBlock) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** A chest placed next to a registered chest joins the tribe. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlacedBlock().getBlock() instanceof ChestBlock)) {
            return;
        }
        BlockPos pos = event.getPos();
        TribeData data = TribeData.get(level.getServer());
        for (Tribe tribe : data.all()) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (tribe.chests.contains(pos.relative(dir))) {
                    // the new half merges into the registered double chest on the next tick, so just take both
                    tribe.chests.add(pos);
                    data.touch();
                    TribeManager.markers(level, tribe, List.of(pos), true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean bed = state.getBlock() instanceof BedBlock;
        boolean chest = state.getBlock() instanceof ChestBlock;
        if (!bed && !chest) {
            return;
        }
        TribeData data = TribeData.get(level.getServer());
        for (Tribe tribe : data.all()) {
            var set = bed ? tribe.beds : tribe.chests;
            if (set.contains(pos)) {
                var halves = TribeManager.halves(level, pos, bed);
                set.removeAll(halves);
                data.touch();
                TribeManager.markers(level, tribe, halves, false);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Tribe tribe = TribeData.get(player.server).ofMaster(player.getUUID());
        if (tribe != null) {
            List<BlockPos> marked = TribeManager.allMarked(tribe);
            if (!marked.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new TribeMarkersPayload(true, marked));
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            restoreCamps(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            restoreCamps(level);
        }
    }

    private static void restoreCamps(ServerLevel level) {
        for (Tribe tribe : TribeData.get(level.getServer()).all()) {
            if (tribe.leader != null && level.getEntity(tribe.leader) instanceof CompanionEntity leader
                    && leader.koboldBehavior() != null) {
                leader.koboldBehavior().removeCamp(level, tribe);
            }
        }
    }
}
