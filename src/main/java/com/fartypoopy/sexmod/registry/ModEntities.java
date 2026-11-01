package com.fartypoopy.sexmod.registry;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.entity.EnergyBallEntity;
import com.fartypoopy.sexmod.entity.FriendlySlimeEntity;
import com.fartypoopy.sexmod.entity.KoboldEggEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FapCraft.MOD_ID);

    private static final Map<CompanionKind, DeferredHolder<EntityType<?>, EntityType<CompanionEntity>>> COMPANIONS =
            new EnumMap<>(CompanionKind.class);
    private static final List<CompatibilityType> COMPATIBILITY_TYPES = new ArrayList<>();

    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> JENNY = register(CompanionKind.JENNY);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> ELLIE = register(CompanionKind.ELLIE);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> SLIME = register(CompanionKind.SLIME);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> BIA = register(CompanionKind.BIA);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> BEE = register(CompanionKind.BEE);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> LUNA = register(CompanionKind.LUNA);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> ALLIE = register(CompanionKind.ALLIE);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> KOBOLD = register(CompanionKind.KOBOLD);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> GOBLIN = register(CompanionKind.GOBLIN);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> GALATH = register(CompanionKind.GALATH);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> MANGLELIE =
            register(CompanionKind.MANGLELIE);

    public static final DeferredHolder<EntityType<?>, EntityType<KoboldEggEntity>> KOBOLD_EGG =
            ENTITY_TYPES.register(
                    "kobold_egg",
                    () -> EntityType.Builder.<KoboldEggEntity>of(KoboldEggEntity::new, MobCategory.CREATURE)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(1)
                            .build(FapCraft.MOD_ID + ":kobold_egg")
            );
    public static final DeferredHolder<EntityType<?>, EntityType<com.fartypoopy.sexmod.entity.CustomModelStubEntity>> CUSTOM_MODEL =
            ENTITY_TYPES.register(
                    "custom_model",
                    () -> EntityType.Builder.<com.fartypoopy.sexmod.entity.CustomModelStubEntity>of(
                                    com.fartypoopy.sexmod.entity.CustomModelStubEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .clientTrackingRange(4)
                            .updateInterval(1)
                            .build(FapCraft.MOD_ID + ":custom_model")
            );
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_JENNY =
            registerCompatibility("player_jenny", CompanionKind.JENNY, true);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_ELLIE =
            registerCompatibility("player_ellie", CompanionKind.ELLIE, true);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_SLIME =
            registerCompatibility("player_slime", CompanionKind.SLIME, true);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_BIA =
            registerCompatibility("player_bia", CompanionKind.BIA, true);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_BEE =
            registerCompatibility("player_bee", CompanionKind.BEE, true);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_ALLIE =
            registerCompatibility("player_allie", CompanionKind.ALLIE, true);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_KOBOLD =
            registerCompatibility("player_kobold", CompanionKind.KOBOLD, true);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_GOBLIN =
            registerCompatibility("player_goblin", CompanionKind.GOBLIN, true);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_LUNA =
            registerCompatibility("player_luna", CompanionKind.LUNA, true);
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> PLAYER_GALATH =
            registerCompatibility("player_galath", CompanionKind.GALATH, true);
    public static final DeferredHolder<EntityType<?>, EntityType<FriendlySlimeEntity>> FRIENDLY_SLIME =
            ENTITY_TYPES.register(
                    "friendly_slime",
                    () -> EntityType.Builder.<FriendlySlimeEntity>of(FriendlySlimeEntity::new, MobCategory.CREATURE)
                            .sized(0.52F, 0.52F)
                            .eyeHeight(0.325F)
                            .spawnDimensionsScale(4.0F)
                            .clientTrackingRange(4)
                            .updateInterval(1)
                            .build(FapCraft.MOD_ID + ":friendly_slime")
            );
    public static final DeferredHolder<EntityType<?>, EntityType<com.fartypoopy.sexmod.entity.PyrocinicalEntity>> PYROCINICAL =
            ENTITY_TYPES.register(
                    "pyrocinical",
                    () -> EntityType.Builder.<com.fartypoopy.sexmod.entity.PyrocinicalEntity>of(
                                    com.fartypoopy.sexmod.entity.PyrocinicalEntity::new, MobCategory.AMBIENT)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(4)
                            .updateInterval(1)
                            .build(FapCraft.MOD_ID + ":pyrocinical")
            );

    public static final DeferredHolder<EntityType<?>, EntityType<com.fartypoopy.sexmod.entity.LunaHookEntity>> LUNA_HOOK =
            ENTITY_TYPES.register(
                    "luna_hook",
                    () -> EntityType.Builder.<com.fartypoopy.sexmod.entity.LunaHookEntity>of(com.fartypoopy.sexmod.entity.LunaHookEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(4)
                            .updateInterval(1)
                            .noSave()
                            .build(FapCraft.MOD_ID + ":luna_hook")
            );
    public static final DeferredHolder<EntityType<?>, EntityType<EnergyBallEntity>> ENERGY_BALL =
            ENTITY_TYPES.register(
                    "energy_ball",
                    () -> EntityType.Builder.<EnergyBallEntity>of(EnergyBallEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(1)
                            .noSave()
                            .build(FapCraft.MOD_ID + ":energy_ball")
            );

    private ModEntities() {
    }

    private static DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> register(CompanionKind kind) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, kind.registryPath());
        DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> holder = ENTITY_TYPES.register(
                kind.registryPath(),
                () -> EntityType.Builder.<CompanionEntity>of(
                                (entityType, level) -> new CompanionEntity(entityType, level, kind),
                                kind == CompanionKind.MANGLELIE ? MobCategory.AMBIENT : MobCategory.CREATURE
                        )
                        .sized(kind.width(), kind.height())
                        .clientTrackingRange(4) // original bi: 50 blocks, every tick
                        .updateInterval(1)
                        .build(id.toString())
        );
        COMPANIONS.put(kind, holder);
        return holder;
    }

    private static DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> registerCompatibility(
            String path,
            CompanionKind kind,
            boolean technicalProxy
    ) {
        DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> holder = ENTITY_TYPES.register(
                path,
                () -> EntityType.Builder.<CompanionEntity>of(
                                (entityType, level) -> new CompanionEntity(
                                        entityType,
                                        level,
                                        kind,
                                        technicalProxy
                                ),
                                MobCategory.MISC
                        )
                        .sized(kind.width(), kind.height())
                        .clientTrackingRange(7) // original bi.b (player forms): 100 blocks, every tick
                        .updateInterval(1)
                        .build(FapCraft.MOD_ID + ":" + path)
        );
        COMPATIBILITY_TYPES.add(new CompatibilityType(holder, kind));
        return holder;
    }

    public static EntityType<CompanionEntity> get(CompanionKind kind) {
        return COMPANIONS.get(kind).get();
    }

    public static List<EntityType<CompanionEntity>> compatibilityTypes() {
        return COMPATIBILITY_TYPES.stream().map(entry -> entry.holder().get()).toList();
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                PYROCINICAL.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.fartypoopy.sexmod.entity.PyrocinicalEntity::checkSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
        COMPANIONS.values().forEach(holder -> event.register(
                holder.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CompanionEntity::checkNaturalSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE
        ));
    }

    public static void createAttributes(EntityAttributeCreationEvent event) {
        COMPANIONS.forEach((kind, holder) ->
                event.put(holder.get(), CompanionEntity.createAttributes(kind).build()));
        COMPATIBILITY_TYPES.forEach(entry -> event.put(
                entry.holder().get(),
                CompanionEntity.createAttributes(entry.kind()).build()
        ));
        event.put(PYROCINICAL.get(), com.fartypoopy.sexmod.entity.PyrocinicalEntity.createAttributes().build());
        event.put(CUSTOM_MODEL.get(), com.fartypoopy.sexmod.entity.CustomModelStubEntity.createAttributes().build());
        event.put(KOBOLD_EGG.get(), KoboldEggEntity.createAttributes().build());
        event.put(FRIENDLY_SLIME.get(), net.minecraft.world.entity.monster.Monster.createMonsterAttributes().build());
    }

    private record CompatibilityType(
            DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> holder,
            CompanionKind kind
    ) {
    }
}
