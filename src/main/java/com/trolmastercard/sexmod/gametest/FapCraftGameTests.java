package com.trolmastercard.sexmod.gametest;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.data.CompanionOwnershipData;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionKind;
import com.trolmastercard.sexmod.entity.CompanionMode;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 1 baseline tests. They pin down what the port already does so later
 * refactors (Phase 2 onward) can be checked against it. Run with:
 * {@code .\gradlew.bat gameTestServer}
 */
@GameTestHolder(FapCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FapCraftGameTests {
    /** Registry paths of the 16 secondary IDs kept from the 1.12.2 jar. */
    private static final List<String> SECONDARY_ENTITY_IDS = List.of(
            "kobold_egg", "custom_model", "friendly_slime", "luna_hook", "energy_ball", "pyrocinical",
            "player_jenny", "player_ellie", "player_slime", "player_bia", "player_bee",
            "player_allie", "player_kobold", "player_goblin", "player_luna", "player_galath");

    private static final List<String> ITEM_IDS = List.of(
            "allies_lamp", "dragon_staff", "tribe_egg", "galath_coin",
            "npc_editor_wand", "kobold_egg_item", "winchester", "luna_rod");

    private static final int EXPECTED_SOUND_EVENTS = 719;

    private FapCraftGameTests() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(FapCraft.MOD_ID, path);
    }

    private static void requireAll(GameTestHelper helper, String what, Registry<?> registry, List<String> paths) {
        List<String> missing = new ArrayList<>();
        for (String path : paths) {
            if (!registry.containsKey(id(path))) {
                missing.add(path);
            }
        }
        if (!missing.isEmpty()) {
            helper.fail("Missing " + what + ": " + missing);
        }
    }

    @GameTest(template = "empty")
    public static void allCompanionKindsHaveEntityTypes(GameTestHelper helper) {
        List<String> paths = new ArrayList<>();
        for (CompanionKind kind : CompanionKind.values()) {
            paths.add(kind.registryPath());
        }
        if (paths.size() != 11) {
            helper.fail("Expected 11 companion kinds, found " + paths.size());
        }
        requireAll(helper, "companion entity types", BuiltInRegistries.ENTITY_TYPE, paths);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void secondaryEntityIdsAreRegistered(GameTestHelper helper) {
        requireAll(helper, "secondary entity types", BuiltInRegistries.ENTITY_TYPE, SECONDARY_ENTITY_IDS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void specialItemsAreRegistered(GameTestHelper helper) {
        requireAll(helper, "items", BuiltInRegistries.ITEM, ITEM_IDS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hornyEffectAndPotionAreRegistered(GameTestHelper helper) {
        requireAll(helper, "mob effect", BuiltInRegistries.MOB_EFFECT, List.of("horny_potion"));
        requireAll(helper, "potion", BuiltInRegistries.POTION, List.of("horny_potion"));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allSoundEventsAreRegistered(GameTestHelper helper) {
        long count = BuiltInRegistries.SOUND_EVENT.keySet().stream()
                .filter(key -> key.getNamespace().equals(FapCraft.MOD_ID))
                .count();
        if (count != EXPECTED_SOUND_EVENTS) {
            helper.fail("Expected " + EXPECTED_SOUND_EVENTS + " sound events, found " + count);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void companionModesCycleAndParse(GameTestHelper helper) {
        CompanionMode[] expected = {CompanionMode.WANDER, CompanionMode.FOLLOW, CompanionMode.STAY,
                CompanionMode.GUARD, CompanionMode.HOME};
        CompanionMode mode = CompanionMode.WANDER;
        for (int i = 0; i < expected.length; i++) {
            if (mode != expected[i]) {
                helper.fail("Mode order changed at step " + i + ": got " + mode);
            }
            mode = mode.next();
        }
        if (mode != CompanionMode.WANDER) {
            helper.fail("Mode cycle did not return to WANDER, got " + mode);
        }
        if (CompanionMode.byName("Follow") != CompanionMode.FOLLOW) {
            helper.fail("byName should ignore case");
        }
        if (CompanionMode.byName("not-a-mode") != CompanionMode.STAY) {
            helper.fail("Unknown mode names should fall back to STAY");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ownershipLimitsHold(GameTestHelper helper) {
        CompanionOwnershipData data = new CompanionOwnershipData();
        UUID owner = UUID.randomUUID();

        UUID firstJenny = UUID.randomUUID();
        if (!data.claim(owner, firstJenny, CompanionKind.JENNY)) {
            helper.fail("First Jenny should be claimable");
        }
        if (data.claim(owner, UUID.randomUUID(), CompanionKind.JENNY)) {
            helper.fail("A second Jenny must be refused (one of each kind)");
        }

        for (int i = 0; i < 4; i++) {
            if (!data.claim(owner, UUID.randomUUID(), CompanionKind.KOBOLD)) {
                helper.fail("Kobold " + (i + 1) + " of 4 should be claimable");
            }
        }
        if (data.claim(owner, UUID.randomUUID(), CompanionKind.KOBOLD)) {
            helper.fail("A fifth Kobold must be refused");
        }

        CompanionKind[] fillers = {CompanionKind.ELLIE, CompanionKind.BIA, CompanionKind.LUNA};
        for (CompanionKind kind : fillers) {
            if (!data.claim(owner, UUID.randomUUID(), kind)) {
                helper.fail(kind + " should be claimable while under the total limit");
            }
        }
        if (data.claim(owner, UUID.randomUUID(), CompanionKind.SLIME)) {
            helper.fail("A ninth companion must be refused (total limit is " + CompanionOwnershipData.MAX_TOTAL + ")");
        }

        data.release(owner, firstJenny);
        if (!data.claim(owner, UUID.randomUUID(), CompanionKind.SLIME)) {
            helper.fail("Releasing a companion should free a slot");
        }

        UUID otherOwner = UUID.randomUUID();
        if (!data.claim(otherOwner, UUID.randomUUID(), CompanionKind.JENNY)) {
            helper.fail("Limits must be tracked per owner");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void everyCompanionKindSpawns(GameTestHelper helper) {
        Vec3 spawnAt = helper.absoluteVec(new Vec3(0.5, 1.0, 0.5));
        List<Entity> spawned = new ArrayList<>();
        for (CompanionKind kind : CompanionKind.values()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id(kind.registryPath()));
            Entity entity = type.create(helper.getLevel());
            if (!(entity instanceof CompanionEntity)) {
                helper.fail(kind + " did not create a CompanionEntity, got " + entity);
                return;
            }
            entity.moveTo(spawnAt.x, spawnAt.y, spawnAt.z);
            if (!helper.getLevel().addFreshEntity(entity)) {
                helper.fail(kind + " could not be added to the level");
            }
            spawned.add(entity);
        }
        for (Entity entity : spawned) {
            if (!entity.isAlive()) {
                helper.fail(entity.getType() + " is not alive right after spawning");
            }
            entity.discard();
        }
        helper.succeed();
    }
}
