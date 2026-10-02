package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.FapCraft;
import net.neoforged.bus.api.SubscribeEvent;
import com.trolmastercard.sexmod.data.GalathBindingData;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Damage rules that apply to companions from outside their own class. */
@EventBusSubscriber(modid = FapCraft.MOD_ID)
public final class CompanionEvents {
    private CompanionEvents() {
    }

    /** Original cc.PlayerChangedDimensionEvent: Galath goes back into the coin when her owner changes dimension. */
    @SubscribeEvent
    public static void onDimensionChange(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            var server = player.getServer();
            carryGoblinAcross(event, player);
            var bindings = com.trolmastercard.sexmod.data.GalathBindingData.get(server);
            bindings.activeFor(player.getUUID()).ifPresent(id -> {
                for (var level : server.getAllLevels()) {
                    if (level.getEntity(id) instanceof CompanionEntity galath) {
                        galath.discard();
                    }
                }
                bindings.clear(player.getUUID());
            });
        }
    }

    /** Original e3$c: a goblin on the player's shoulder is re-created in the new dimension and the old one removed. */
    private static void carryGoblinAcross(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event,
                                          net.minecraft.server.level.ServerPlayer player) {
        var server = player.getServer();
        var from = server.getLevel(event.getFrom());
        if (from == null || !(player.level() instanceof net.minecraft.server.level.ServerLevel to)) {
            return;
        }
        java.util.List<CompanionEntity> carried = new java.util.ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : from.getAllEntities()) {
            if (entity instanceof CompanionEntity goblin && goblin.goblin() != null
                    && goblin.goblinCarrier().filter(player.getUUID()::equals).isPresent()
                    && goblin.goblinState() == GoblinBehavior.SHOULDER) {
                carried.add(goblin);
            }
        }
        for (CompanionEntity old : carried) {
            CompanionEntity copy = com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(to);
            if (copy == null) {
                continue;
            }
            copy.setGoblinModel(old.goblinModel());
            if (old.hasCustomName()) {
                copy.setCustomName(old.getCustomName());
            }
            copy.setGoblinTamed(true);
            copy.setGoblinCarrier(player.getUUID());
            copy.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            to.addFreshEntity(copy);
            copy.setGoblinState(GoblinBehavior.SHOULDER);
            old.setGoblinCarrier(null);
            old.discard();
        }
    }

    /** Original ey: a bed next to a girl who is in a scene cannot be broken. */
    @SubscribeEvent
    public static void onBedBreak(BlockEvent.BreakEvent event) {
        if (!event.getState().is(net.minecraft.tags.BlockTags.BEDS) || !(event.getLevel() instanceof net.minecraft.world.level.Level level)) {
            return;
        }
        var area = new net.minecraft.world.phys.AABB(event.getPos()).inflate(3.0D);
        if (!level.getEntitiesOfClass(CompanionEntity.class, area, CompanionEntity::isInScene).isEmpty()) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "this bed is currently used by a girl.. pls don't disturb okay? ... you are kinda mean rn"), true);
        }
    }

    /**
     * Original co: an animal that gets the horny effect falls in love with the nearest player within 30 blocks at
     * once (adults only; the effect is dropped from every animal), and a villager gets a one-off breeding goal.
     */
    @SubscribeEvent
    public static void onAnimalTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.npc.Villager villager
                && villager.hasEffect(com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT)) {
            villager.goalSelector.addGoal(2, new VillagerBreedGoal(villager));
            villager.removeEffect(com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT);
        }
        if (event.getEntity() instanceof net.minecraft.world.entity.animal.Animal animal
                && animal.hasEffect(com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT)) {
            if (animal.getAge() >= 0) {
                animal.setAge(0);
                animal.resetLove();
                animal.setInLove(animal.level().getNearestPlayer(animal, 30.0D));
            }
            animal.removeEffect(com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT);
        }
    }

    /** Original cw: walks to the nearest villager within 8x3x8 and, after 300 ticks, a baby is born. */
    private static final class VillagerBreedGoal extends net.minecraft.world.entity.ai.goal.Goal {
        private final net.minecraft.world.entity.npc.Villager self;
        private net.minecraft.world.entity.npc.Villager partner;
        private int timer;

        private VillagerBreedGoal(net.minecraft.world.entity.npc.Villager self) {
            this.self = self;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.timer != 0) {
                return false;
            }
            this.partner = this.self.level().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                            this.self.getBoundingBox().inflate(8.0D, 3.0D, 8.0D), other -> other != this.self)
                    .stream().findFirst().orElse(null);
            return this.partner != null;
        }

        @Override
        public void start() {
            this.timer = 300;
        }

        @Override
        public boolean canContinueToUse() {
            return true;
        }

        @Override
        public void tick() {
            this.timer--;
            this.self.getLookControl().setLookAt(this.partner, 10.0F, 30.0F);
            if (this.self.distanceToSqr(this.partner) > 2.25D) {
                this.self.getNavigation().moveTo(this.partner, 0.25D);
            }
            if (this.timer <= 0) {
                this.breed();
                this.self.goalSelector.removeGoal(this);
            }
            if (this.self.getRandom().nextInt(35) == 0) {
                this.self.level().broadcastEntityEvent(this.self, (byte) 12);
            }
        }

        private void breed() {
            if (!(this.self.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
                return;
            }
            net.minecraft.world.entity.AgeableMob child = this.self.getBreedOffspring(serverLevel, this.partner);
            this.partner.setAge(6000);
            this.self.setAge(6000);
            net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent event =
                    new net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent(this.self, this.partner, child);
            if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled()
                    || event.getChild() == null) {
                return;
            }
            net.minecraft.world.entity.AgeableMob baby = event.getChild();
            baby.setAge(-24000);
            baby.moveTo(this.self.getX(), this.self.getY(), this.self.getZ(), 0.0F, 0.0F);
            serverLevel.addFreshEntity(baby);
            serverLevel.broadcastEntityEvent(baby, (byte) 12);
        }
    }

    /** Original ah.b: a player in a scene is immune to everything but the void while her partner is within one block. */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
                && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)
                && !event.getSource().typeHolder().is(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sexmod", "succubus"))) {
            // original ah.a: the succubus drain (a3) is the one thing that still hurts a player in a scene
            boolean partnered = !player.level().getEntitiesOfClass(CompanionEntity.class, player.getBoundingBox().inflate(1.0D),
                    c -> c.isInScene() && c.scenePartnerId().filter(player.getUUID()::equals).isPresent()).isEmpty();
            if (partnered) {
                event.setCanceled(true);
            }
        }
    }

    /** Lethal damage while following leaves the girl downed at 1 HP; a downed girl takes no damage. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof CompanionEntity companion) {
            event.setNewDamage(companion.adjustIncomingDamage(event.getNewDamage()));
        }
    }

    /** Original f8.b: arrows shot by Manglelie pass through every companion. */
    @SubscribeEvent
    public static void onArrowImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                && arrow.getOwner() instanceof CompanionEntity shooter && shooter.kind() == CompanionKind.MANGLELIE
                && event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof CompanionEntity) {
            event.setCanceled(true);
            return;
        }
        if (event.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow
                && arrow.getPersistentData().getInt("sexmod:punch") > 0
                && event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity victim) {
            // 1.12 punch: push along the arrow's horizontal motion, 0.6 blocks of speed per level, 0.1 up
            var motion = arrow.getDeltaMovement();
            double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (horizontal > 0.0D) {
                double scale = arrow.getPersistentData().getInt("sexmod:punch") * 0.6D / horizontal;
                victim.push(motion.x * scale, 0.1D, motion.z * scale);
            }
        }
    }

    /** Original CheckSpawn hijack: a natural Wither Skeleton or Blaze may become the wild Galath boss instead. */
    @SubscribeEvent
    public static void onFinalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        var mob = event.getEntity();
        if (!(mob instanceof net.minecraft.world.entity.monster.WitherSkeleton)
                && !(mob instanceof net.minecraft.world.entity.monster.Blaze)) {
            return;
        }
        var type = event.getSpawnType();
        if (type == net.minecraft.world.entity.MobSpawnType.SPAWNER || type == net.minecraft.world.entity.MobSpawnType.SPAWN_EGG
                || type == net.minecraft.world.entity.MobSpawnType.COMMAND || type == net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED
                || type == net.minecraft.world.entity.MobSpawnType.TRIGGERED || type == net.minecraft.world.entity.MobSpawnType.DISPENSER) {
            return;
        }
        if (!(event.getLevel().getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                || level.dimension() != net.minecraft.world.level.Level.NETHER) {
            return;
        }
        var pos = mob.blockPosition();
        if (!GalathBoss.canSpawnAt(level, pos)) {
            return;
        }
        CompanionEntity galath = com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.GALATH).create(level);
        if (galath == null) {
            return;
        }
        event.setSpawnCancelled(true);
        galath.moveTo(event.getX(), event.getY(), event.getZ(), level.random.nextFloat() * 360.0F, 0.0F);
        galath.setPersistenceRequired();
        level.addFreshEntity(galath);
        GalathBindingData.get(level.getServer()).addSpawn(pos, false);
    }
}
