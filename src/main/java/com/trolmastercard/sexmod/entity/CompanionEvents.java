package com.trolmastercard.sexmod.entity;

import com.trolmastercard.sexmod.FapCraft;
import net.neoforged.bus.api.SubscribeEvent;
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

    /** Original co: an animal that gets the horny effect falls in love with the nearest player within 30 blocks at once. */
    @SubscribeEvent
    public static void onAnimalTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.animal.Animal animal && !animal.level().isClientSide
                && animal.getAge() >= 0 && animal.hasEffect(com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT)) {
            Player nearest = animal.level().getNearestPlayer(animal, 30.0D);
            animal.setInLove(nearest);
            animal.removeEffect(com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT);
        }
    }

    /** Original ah.b: a player in a scene is immune to everything but the void while her partner is within one block. */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
                && !event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
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
        }
    }
}
