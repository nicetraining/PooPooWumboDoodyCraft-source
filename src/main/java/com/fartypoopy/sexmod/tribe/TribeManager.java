package com.fartypoopy.sexmod.tribe;

import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Original ax static helpers: spawning tribes, claiming them, finding members, bed and chest halves. */
public final class TribeManager {
    public static final int GENERATED_MEMBERS = 4;
    private static final double[][] OFFSETS = {{0, 0}, {0.5, 0}, {-0.5, 0}, {0, 0.5}};

    private TribeManager() {
    }

    public static CompanionEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof CompanionEntity companion && companion.isAlive()) {
                return companion;
            }
        }
        return null;
    }

    public static List<CompanionEntity> loadedMembers(MinecraftServer server, Tribe tribe) {
        List<CompanionEntity> list = new ArrayList<>();
        for (UUID id : new ArrayList<>(tribe.members.keySet())) {
            CompanionEntity member = find(server, id);
            if (member != null && member.kind() == CompanionKind.KOBOLD) {
                list.add(member);
            }
        }
        return list;
    }

    /** Original ax.a(World, Vec3d): four unowned Kobolds of a random colour, member 0 is the big leader. */
    public static Tribe spawnTribe(ServerLevel level, Vec3 pos) {
        TribeData data = TribeData.get(level.getServer());
        RandomSource random = level.getRandom();
        UUID tribeId = UUID.randomUUID();
        KoboldColor color = KoboldColor.values()[random.nextInt(KoboldColor.values().length)];
        Tribe tribe = data.getOrCreate(tribeId, color);
        for (int i = 0; i < GENERATED_MEMBERS; i++) {
            float size = i == 0 ? 0.25F : random.nextFloat() * 0.25F;
            CompanionEntity kobold = newKobold(level, tribeId, size, color);
            if (kobold == null) {
                continue;
            }
            kobold.moveTo(pos.x + OFFSETS[i][0], pos.y, pos.z + OFFSETS[i][1], random.nextFloat() * 360.0F, 0.0F);
            tribe.members.put(kobold.getUUID(), kobold.blockPosition());
            if (i == 0) {
                tribe.leader = kobold.getUUID();
                kobold.setTribeLeader(true);
            }
            level.addFreshEntity(kobold);
        }
        data.touch();
        return tribe;
    }

    /** Original ff.a(world, uuid, size): max health 10 - 25 * size. */
    public static CompanionEntity newKobold(ServerLevel level, UUID tribeId, float size, KoboldColor color) {
        CompanionEntity kobold = ModEntities.get(CompanionKind.KOBOLD).create(level, null, BlockPos.ZERO, MobSpawnType.SPAWN_EGG, false, false);
        if (kobold == null) {
            return null;
        }
        kobold.setKoboldSize(size);
        kobold.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10.0D - size * 25.0D);
        kobold.setHealth(kobold.getMaxHealth());
        kobold.setTribeId(tribeId);
        kobold.setBodyColor(color);
        kobold.setAppearanceVariant(level.getRandom().nextInt(CompanionKind.KOBOLD.appearanceVariants()));
        return kobold;
    }

    /** g9: name and claim every unowned loaded member. Returns false when nothing was claimed. */
    public static boolean claim(MinecraftServer server, UUID playerId, String playerName, Vec3 playerPos, UUID tribeId, String rawName) {
        TribeData data = TribeData.get(server);
        Tribe tribe = data.tribe(tribeId);
        String name = rawName == null ? "" : rawName.strip();
        if (tribe == null || name.isEmpty() || name.length() > 15 || data.ofMaster(playerId) != null) {
            return false;
        }
        List<CompanionEntity> members = loadedMembers(server, tribe);
        boolean near = false;
        for (CompanionEntity member : members) {
            if (member.position().distanceToSqr(playerPos) < 40.0D * 40.0D) {
                near = true;
            }
        }
        if (!near) {
            return false;
        }
        KoboldColor color = tribe.color;
        boolean claimed = false;
        for (CompanionEntity member : members) {
            if (!member.hasMaster()) {
                member.setMaster(playerId);
                member.setTribeName(name);
                color = member.bodyColor();
                claimed = true;
            }
        }
        if (!claimed) {
            return false;
        }
        Component message = Component.literal(playerName + " formed the ")
                .append(Component.literal(name + " ").withStyle(color.chat()))
                .append(Component.literal("Tribe").withStyle(ChatFormatting.WHITE));
        server.getPlayerList().broadcastSystemMessage(message, false);
        tribe.follow = true;
        tribe.master = playerId;
        data.touch();
        return true;
    }

    // ---- bed and chest halves ----

    public static BlockPos bedPartner(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BedBlock)) {
            return null;
        }
        Direction facing = state.getValue(BedBlock.FACING);
        BlockPos other = state.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
        return level.getBlockState(other).getBlock() instanceof BedBlock ? other : null;
    }

    public static BlockPos chestPartner(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return null;
        }
        return pos.relative(ChestBlock.getConnectedDirection(state));
    }

    public static Set<BlockPos> halves(Level level, BlockPos pos, boolean bed) {
        Set<BlockPos> set = new HashSet<>();
        set.add(pos);
        BlockPos other = bed ? bedPartner(level, pos) : chestPartner(level, pos);
        if (other != null) {
            set.add(other);
        }
        return set;
    }

    /** Original h6 echo: tell the tribe owner which blocks to draw (or stop drawing) as marked. */
    public static void markers(ServerLevel level, Tribe tribe, Collection<BlockPos> blocks, boolean add) {
        if (tribe.master == null || blocks.isEmpty()) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tribe.master);
        if (owner != null) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(owner,
                    new com.fartypoopy.sexmod.network.TribeMarkersPayload(add, new ArrayList<>(blocks)));
        }
    }

    /** Everything a player's tribe has marked (login restore). */
    public static List<BlockPos> allMarked(Tribe tribe) {
        List<BlockPos> list = new ArrayList<>(tribe.beds);
        list.addAll(tribe.chests);
        for (TribeTask task : tribe.tasks) {
            list.addAll(task.blocks);
        }
        return list;
    }
}
