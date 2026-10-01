package com.trolmastercard.sexmod.tribe;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The original ff: a Kobold has no AI goals to speak of, everything is scripted per tick. Owned Kobolds follow their
 * master (tribe follow mode), work assigned tasks by day, sleep in registered beds by night and fight anything that
 * attacks the tribe. Wild tribes walk behind a leader by day and camp around a fire at night.
 *
 * <p>Parts the original decompile could not recover (leader task dispatch, wild exploring) are reconstructed from the
 * callers and are marked "inferred" in remake-notes.md.</p>
 */
public final class KoboldBehavior {
    public static final int ST_NONE = 0;
    public static final int ST_ATTACK = 1;
    public static final int ST_SLEEP = 2;
    public static final int ST_WORK = 3;

    private static final String[] NAMES = {
            "Vags", "Snis", "Suk", "Snok", "Orpu", "Dovni", "Ahza", "Zarbu", "Rupli", "Kagri", "Nud", "Gox", "Vum",
            "Snek", "Aglo", "Givlu", "Gukle", "Vutu", "Evni", "Kakla", "Tuks", "Nev", "Kugs", "Sneks", "Vihli",
            "Snuppu", "Sogi", "Guldo", "Durbi", "Hikbu", "Guv", "San", "Ken", "Nern", "Zogni", "Ahze", "Snoblo",
            "Snoggi", "Nutro", "Vekda", "Morn", "Snogs", "Teg", "Tigs", "Rokko", "Oblu", "Tihzi", "Mohru", "Sahsu",
            "Mahlu"
    };
    private static final double WALK = 0.35D;
    private static final double RUN = 0.7D;
    private static final int ATTACK_COOLDOWN = 84;
    private static final int ATTACK_HIT_TICK = 32;
    private static final double LEAD_RADIUS = 30.0D;

    private final CompanionEntity k;
    private final KoboldWork work;
    private int healTicks;
    private int attackCooldown;
    private int attackAnim;
    private int interactTicks;
    private int syncTicks;
    private int scanTicks;
    private int eggCounter = -1;
    private boolean eggGiven;
    private int wanderTicks;
    private BlockPos campSpot;
    private BlockPos campFire;
    private BlockState campFloor;
    private BlockState campFireOldState;
    private boolean exploring;
    private BlockPos exploreTarget;
    private int bedWalkTicks;

    public KoboldBehavior(CompanionEntity k) {
        this.k = k;
        this.work = new KoboldWork(k);
    }

    public static String randomName(RandomSource random) {
        return NAMES[random.nextInt(NAMES.length)];
    }

    KoboldWork work() {
        return this.work;
    }

    public void releaseFromTask(Tribe tribe) {
        this.work.release(tribe);
    }

    public void onLoaded() {
        this.attackAnim = 0;
    }

    /** A player opened the action menu: stand still and face them for a while (the original's interacting-player flag). */
    public void interact(Player player) {
        this.interactTicks = 60;
        this.k.getNavigation().stop();
        this.k.getLookControl().setLookAt(player, 30.0F, 30.0F);
    }

    public boolean isBusy() {
        return this.interactTicks > 0;
    }

    /** Start of the mating finish: the egg is handed over 132 ticks later (original V counter). */
    public void startEggCounter() {
        this.eggCounter = 0;
    }

    public int eggCounter() {
        return this.eggCounter;
    }

    public void finishEggCounter() {
        this.eggCounter = -1;
    }

    // ------------------------------------------------------------------ tick

    public void tick(ServerLevel level) {
        this.tickEgg(level);
        if (this.k.isInScene() || this.k.isBedWalking()) {
            return;
        }
        TribeData data = TribeData.get(level.getServer());
        UUID tribeId = this.k.tribeId().orElse(null);
        Tribe tribe = tribeId == null ? null : data.getOrCreate(tribeId, this.k.bodyColor());

        if (tribe != null) {
            this.bookkeeping(level, data, tribe);
        }
        if (this.interactTicks > 0) {
            this.interactTicks--;
            this.k.getNavigation().stop();
            return;
        }
        this.heal(level);
        if (tribe == null) {
            return;
        }
        this.k.setKoboldUnderAttack(!tribe.enemies.isEmpty());
        this.k.setTribeLeader(this.k.getUUID().equals(tribe.leader));
        this.k.setTribeFollow(tribe.follow);
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.k.koboldState() == ST_ATTACK) {
            this.tickAttack(level, tribe);
            return;
        }
        this.decide(level, tribe);
    }

    private void bookkeeping(ServerLevel level, TribeData data, Tribe tribe) {
        // body colour follows the tribe colour
        if (this.k.bodyColor() != tribe.color) {
            this.k.setBodyColor(tribe.color);
        }
        if (this.k.isOwnedKobold() && tribe.master == null) {
            tribe.master = this.k.masterId().orElse(null);
            data.touch();
        }
        if (++this.syncTicks >= 20) {
            this.syncTicks = 0;
            BlockPos pos = this.k.blockPosition();
            if (!pos.equals(tribe.members.put(this.k.getUUID(), pos))) {
                data.touch();
            }
            this.pickLeader(level, tribe);
        }
    }

    /** Mating finish: 132 ticks into the cum phase the owner receives a Kobold Egg of this tribe (original V counter). */
    private void tickEgg(ServerLevel level) {
        boolean cum = this.k.currentScene() == com.trolmastercard.sexmod.entity.CompanionScene.KOBOLD_MATING_PRESS
                && this.k.currentScenePhase() == 3;
        if (!cum) {
            this.eggCounter = -1;
            this.eggGiven = false;
            return;
        }
        if (this.eggCounter < 0 && !this.eggGiven) {
            this.eggCounter = 0;
        }
        if (this.eggCounter >= 0 && ++this.eggCounter >= 132) {
            this.eggCounter = -1;
            this.eggGiven = true;
            UUID tribe = this.k.tribeId().orElse(null);
            ServerPlayer player = this.k.scenePartnerId().map(level.getServer().getPlayerList()::getPlayer).orElse(null);
            if (tribe != null && player != null) {
                net.minecraft.world.item.ItemStack egg = new net.minecraft.world.item.ItemStack(
                        com.trolmastercard.sexmod.registry.ModItems.KOBOLD_EGG_ITEM.get());
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, egg, tag -> {
                    tag.putString("tribeID", tribe.toString());
                    tag.putString("tribeColor", this.k.bodyColor().name());
                });
                player.getInventory().add(egg); // like the original: nothing drops when the inventory is full
            }
        }
    }

    /** Original a.b(): the alive member with the smallest size leads when there is no leader. */
    private void pickLeader(ServerLevel level, Tribe tribe) {
        CompanionEntity current = tribe.leader == null ? null : TribeManager.find(level.getServer(), tribe.leader);
        if (current != null && current.tribeId().filter(tribe.id::equals).isPresent()) {
            return;
        }
        CompanionEntity best = null;
        for (CompanionEntity member : TribeManager.loadedMembers(level.getServer(), tribe)) {
            if (member.tribeId().filter(tribe.id::equals).isPresent() && (best == null || member.koboldSize() < best.koboldSize())) {
                best = member;
            }
        }
        tribe.leader = best == null ? null : best.getUUID();
    }

    private void heal(ServerLevel level) {
        if (!this.k.koboldUnderAttack() && this.k.getHealth() < this.k.getMaxHealth()) {
            if (++this.healTicks >= 100) {
                this.k.heal(2.0F);
                this.healTicks = 0;
                level.sendParticles(ParticleTypes.HEART, this.k.getX(), this.k.getY() + this.k.getBbHeight(), this.k.getZ(),
                        3, 0.25D, 0.25D, 0.25D, 0.0D);
            }
        } else {
            this.healTicks = 0;
        }
    }

    // ------------------------------------------------------------------ decision tree

    private Player owner(ServerLevel level) {
        return this.k.masterId().map(level::getPlayerByUUID).orElse(null);
    }

    private void decide(ServerLevel level, Tribe tribe) {
        boolean underAttack = this.k.koboldUnderAttack();
        if (this.k.isOwnedKobold() && tribe.follow && !underAttack) {
            this.work.release(tribe);
            this.wake(tribe);
            this.followOwner(level);
            return;
        }
        if (!this.k.isOwnedKobold() && tribe.follow && !underAttack) {
            return; // wild Kobolds ignore follow mode
        }
        boolean leader = this.k.getUUID().equals(tribe.leader);
        if (leader) {
            Tribe.Mode before = tribe.mode;
            tribe.mode = level.getDayTime() % 24000L < 12000L ? Tribe.Mode.ACTIVE : Tribe.Mode.REST;
            if (before == Tribe.Mode.ACTIVE && tribe.mode == Tribe.Mode.REST) {
                this.k.sayNearby(level, Component.literal("okay resting time owo")); // original: the leader announces dusk
            }
        }
        if (this.combat(level, tribe)) {
            return;
        }
        if (this.lureToStaff(level, tribe)) {
            return;
        }
        if (tribe.mode == Tribe.Mode.REST) {
            this.work.release(tribe);
            if (this.k.isOwnedKobold()) {
                this.sleepInBed(level, tribe);
            } else {
                this.camp(level, tribe, leader);
            }
            return;
        }
        this.wake(tribe);
        if (this.k.isOwnedKobold()) {
            this.ownedDay(level, tribe);
        } else {
            this.wildDay(level, tribe, leader);
        }
    }

    private void followOwner(ServerLevel level) {
        Player owner = this.owner(level);
        if (owner == null) {
            return;
        }
        double distance = this.k.distanceTo(owner);
        if (distance > 2.0D) {
            double speed = (owner.isSprinting() ? RUN : WALK) + Math.floor(distance / 5.0D) * 0.3D;
            if (this.k.isInWater()) {
                speed *= 60.0D; // original ff.a: sixty times faster in water
            }
            this.k.getNavigation().moveTo(owner, speed);
        } else {
            this.k.getNavigation().stop();
        }
        if (distance > 15.0D) {
            this.teleportNear(level, owner);
        }
    }

    private void teleportNear(ServerLevel level, Player player) {
        RandomSource random = this.k.getRandom();
        for (int attempt = 0; attempt < 20; attempt++) {
            double x = player.getX() + random.nextInt(10);
            double z = player.getZ() + random.nextInt(10);
            BlockPos pos = BlockPos.containing(x, player.getY(), z);
            if (level.noCollision(this.k, this.k.getBoundingBox().move(x - this.k.getX(), player.getY() - this.k.getY(), z - this.k.getZ()))) {
                this.k.teleportTo(x, player.getY(), z);
                this.k.setDeltaMovement(Vec3.ZERO);
                this.k.getNavigation().stop();
                return;
            }
        }
        this.k.teleportTo(player.getX(), player.getY(), player.getZ());
        this.k.setDeltaMovement(Vec3.ZERO);
        this.k.getNavigation().stop();
    }

    // ------------------------------------------------------------------ owned day / night

    private void ownedDay(ServerLevel level, Tribe tribe) {
        if (!this.work.work(level, tribe)) {
            this.work.idle(level, tribe);
        }
    }

    private void wake(Tribe tribe) {
        if (this.k.koboldState() == ST_SLEEP) {
            this.k.setKoboldState(ST_NONE);
            this.k.setNoGravity(false);
            this.k.noPhysics = false;
        }
        tribe.sleeping.remove(this.k.getUUID());
        this.bedWalkTicks = 0;
    }

    /** Original i(tribe). */
    private void sleepInBed(ServerLevel level, Tribe tribe) {
        BlockPos[] assigned = tribe.sleeping.get(this.k.getUUID());
        if (assigned != null) {
            double x = (assigned[0].getX() + assigned[1].getX()) / 2.0D + 0.5D;
            double z = (assigned[0].getZ() + assigned[1].getZ()) / 2.0D + 0.5D;
            double y = assigned[0].getY() + 0.5625D;
            this.k.setKoboldState(ST_SLEEP);
            this.k.setNoGravity(true);
            this.k.noPhysics = true;
            this.k.getNavigation().stop();
            this.k.setDeltaMovement(Vec3.ZERO);
            this.k.setPos(x, y, z);
            float yaw = assigned[0].getX() == assigned[1].getX() ? 0.0F : 90.0F;
            this.k.setYRot(yaw);
            this.k.yBodyRot = yaw;
            this.k.yHeadRot = yaw;
            return;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : tribe.beds) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.OCCUPIED) || tribe.bedAssigned(pos)) {
                continue;
            }
            double distance = this.k.position().distanceToSqr(Vec3.atCenterOf(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos;
            }
        }
        if (best == null) {
            this.work.wander(level);
            return;
        }
        this.bedWalkTicks++;
        if (bestDistance <= 4.0D) {
            BlockPos partner = TribeManager.bedPartner(level, best);
            if (partner != null) {
                tribe.sleeping.put(this.k.getUUID(), new BlockPos[]{best, partner});
            }
            return;
        }
        Vec3 bed = Vec3.atCenterOf(best);
        if (Math.abs(this.k.getY() - bed.y) > 4.0D || this.bedWalkTicks > 400
                || !this.k.getNavigation().moveTo(bed.x, bed.y, bed.z, WALK) && this.k.getNavigation().isDone()) {
            this.work.portalTeleport(level, bed.add(0.0D, 0.0D, 0.0D));
            this.bedWalkTicks = 0;
        }
    }

    // ------------------------------------------------------------------ wild tribes

    /** Night: gather around a camp fire the leader lights. */
    private void camp(ServerLevel level, Tribe tribe, boolean leader) {
        if (tribe.home == null && leader) {
            BlockPos pos = this.k.blockPosition().relative(Direction.EAST);
            BlockPos floor = pos.below();
            if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
                this.campFloor = level.getBlockState(floor);
                this.campFireOldState = level.getBlockState(pos);
                level.setBlock(floor, Blocks.NETHERRACK.defaultBlockState(), 3);
                level.setBlock(pos, ModBlocks.FIRE.get().defaultBlockState(), 3);
                this.campFire = pos;
                tribe.home = pos;
            }
        }
        if (tribe.home == null) {
            return;
        }
        if (this.campSpot == null) {
            RandomSource random = this.k.getRandom();
            this.campSpot = tribe.home.offset((random.nextBoolean() ? 1 : -1) * (random.nextInt(2) + 1), 0,
                    (random.nextBoolean() ? 1 : -1) * (random.nextInt(2) + 1)); // original: never on the fire
        }
        if (this.k.position().distanceToSqr(Vec3.atBottomCenterOf(this.campSpot)) > 2.0D) {
            this.k.getNavigation().moveTo(this.campSpot.getX() + 0.5D, this.campSpot.getY(), this.campSpot.getZ() + 0.5D, WALK);
        } else {
            this.k.getNavigation().stop();
        }
    }

    /** Restores the blocks under the camp fire (original WorldEvent.Unload handler). */
    public void removeCamp(ServerLevel level, Tribe tribe) {
        if (this.campFire != null) {
            if (level.getBlockState(this.campFire).is(ModBlocks.FIRE.get())) {
                level.setBlock(this.campFire, this.campFireOldState == null ? Blocks.AIR.defaultBlockState() : this.campFireOldState, 3);
            }
            if (this.campFloor != null) {
                level.setBlock(this.campFire.below(), this.campFloor, 3);
            }
            this.campFire = null;
            tribe.home = null;
        }
    }

    /** Inferred leader AI (decompile failed): explore, then work the trees around, repeat. */
    private void wildDay(ServerLevel level, Tribe tribe, boolean leader) {
        this.campSpot = null;
        if (tribe.home != null && leader) {
            this.removeCamp(level, tribe);
            tribe.home = null;
        }
        if (this.work.work(level, tribe)) {
            return;
        }
        if (leader) {
            if (this.exploreTarget == null || this.k.blockPosition().closerThan(this.exploreTarget, 5.0D)) {
                if (this.exploreTarget != null && !this.exploring) {
                    this.exploring = true;
                    this.k.sayNearby(level, Component.literal("Time to work bitches!"));
                    this.assignTrees(level, tribe);
                } else if (this.exploring && tribe.tasks.isEmpty()) {
                    this.exploring = false;
                    this.k.sayNearby(level, Component.literal("Lets go somewhere else"));
                    this.pickExploreTarget(level);
                } else if (this.exploreTarget == null) {
                    this.pickExploreTarget(level);
                }
            }
            if (this.exploreTarget != null && !this.exploring) {
                this.k.getNavigation().moveTo(this.exploreTarget.getX() + 0.5D, this.exploreTarget.getY(), this.exploreTarget.getZ() + 0.5D, WALK);
            } else {
                this.work.wander(level);
            }
            return;
        }
        CompanionEntity leaderEntity = tribe.leader == null ? null : TribeManager.find(level.getServer(), tribe.leader);
        if (leaderEntity == null) {
            this.work.wander(level);
            return;
        }
        double distance = this.k.distanceTo(leaderEntity);
        if (distance > 20.0D) {
            this.k.teleportTo(leaderEntity.getX(), leaderEntity.getY(), leaderEntity.getZ());
        } else if (distance > 4.0D) {
            this.k.getNavigation().moveTo(leaderEntity, WALK);
        } else {
            this.work.wander(level);
        }
    }

    private void pickExploreTarget(ServerLevel level) {
        RandomSource random = this.k.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = 25.0D + random.nextDouble() * 20.0D;
        int x = (int) (this.k.getX() + Math.cos(angle) * distance);
        int z = (int) (this.k.getZ() + Math.sin(angle) * distance);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        this.exploreTarget = new BlockPos(x, y, z);
    }

    /** Original c(tribe, tasks): one fell-tree task per follower, trees within 30 blocks (4 vertically). */
    private void assignTrees(ServerLevel level, Tribe tribe) {
        int wanted = Math.max(0, tribe.members.size() - 1);
        BlockPos center = this.k.blockPosition();
        for (int i = 0; i < wanted; i++) {
            BlockPos found = null;
            outer:
            for (int r = 0; r <= 30; r++) {
                for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
                    if (Math.max(Math.abs(pos.getX() - center.getX()), Math.abs(pos.getZ() - center.getZ())) != r) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    BlockState below = level.getBlockState(pos.below());
                    if (state.is(BlockTags.LOGS) && !below.is(BlockTags.LOGS) && !below.isAir() && tribe.taskAt(pos) == null) {
                        found = pos.immutable();
                        break outer;
                    }
                }
            }
            if (found == null) {
                return;
            }
            tribe.tasks.add(KoboldWork.treeTask(level, found, tribe));
            this.k.sayNearby(level, Component.literal("Someone, go fall this tree!"));
        }
    }

    /** Wild Kobolds gather around a player holding a Dragon Staff. */
    private boolean lureToStaff(ServerLevel level, Tribe tribe) {
        if (this.k.isOwnedKobold() || this.k.koboldUnderAttack()) {
            return false;
        }
        Player nearest = null;
        double best = 10.0D * 10.0D;
        // original t(): the distance is the minimum over all tribe members, so the whole tribe answers together
        java.util.List<CompanionEntity> members = TribeManager.loadedMembers(level.getServer(), tribe);
        for (Player player : level.players()) {
            if (!holdsStaff(player)) {
                continue;
            }
            double distance = player.distanceToSqr(this.k);
            for (CompanionEntity member : members) {
                distance = Math.min(distance, player.distanceToSqr(member));
            }
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest == null) {
            return false;
        }
        this.k.getNavigation().stop();
        this.k.getLookControl().setLookAt(nearest, 30.0F, 30.0F);
        if (this.k.distanceTo(nearest) > 2.0D) {
            this.k.getNavigation().moveTo(nearest, WALK);
        }
        return true;
    }

    public static boolean holdsStaff(Player player) {
        return player.getMainHandItem().getItem() instanceof com.trolmastercard.sexmod.item.DragonStaffItem
                || player.getOffhandItem().getItem() instanceof com.trolmastercard.sexmod.item.DragonStaffItem;
    }

    // ------------------------------------------------------------------ combat

    private boolean combat(ServerLevel level, Tribe tribe) {
        if (++this.scanTicks >= 10) {
            this.scanTicks = 0;
            this.scanTribeEnemies(level, tribe);
        }
        net.minecraft.world.entity.Entity leaderEntity = level.getEntity(tribe.leader);
        tribe.enemies.removeIf(id -> {
            net.minecraft.world.entity.Entity entity = level.getEntity(id);
            // original c(UUID, Z): only enemies within 30 blocks of the leader count
            return !(entity instanceof LivingEntity living) || !living.isAlive()
                    || (leaderEntity != null && living.distanceTo(leaderEntity) > 30.0F);
        });
        this.k.setKoboldUnderAttack(!tribe.enemies.isEmpty());
        if (tribe.enemies.isEmpty()) {
            return false;
        }
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID id : tribe.enemies) {
            if (level.getEntity(id) instanceof LivingEntity living) {
                double distance = living.distanceToSqr(this.k);
                if (distance < best) {
                    best = distance;
                    nearest = living;
                }
            }
        }
        if (nearest == null) {
            return false;
        }
        this.work.release(tribe);
        this.wake(tribe);
        if (this.k.distanceTo(nearest) > 1.5D) {
            this.k.getNavigation().moveTo(nearest, RUN);
            return true;
        }
        if (this.attackCooldown > 0) {
            return true;
        }
        this.k.getNavigation().stop();
        this.k.getLookControl().setLookAt(nearest, 60.0F, 60.0F);
        this.k.setKoboldState(ST_ATTACK);
        this.attackCooldown = ATTACK_COOLDOWN;
        this.attackAnim = 0;
        return true;
    }

    /** Original c(tribe): other Kobolds in sight within 30 blocks of the leader count as enemies unless both are owned. */
    private void scanTribeEnemies(ServerLevel level, Tribe tribe) {
        CompanionEntity leader = tribe.leader == null ? this.k : TribeManager.find(level.getServer(), tribe.leader);
        if (leader == null) {
            leader = this.k;
        }
        for (CompanionEntity other : level.getEntitiesOfClass(CompanionEntity.class, leader.getBoundingBox().inflate(LEAD_RADIUS),
                e -> e != this.k && e.koboldBehavior() != null && e.isAlive())) {
            boolean bothOwned = this.k.isOwnedKobold() && other.isOwnedKobold();
            boolean sameTribe = other.tribeId().isPresent() && other.tribeId().equals(this.k.tribeId());
            if (!bothOwned && !sameTribe && this.k.hasLineOfSight(other)) {
                tribe.enemies.add(other.getUUID());
            }
        }
    }

    private void tickAttack(ServerLevel level, Tribe tribe) {
        this.k.getNavigation().stop();
        this.attackAnim++;
        if (this.attackAnim == ATTACK_HIT_TICK) {
            DamageSource source = level.damageSources().mobAttack(this.k);
            for (UUID id : new ArrayList<>(tribe.enemies)) {
                if (level.getEntity(id) instanceof LivingEntity enemy && enemy.distanceTo(this.k) <= 2.0F) {
                    enemy.hurt(source, 5.0F);
                    if (!enemy.isAlive()) {
                        tribe.enemies.remove(id);
                    }
                }
            }
        }
        if (this.attackAnim >= ATTACK_COOLDOWN) {
            this.attackAnim = 0;
            this.k.setKoboldState(ST_NONE);
        }
    }

    /** LivingHurtEvent hook: something hurt a member of this tribe. */
    public void attacked(ServerLevel level, LivingEntity attacker) {
        TribeData data = TribeData.get(level.getServer());
        Tribe tribe = this.k.tribeId().map(data::tribe).orElse(null);
        if (tribe == null || attacker == this.k) {
            return;
        }
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator()
                || this.k.masterId().filter(player.getUUID()::equals).isPresent())) {
            return;
        }
        if (attacker instanceof CompanionEntity other && other.tribeId().equals(this.k.tribeId())) {
            return;
        }
        tribe.enemies.add(attacker.getUUID());
        if (tribe.master != null) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tribe.master);
            if (owner != null) {
                owner.displayClientMessage(Component.literal("Your Tribe is under Attack!").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** Death hook (original func_70645_a). */
    public void died(ServerLevel level) {
        TribeData data = TribeData.get(level.getServer());
        Tribe tribe = this.k.tribeId().map(data::tribe).orElse(null);
        if (tribe == null) {
            return;
        }
        this.work.release(tribe);
        tribe.members.remove(this.k.getUUID());
        tribe.sleeping.remove(this.k.getUUID());
        if (this.k.getUUID().equals(tribe.leader)) {
            tribe.leader = null;
        }
        data.touch();
        ServerPlayer owner = tribe.master == null ? null : level.getServer().getPlayerList().getPlayer(tribe.master);
        if (owner == null) {
            return;
        }
        if (TribeManager.loadedMembers(level.getServer(), tribe).stream().noneMatch(m -> m != this.k && m.isAlive())
                && this.k.isOwnedKobold() && tribe.members.isEmpty()) {
            owner.sendSystemMessage(Component.literal("ur ").append(Component.literal("tribe ").withStyle(ChatFormatting.RED))
                    .append(Component.literal("has been ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("eradicated ").withStyle(ChatFormatting.RED))
                    .append(Component.literal("uwu").withStyle(ChatFormatting.WHITE)));
            TribeManager.markers(level, tribe, TribeManager.allMarked(tribe), false);
        }
        if (this.k.isOwnedKobold()) {
            owner.sendSystemMessage(Component.literal(this.k.koboldName()).withStyle(ChatFormatting.RED)
                    .append(Component.literal(" has perished ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("uwu").withStyle(ChatFormatting.RED)));
        }
    }

    static boolean isPickaxeLike(net.minecraft.world.item.ItemStack stack) {
        return stack.is(Items.IRON_PICKAXE);
    }
}
