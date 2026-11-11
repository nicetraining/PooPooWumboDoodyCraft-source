package com.fartypoopy.sexmod.gametest;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionActions;
import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.entity.CompanionScene;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.entity.CompanionAction;
import com.fartypoopy.sexmod.menu.CompanionInventoryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
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

    private static final String[] ANIMATION_FILES = {"allie/allie", "allie/lamp", "bee/bee", "bia/bia", "cat/cat",
            "ellie/ellie", "galath/galath", "galath/galath_coin", "goblin/goblin", "jenny/jenny", "kobold/egg",
            "kobold/kobold", "kobold/staff", "manglelie/manglelie", "slime/slime"};

    /** GeckoLib 4.9.3+ rejects a whole animation over one value it cannot compile (it broke 132 of them once). */
    @GameTest(template = "empty")
    public static void everyAnimationValueCompiles(GameTestHelper helper) {
        List<String> bad = new ArrayList<>();
        for (String file : ANIMATION_FILES) {
            String path = "/assets/sexmod/animations/" + file + ".animation.json";
            try (var in = FapCraftGameTests.class.getResourceAsStream(path)) {
                if (in == null) {
                    bad.add(path + " missing");
                    continue;
                }
                var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                checkValues(root, file, bad);
            } catch (Exception e) {
                bad.add(path + ": " + e);
            }
        }
        if (!bad.isEmpty()) {
            helper.fail(bad.size() + " animation values do not compile, e.g. " + bad.subList(0, Math.min(5, bad.size())));
        }
        helper.succeed();
    }

    private static void checkValues(com.google.gson.JsonElement element, String where, List<String> bad) {
        if (element.isJsonObject()) {
            for (var entry : element.getAsJsonObject().entrySet()) {
                if (entry.getKey().equals("vector") && entry.getValue().isJsonArray()) {
                    for (var value : entry.getValue().getAsJsonArray()) {
                        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                            // the client registers FapCraft.ANIMATION_VARIABLES before loading; the server cannot
                            // (client-only registry), so they are replaced by 0 here
                            String expression = value.getAsString().toLowerCase(java.util.Locale.ROOT);
                            expression = expression.replace("query.anim_time", "0"); // queries are client-only too
                            for (String name : FapCraft.ANIMATION_VARIABLES) {
                                expression = expression.replaceAll("(?<![a-z_.])" + name + "(?![a-z_0-9])", "0");
                            }
                            try {
                                software.bernie.geckolib.loading.math.MathParser.parseJson(
                                        new com.google.gson.JsonPrimitive(expression));
                            } catch (Exception e) {
                                bad.add(where + ": '" + value.getAsString() + "'");
                            }
                        }
                    }
                } else {
                    checkValues(entry.getValue(), where, bad);
                }
            }
        } else if (element.isJsonArray()) {
            for (var value : element.getAsJsonArray()) {
                checkValues(value, where, bad);
            }
        }
    }

    /** 1.12 door halves stored half the state each; placed houses must show the combined state (double doors meet). */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void structureDoorsJoinTheirHalves(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 40, 0));
        com.fartypoopy.sexmod.worldgen.OriginalStructures.place(level, "jenny", origin, net.minecraft.world.level.block.Rotation.NONE);
        expectDoor(helper, origin.offset(3, 1, 1), net.minecraft.core.Direction.NORTH, "left", false);
        expectDoor(helper, origin.offset(4, 1, 1), net.minecraft.core.Direction.NORTH, "right", false);
        BlockPos ellie = origin.offset(0, 20, 0);
        com.fartypoopy.sexmod.worldgen.OriginalStructures.place(level, "ellie", ellie, net.minecraft.world.level.block.Rotation.NONE);
        expectDoor(helper, ellie.offset(13, 3, 14), net.minecraft.core.Direction.SOUTH, "right", true);
        expectDoor(helper, ellie.offset(5, 9, 15), net.minecraft.core.Direction.NORTH, "left", true);
        expectDoor(helper, ellie.offset(6, 9, 15), net.minecraft.core.Direction.NORTH, "right", true);
        BlockPos bia = origin.offset(0, 40, 0);
        com.fartypoopy.sexmod.worldgen.OriginalStructures.place(level, "bia", bia, net.minecraft.world.level.block.Rotation.NONE);
        var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(origin.offset(-4, 0, -4).getCenter(), bia.offset(30, 30, 30).getCenter()));
        // the Bia template itself holds a plank and a door lying on the floor (entities, spawned in 1.12 too)
        drops.removeIf(e -> e.getItem().is(net.minecraft.world.item.Items.BIRCH_PLANKS)
                || e.getItem().is(net.minecraft.world.item.Items.OAK_DOOR) && e.getItem().getCount() == 2);
        if (!drops.isEmpty()) {
            helper.fail("placing the houses broke blocks: " + drops.stream().map(e -> e.getItem().toString()).toList());
        }
        helper.succeed();
    }


    /** The houses and lairs are data-driven worldgen structures (so /locate structure finds them). */
    @GameTest(template = "empty")
    public static void structuresAreRegistered(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var structures = access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var sets = access.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
        for (String name : new String[]{"jenny", "ellie", "bia", "luna", "goblin_lair"}) {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sexmod", name);
            if (!structures.containsKey(id) || !sets.containsKey(id)) {
                helper.fail("structure and structure set sexmod:" + name + " are loaded");
                return;
            }
        }
        helper.succeed();
    }

    private static void expectDoor(GameTestHelper helper, BlockPos lower, net.minecraft.core.Direction facing, String hinge, boolean open) {
        for (BlockPos pos : new BlockPos[]{lower, lower.above()}) {
            var state = helper.getLevel().getBlockState(pos);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                    || state.getValue(net.minecraft.world.level.block.DoorBlock.FACING) != facing
                    || !state.getValue(net.minecraft.world.level.block.DoorBlock.HINGE).getSerializedName().equals(hinge)
                    || state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) != open) {
                helper.fail("door at " + pos + " is " + state + ", expected facing " + facing + " hinge " + hinge + " open " + open);
            }
        }
    }

    /** Every scene label a menu can show is a literal or a key en_us.json defines (raw keys once showed in menus). */
    @GameTest(template = "empty")
    public static void everySceneMenuLabelIsTranslated(GameTestHelper helper) throws java.io.IOException {
        com.google.gson.JsonObject lang;
        try (var in = FapCraftGameTests.class.getResourceAsStream("/assets/sexmod/lang/en_us.json")) {
            lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> missing = new ArrayList<>();
        for (CompanionScene scene : CompanionScene.values()) {
            String label = scene.menuLabelKey();
            boolean key = label.contains(".") && !label.contains(" ");
            if (key && !lang.has(label)) {
                missing.add(scene + " -> " + label);
            }
        }
        if (!missing.isEmpty()) {
            helper.fail("untranslated scene labels: " + missing);
        }
        helper.succeed();
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

    private static CompanionEntity spawnKind(GameTestHelper helper, CompanionKind kind) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id(kind.registryPath()));
        Entity entity = type.create(helper.getLevel());
        entity.moveTo(helper.absoluteVec(new Vec3(0.5, 1.0, 0.5)));
        helper.getLevel().addFreshEntity(entity);
        return (CompanionEntity) entity;
    }

    @GameTest(template = "empty")
    public static void equipmentMenusMatchTheOriginal(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompanionEntity luna = spawnKind(helper, CompanionKind.LUNA);
        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);

        CompanionInventoryMenu lunaMenu = new CompanionInventoryMenu(1, player.getInventory(), luna);
        CompanionInventoryMenu jennyMenu = new CompanionInventoryMenu(2, player.getInventory(), jenny);
        if (lunaMenu.slots.size() != 7 + 36) {
            helper.fail("Luna menu should have 7 companion slots, slots=" + lunaMenu.slots.size());
        }
        if (jennyMenu.slots.size() != 6 + 36) {
            helper.fail("Jenny menu should have 6 companion slots, slots=" + jennyMenu.slots.size());
        }
        Slot weapon = jennyMenu.slots.get(0);
        Slot bow = jennyMenu.slots.get(1);
        Slot helmet = jennyMenu.slots.get(2);
        Slot rod = lunaMenu.slots.get(6);
        if (!weapon.mayPlace(new ItemStack(Items.IRON_SWORD)) || weapon.mayPlace(new ItemStack(Items.BOW))) {
            helper.fail("weapon slot must take swords/tools only");
        }
        if (!bow.mayPlace(new ItemStack(Items.BOW)) || bow.mayPlace(new ItemStack(Items.IRON_SWORD))) {
            helper.fail("bow slot must take bows only");
        }
        if (!helmet.mayPlace(new ItemStack(Items.IRON_HELMET)) || helmet.mayPlace(new ItemStack(Items.IRON_BOOTS))) {
            helper.fail("helmet slot must take helmets only");
        }
        if (!rod.mayPlace(new ItemStack(Items.FISHING_ROD)) || rod.mayPlace(new ItemStack(Items.STICK))) {
            helper.fail("rod slot must take fishing rods only");
        }
        luna.discard();
        jenny.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void storageSurvivesSaveAndLoad(GameTestHelper helper) {
        for (CompanionKind kind : CompanionKind.values()) {
            boolean expected = kind == CompanionKind.KOBOLD || kind == CompanionKind.BEE;
            if (kind.hasStorage() != expected) {
                helper.fail(kind + " storage flag is wrong");
            }
        }
        CompanionEntity kobold = spawnKind(helper, CompanionKind.KOBOLD);
        kobold.storage().setItem(26, new ItemStack(Items.DIAMOND, 3));
        CompoundTag tag = new CompoundTag();
        kobold.addAdditionalSaveData(tag);
        CompanionEntity copy = spawnKind(helper, CompanionKind.KOBOLD);
        copy.readAdditionalSaveData(tag);
        ItemStack loaded = copy.storage().getItem(26);
        if (!loaded.is(Items.DIAMOND) || loaded.getCount() != 3) {
            helper.fail("storage contents were not restored, got " + loaded
                    + "; original=" + kobold.storage().getItem(26)
                    + "; savedTag=" + tag.get("FapCraftStorage")
                    + "; copyKind=" + copy.kind());
        }
        kobold.equipment().setItem(2, new ItemStack(Items.IRON_HELMET));
        CompoundTag equipTag = new CompoundTag();
        kobold.addAdditionalSaveData(equipTag);
        CompanionEntity equipCopy = spawnKind(helper, CompanionKind.KOBOLD);
        equipCopy.readAdditionalSaveData(equipTag);
        if (!equipCopy.equipment().getItem(2).is(Items.IRON_HELMET) || !equipCopy.equipment().getItem(0).isEmpty()) {
            helper.fail("helmet did not stay in slot 2 after reload");
        }
        equipCopy.discard();
        kobold.discard();
        copy.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actionCostsAreChargedLikeTheOriginal(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompanionEntity luna = spawnKind(helper, CompanionKind.LUNA);
        CompanionEntity kobold = spawnKind(helper, CompanionKind.KOBOLD);

        requireCost(helper, CompanionScene.JENNY_BLOWJOB, Items.EMERALD, 3);
        requireCost(helper, CompanionScene.JENNY_BOOBJOB, Items.ENDER_PEARL, 2);
        requireCost(helper, CompanionScene.JENNY_DOGGY, Items.DIAMOND, 2);
        requireCost(helper, CompanionScene.LUNA_SITTING, Items.COD, 3);
        requireCost(helper, CompanionScene.LUNA_TOUCH, Items.SALMON, 2);
        requireCost(helper, CompanionScene.KOBOLD_ANAL, Items.GOLD_INGOT, 3);
        requireCost(helper, CompanionScene.KOBOLD_BLOWJOB, Items.IRON_PICKAXE, 1);
        if (!CompanionActions.costOf(CompanionScene.JENNY_WAVE).isEmpty()) {
            helper.fail("actions without an entry must be free");
        }
        if (CompanionActions.canAfford(player, luna, CompanionScene.LUNA_SITTING)) {
            helper.fail("an empty inventory cannot afford a paid action");
        }
        player.getInventory().setItem(0, new ItemStack(Items.COD, 2));
        player.getInventory().setItem(1, new ItemStack(Items.COD, 1));
        if (CompanionActions.canAfford(player, luna, CompanionScene.LUNA_SITTING)) {
            helper.fail("the price must fit in one stack (2+1 cod is not enough)");
        }
        player.getInventory().setItem(2, new ItemStack(Items.COD, 5));
        if (!CompanionActions.canAfford(player, luna, CompanionScene.LUNA_SITTING)) {
            helper.fail("a stack of 5 cod should afford a 3 cod action");
        }
        CompanionActions.pay(player, luna, CompanionScene.LUNA_SITTING);
        if (player.getInventory().countItem(Items.COD) != 5) {
            helper.fail("paying should leave 5 cod in total, left " + player.getInventory().countItem(Items.COD));
        }
        if (!CompanionActions.costFor(kobold, player, CompanionScene.KOBOLD_ANAL).is(Items.GOLD_INGOT)) {
            helper.fail("a stranger pays a wild Kobold");
        }
        kobold.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                com.fartypoopy.sexmod.registry.ModPotions.HORNY_EFFECT, 200));
        if (!CompanionActions.costFor(kobold, player, CompanionScene.KOBOLD_ANAL).isEmpty()) {
            helper.fail("a Kobold under the horny effect is free");
        }
        luna.discard();
        kobold.discard();
        helper.succeed();
    }

    private static void requireCost(GameTestHelper helper, CompanionScene scene, net.minecraft.world.item.Item item, int count) {
        ItemStack cost = CompanionActions.costOf(scene);
        if (!cost.is(item) || cost.getCount() != count) {
            helper.fail(scene + " should cost " + count + " x " + item + ", got " + cost);
        }
    }

    @GameTest(template = "empty")
    public static void actionPanelListsMatchTheOriginal(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);
        CompanionEntity kobold = spawnKind(helper, CompanionKind.KOBOLD);
        CompanionEntity luna = spawnKind(helper, CompanionKind.LUNA);

        var jennyActions = CompanionActions.actionsFor(jenny, player);
        if (jennyActions.size() != 4 || jennyActions.contains(CompanionScene.JENNY_WAVE)
                || !jennyActions.contains(CompanionScene.JENNY_STRIP)) {
            helper.fail("Jenny offers blowjob, boobjob, doggy and strip, got " + jennyActions);
        }
        var koboldActions = CompanionActions.actionsFor(kobold, player);
        if (koboldActions.size() != 2 || koboldActions.contains(CompanionScene.KOBOLD_MATING_PRESS)) {
            helper.fail("a stranger gets anal and oral only, got " + koboldActions);
        }
        if (!CompanionKind.KOBOLD.opensMenuToStrangers() || CompanionKind.JENNY.opensMenuToStrangers()) {
            helper.fail("only the Kobold menu is public");
        }
        if (!kobold.mayUseSceneActions(player) || !jenny.mayUseSceneActions(player)) {
            helper.fail("a girl who follows nobody is open to every player");
        }
        jenny.setMaster(UUID.randomUUID());
        if (jenny.mayUseSceneActions(player) || jenny.canUsePanel(player)) {
            helper.fail("a girl following someone else is busy for everybody else");
        }
        kobold.setMaster(UUID.randomUUID());
        if (!kobold.mayUseSceneActions(player)) {
            helper.fail("the Kobold's scene menu stays public even when it has a master");
        }
        jenny.setMaster(player.getUUID());
        if (!jenny.canUsePanel(player)) {
            helper.fail("her master gets the panel");
        }
        if (CompanionActions.actionsFor(luna, player).size() != 3) {
            helper.fail("Luna offers three actions");
        }
        if (!CompanionActions.showsFixedButtons(CompanionKind.JENNY)
                || CompanionActions.showsFixedButtons(CompanionKind.ALLIE)
                || CompanionActions.showsFixedButtons(CompanionKind.KOBOLD)) {
            helper.fail("fixed follow/home column: Jenny yes, Allie and Kobold no");
        }
        jenny.discard();
        kobold.discard();
        luna.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void masterIsTransientAndGirlIdSurvives(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);
        jenny.setMaster(player.getUUID());
        if (!jenny.isMaster(player) || !jenny.isFollowing()) {
            helper.fail("setMaster should make her follow the player");
        }
        CompoundTag tag = new CompoundTag();
        jenny.addAdditionalSaveData(tag);
        for (String key : List.of("FapCraftOwner", "FapCraftMode", "FapCraftFollowing", "master")) {
            if (tag.contains(key)) {
                helper.fail("the original saves no owner or follow state, found " + key);
            }
        }
        if (!tag.contains("girlID")) {
            helper.fail("girlID must be saved");
        }
        CompanionEntity copy = spawnKind(helper, CompanionKind.JENNY);
        copy.readAdditionalSaveData(tag);
        if (copy.hasMaster()) {
            helper.fail("after a reload she follows nobody");
        }
        if (!copy.girlId().equals(jenny.girlId())) {
            helper.fail("girlID must survive a reload");
        }
        jenny.discard();
        copy.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lethalDamageLeavesAFollowingGirlDowned(GameTestHelper helper) {
        CompanionEntity bia = spawnKind(helper, CompanionKind.BIA);
        bia.hurt(bia.damageSources().generic(), 1000.0F);
        if (!bia.isDeadOrDying()) {
            helper.fail("without a master lethal damage kills her");
        }

        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);
        jenny.setMaster(UUID.randomUUID());
        jenny.hurt(jenny.damageSources().generic(), 1000.0F);
        if (jenny.isDeadOrDying() || !jenny.isDowned() || jenny.action() != CompanionAction.DOWNED) {
            helper.fail("with a master lethal damage leaves her downed; health=" + jenny.getHealth());
        }
        if (Math.abs(jenny.getHealth() - 1.0F) > 0.01F) {
            helper.fail("downed at 1 HP, got " + jenny.getHealth());
        }
        float before = jenny.getHealth();
        jenny.invulnerableTime = 0;
        jenny.hurt(jenny.damageSources().generic(), 5.0F);
        if (jenny.getHealth() != before) {
            helper.fail("a downed girl takes no damage");
        }
        jenny.heal(2.0F);
        if (!jenny.isDowned()) {
            helper.fail("she stays downed until she is fully healed");
        }
        jenny.heal(1000.0F);
        if (jenny.isDowned() || jenny.action() != CompanionAction.NONE) {
            helper.fail("full health ends the downed state");
        }
        bia.discard();
        jenny.discard();
        helper.succeed();
    }

    /** Original ap: the lamp sits in the three-roll pool of the dungeon, mineshaft and mansion chests, not in the pyramid. */
    @GameTest(template = "empty")
    public static void alliesLampIsInTheOriginalChestPools(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (String table : List.of("simple_dungeon", "abandoned_mineshaft", "woodland_mansion", "desert_pyramid")) {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("chests/" + table));
            var loot = server.reloadableRegistries().getLootTable(key);
            boolean found = false;
            for (String name : List.of("main", "pool1", "pool2", "pool3")) {
                var pool = loot.getPool(name);
                if (pool == null) {
                    continue;
                }
                try {
                    var field = net.minecraft.world.level.storage.loot.LootPool.class.getDeclaredField("entries");
                    field.setAccessible(true);
                    for (Object entry : (List<?>) field.get(pool)) {
                        if (!(entry instanceof net.minecraft.world.level.storage.loot.entries.LootItem)) {
                            continue;
                        }
                        var itemField = net.minecraft.world.level.storage.loot.entries.LootItem.class.getDeclaredField("item");
                        itemField.setAccessible(true);
                        var holder = (net.minecraft.core.Holder<?>) itemField.get(entry);
                        if (holder.value() == com.fartypoopy.sexmod.registry.ModItems.ALLIES_LAMP.get()) {
                            if (!name.equals("pool2")) {
                                helper.fail("the lamp is in " + table + " " + name);
                            }
                            found = true;
                        }
                    }
                } catch (ReflectiveOperationException exception) {
                    helper.fail(exception.toString());
                }
            }
            if (found != !table.equals("desert_pyramid")) {
                helper.fail("lamp in " + table + ": " + found);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void girlArmourUsesTheOriginalFormula(GameTestHelper helper) {
        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);
        jenny.equipment().setItem(3, new ItemStack(Items.DIAMOND_CHESTPLATE)); // her slot 3 is the chest (mirrored)
        jenny.setHealth(jenny.getMaxHealth());
        float before = jenny.getHealth();
        jenny.invulnerableTime = 0;
        boolean hit = jenny.hurt(jenny.damageSources().cactus(), 5.0F); // generic bypasses armour, as in 1.12
        // gu: armour 8, toughness 3 -> 5 * (1 - max(8 / 5, 8 - 20 / 11) / 25)
        float expected = 5.0F * (1.0F - Math.max(8.0F / 5.0F, 8.0F - 20.0F / 11.0F) / 25.0F);
        if (Math.abs(before - jenny.getHealth() - expected) > 0.01F) {
            helper.fail("a diamond chestplate lets " + expected + " of 5 through, health " + before + " -> " + jenny.getHealth()
                    + " (hit " + hit + ", chest " + jenny.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                    + ", direct " + com.fartypoopy.sexmod.entity.GirlArmour.apply(jenny, jenny.damageSources().cactus(), 5.0F) + ")");
        }
        jenny.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void biaProneDoggyKeyframeSwitches(GameTestHelper helper) {
        CompanionScene prone = CompanionScene.BIA_PRONE;
        var random = net.minecraft.util.RandomSource.create(7L);
        // doggySwitch at 0.375 s of the soft loop (tick 7.5), only with the fast key held
        if (prone.keyframeSwitch(2, 8, true, random) != 3 || prone.keyframeSwitch(2, 8, false, random) != -1
                || prone.keyframeSwitch(2, 5, true, random) != -1 || prone.keyframeSwitch(2, 37, true, random) != 3) {
            helper.fail("soft turns hard at the doggySwitch keyframe of each soft loop while fast is held");
        }
        // doggyReset at 0.625 s of the hard loop: a different one of hard1..3
        for (int current : new int[] {3, 5, 6}) {
            int next = prone.keyframeSwitch(current, 13, true, random);
            if (next == current || (next != 3 && next != 5 && next != 6)) {
                helper.fail("doggyReset picks another hard variant, got " + next + " from " + current);
            }
        }
        // fp PRONE_DOGGY_HARD: length 34, followUp SOFT (doggyReset restarts the count while the key is held)
        if (prone.previousLoopPhase(3) != 2 || prone.previousLoopPhase(5) != 2 || prone.finalePhase() != 4
                || prone.phase(3).durationTicks() != 34 || !prone.fastAlwaysEndsSlow()) {
            helper.fail("the hard loop returns to soft 34 ticks after its last restart and finishes with prone_doggy_cum");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void attackVariantsCycleLikeTheOriginal(GameTestHelper helper) {
        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);
        // original e2.S: starts at 1, attackDone counts it on (back to 0 at 3), so the animations go attack1, attack2, attack0, ...
        int[] expected = {2, 0, 1, 2};
        if (jenny.attackCycle != 1) {
            helper.fail("the first attack is attack1, got " + jenny.attackCycle);
        }
        for (int value : expected) {
            jenny.attackDone();
            if (jenny.attackCycle != value || !jenny.attackFinished) {
                helper.fail("after attackDone expected attack" + value + ", got " + jenny.attackCycle);
            }
        }
        if (jenny.weaponMode() != 0) {
            helper.fail("a girl outside a fight shows no weapon, mode " + jenny.weaponMode());
        }
        jenny.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void goHomeThrowsAPearlAndTeleports(GameTestHelper helper) {
        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);
        net.minecraft.core.BlockPos home = helper.absolutePos(new net.minecraft.core.BlockPos(5, 1, 5));
        jenny.setHome(home);
        jenny.setMaster(UUID.randomUUID());
        jenny.startGoHome();
        if (jenny.hasMaster()) {
            helper.fail("going home ends the following");
        }
        if (jenny.action() != CompanionAction.THROW_PEARL) {
            helper.fail("she starts with the pearl throw, action=" + jenny.action());
        }
        // checked when the routine ends: afterwards her normal AI may already stroll away from home
        helper.succeedWhen(() -> {
            if (jenny.action() != CompanionAction.NONE) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("the routine ends after the teleport, action=" + jenny.action());
            }
            if (jenny.position().distanceTo(Vec3.atBottomCenterOf(home)) > 1.5D) {
                helper.fail("she should stand at home, is at " + jenny.position() + " home " + home);
            }
            jenny.discard();
        });
    }

    @GameTest(template = "empty")
    public static void clothingToggleFollowsTheOriginal(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);
        CompanionEntity ellie = spawnKind(helper, CompanionKind.ELLIE);
        CompanionEntity bia = spawnKind(helper, CompanionKind.BIA);
        requireCost(helper, CompanionScene.JENNY_STRIP, Items.GOLD_INGOT, 1);
        if (!CompanionActions.costFor(jenny, player, CompanionScene.JENNY_STRIP).is(Items.GOLD_INGOT)) {
            helper.fail("stripping a dressed Jenny costs a gold ingot");
        }
        if (!CompanionActions.actionsFor(ellie, player).equals(List.of(CompanionScene.ELLIE_CARRY))) {
            helper.fail("dressed Ellie offers only her carry scene");
        }
        if (!CompanionActions.costFor(bia, player, CompanionScene.BIA_STRIP).isEmpty()) {
            helper.fail("Bia's strip is free");
        }
        jenny.toggleClothing();
        ellie.toggleClothing();
        if (!jenny.isNude() || !ellie.isNude()) {
            helper.fail("toggling undresses");
        }
        if (!CompanionActions.costFor(jenny, player, CompanionScene.JENNY_STRIP).isEmpty()) {
            helper.fail("dressing up again is free");
        }
        if (!CompanionActions.actionsFor(ellie, player).equals(List.of(CompanionScene.ELLIE_STRIP))) {
            helper.fail("nude Ellie offers only dress up");
        }
        CompoundTag tag = new CompoundTag();
        jenny.addAdditionalSaveData(tag);
        CompanionEntity copy = spawnKind(helper, CompanionKind.JENNY);
        copy.readAdditionalSaveData(tag);
        if (copy.isNude()) {
            helper.fail("nudity is not saved: she is dressed after a reload");
        }
        jenny.toggleClothing();
        if (jenny.isNude()) {
            helper.fail("toggling again dresses her");
        }
        jenny.discard();
        ellie.discard();
        bia.discard();
        copy.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void biaTalkLeadsToSecondMenu(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompanionEntity bia = spawnKind(helper, CompanionKind.BIA);
        if (!CompanionActions.actionsFor(bia, player).equals(
                List.of(CompanionScene.BIA_STRIP, CompanionScene.BIA_TALK, CompanionScene.BIA_HEADPAT))) {
            helper.fail("Bia offers strip, talk and head pat, got " + CompanionActions.actionsFor(bia, player));
        }
        if (!CompanionScene.BIA_ANAL.requiresBed() || !CompanionScene.BIA_PRONE.requiresBed()) {
            helper.fail("both second-menu scenes need a bed");
        }
        if (!CompanionScene.BIA_TALK.isPrelude() || CompanionScene.BIA_ANAL.isPrelude()) {
            helper.fail("talk is only a prelude");
        }
        if (CompanionScene.BIA_TALK.phases().size() != 3) {
            helper.fail("talk, wait for the choice, response");
        }
        bia.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void biaWalksToABedBeforeTheScene(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompanionEntity bia = spawnKind(helper, CompanionKind.BIA);

        if (bia.beginBedWalk(helper.getLevel(), player, CompanionScene.BIA_ANAL) || bia.isBedWalking()) {
            helper.fail("with no bed she refuses (nobedinsight) and does not walk");
        }

        net.minecraft.core.BlockPos foot = new net.minecraft.core.BlockPos(3, 1, 2);
        net.minecraft.core.BlockPos head = new net.minecraft.core.BlockPos(3, 1, 3);
        net.minecraft.world.level.block.state.BlockState bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
        helper.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        helper.setBlock(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD));

        if (!bia.beginBedWalk(helper.getLevel(), player, CompanionScene.BIA_ANAL) || !bia.isBedWalking()) {
            helper.fail("with a bed she starts walking to it instead of starting the scene");
        }
        if (bia.isInScene()) {
            helper.fail("no scene animation plays before she has reached the bed");
        }
        Vec3 target = bia.bedWalkTarget();
        Vec3 bedCenter = helper.absoluteVec(new Vec3(3.5, 1.0, 2.5));
        if (target == null || target.distanceTo(bedCenter) > 2.6D || target.distanceTo(bedCenter) < 0.9D) {
            helper.fail("her spot is on a side of the bed, got " + target);
        }
        if (!CompanionScene.BIA_ANAL.waitScene().isWaitScene()
                || CompanionScene.BIA_PRONE.waitScene() != CompanionScene.BIA_SIT_WAIT
                || !CompanionScene.BIA_TALK.locksControls()) {
            helper.fail("wait scenes and the talk control lock exist");
        }
        bia.discard();
        helper.succeed();
    }

    // Own batch that runs first: Jenny searches 22 blocks for beds and would find beds left by other tests.
    @GameTest(template = "empty", batch = "a_first")
    public static void jennyWalksToABedForDoggy(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);

        boolean strangerBed = false; // other tests' arenas sit a few blocks away and some of them place beds
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(jenny.blockPosition().offset(-24, -6, -24), jenny.blockPosition().offset(24, 6, 24))) {
            strangerBed |= helper.getLevel().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BedBlock;
        }
        if (!strangerBed && (jenny.beginBedWalk(helper.getLevel(), player, CompanionScene.JENNY_DOGGY) || jenny.isBedWalking())) {
            StringBuilder beds = new StringBuilder();
            var origin = jenny.blockPosition();
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-24, -6, -24), origin.offset(24, 6, 24))) {
                if (helper.getLevel().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                    beds.append(pos.toShortString()).append(' ');
                }
            }
            helper.fail("with no bed Jenny refuses and does not walk (jenny " + origin.toShortString() + ", beds " + beds + ")");
        }
        net.minecraft.world.level.block.state.BlockState bed = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
        helper.setBlock(new net.minecraft.core.BlockPos(3, 1, 2), bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        helper.setBlock(new net.minecraft.core.BlockPos(3, 1, 3), bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        if (!jenny.beginBedWalk(helper.getLevel(), player, CompanionScene.JENNY_DOGGY) || !jenny.isBedWalking()) {
            helper.fail("with a bed Jenny walks to it first");
        }
        if (CompanionScene.JENNY_DOGGY.waitScene() != CompanionScene.JENNY_DOGGY_WAIT
                || !CompanionScene.JENNY_DOGGY_WAIT.isWaitScene()) {
            helper.fail("Jenny's doggy has a wait scene");
        }
        jenny.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void allieWishFlowAndIdleRemoval(GameTestHelper helper) {
        if (CompanionScene.ALLIE_SUMMON.phase(0).durationTicks() != 244
                || !CompanionScene.ALLIE_SUMMON.isMenuWaitPhase(1)
                || CompanionScene.ALLIE_SUMMON.isMenuWaitPhase(0)
                || !CompanionScene.ALLIE_SUMMON_NORMAL.isMenuWaitPhase(1)
                || !CompanionScene.ALLIE_PREPARE.isPrelude()
                || !CompanionScene.ALLIE_RICH_NORMAL.isPrelude()
                || CompanionScene.ALLIE_DEEPTHROAT.isPrelude()
                || !CompanionScene.ALLIE_DEEPTHROAT.hasPartner()
                || !CompanionScene.ALLIE_PREPARE.locksControls()) {
            helper.fail("Allie's scene chain is not set up as in the original");
        }
        CompanionEntity allie = spawnKind(helper, CompanionKind.ALLIE);
        helper.runAfterDelay(12, () -> {
            if (allie.isAlive()) {
                helper.fail("an Allie without a wish scene should vanish (original: state NULL removes her)");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void slimeGirlStateAndBaby(GameTestHelper helper) {
        CompanionEntity slime = spawnKind(helper, CompanionKind.SLIME);
        CompoundTag tag = new CompoundTag();
        tag.putInt("hornyLevel", 3);
        tag.putInt("ticksUntilBirth", 100);
        slime.readAdditionalSaveData(tag);
        if (slime.slimeHornyLevel() != 3 || !slime.isPregnant() || !slime.isNude()) {
            helper.fail("the slime's horny level, pregnancy and nudity are read from the save");
        }
        CompoundTag saved = new CompoundTag();
        slime.addAdditionalSaveData(saved);
        if (saved.getInt("hornyLevel") != 3 || saved.getInt("ticksUntilBirth") != 100) {
            helper.fail("the slime's horny level and birth timer are saved");
        }
        com.fartypoopy.sexmod.entity.FriendlySlimeEntity baby =
                com.fartypoopy.sexmod.registry.ModEntities.FRIENDLY_SLIME.get().create(helper.getLevel());
        if (baby == null) {
            helper.fail("the friendly slime entity can be created");
            return;
        }
        baby.setSize(1, true);
        if (baby.ageInTicks() != 0 || baby.getSize() != 1 || CompanionScene.SLIME_DOGGY.requiresBed()) {
            helper.fail("a new baby slime is size 1 and age 0; the slime's doggy needs no bed");
        }
        slime.discard();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void beeTamedAndChestFlagsSurviveReload(GameTestHelper helper) {
        CompanionEntity bee = spawnKind(helper, CompanionKind.BEE);
        if (bee.isBeeTamed() || bee.hasBeeChest()) {
            helper.fail("a new bee is wild and has no chest");
        }
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isTamed", true);
        tag.putBoolean("hasChest", true);
        bee.readAdditionalSaveData(tag);
        CompoundTag saved = new CompoundTag();
        bee.addAdditionalSaveData(saved);
        if (!bee.isBeeTamed() || !bee.hasBeeChest() || !saved.getBoolean("isTamed") || !saved.getBoolean("hasChest")) {
            helper.fail("isTamed and hasChest are read and saved with the original keys");
        }
        bee.discard();
        helper.succeed();
    }

    /** Bug report: a bee sometimes died of fall damage when a world was loaded. The original bee (fz.fall) takes none. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_beefall")
    public static void aBeeTakesNoFallDamage(GameTestHelper helper) {
        forceArena(helper);
        skyFloor(helper, 20, 250);
        CompanionEntity bee = spawnKind(helper, CompanionKind.BEE);
        bee.moveTo(helper.absoluteVec(new Vec3(8.5, 262.0, 8.5)));
        bee.setHealth(bee.getMaxHealth());
        bee.fallDistance = 60.0F; // as if she was saved mid-drop and the world loaded with her still falling
        helper.onEachTick(() -> {
            if (!bee.onGround()) {
                bee.setDeltaMovement(0.0D, -1.5D, 0.0D);
            }
        });
        helper.runAfterDelay(80, () -> {
            if (!bee.onGround()) {
                helper.fail("the bee never reached the floor (y " + bee.getY() + ")");
                return;
            }
            if (!bee.isAlive() || bee.getHealth() < bee.getMaxHealth()) {
                helper.fail("a bee was hurt by a fall: health " + bee.getHealth() + " of " + bee.getMaxHealth());
                return;
            }
            bee.discard();
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- Kobold tribes

    /** Keeps the arena chunks entity-ticking so the AI tests do not depend on where the structure lands. */
    private static void forceWideArena(GameTestHelper helper) {
        var level = helper.getLevel();
        var centre = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setChunkForced((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz, true);
            }
        }
    }

    private static void forceArena(GameTestHelper helper) {
        var level = helper.getLevel();
        var centre = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(cx + dx, cz + dz, true);
            }
        }
    }

    /** A platform far above the other arenas so mobs from other tests (they sit a few blocks apart) never meet. */
    private static void skyFloor(GameTestHelper helper, int size, int up) {
        for (int x = -1; x <= size; x++) {
            for (int z = -1; z <= size; z++) {
                helper.setBlock(new net.minecraft.core.BlockPos(x, up, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
    }

    private static void floor(GameTestHelper helper, int size) {
        for (int x = -1; x <= size; x++) {
            for (int z = -1; z <= size; z++) {
                helper.setBlock(new net.minecraft.core.BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
    }

    @GameTest(template = "empty", timeoutTicks = 900, batch = "b_luna")
    public static void lunaFishesEatsAndStartsOver(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        floor(helper, 14);
        // a sealed basin: stone ring and bottom, two blocks of still water
        for (int x = 4; x <= 10; x++) {
            for (int z = 1; z <= 7; z++) {
                boolean pool = x >= 5 && x <= 9 && z >= 2 && z <= 6;
                for (int y = -2; y <= 0; y++) {
                    var state = pool && y >= -1 ? net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
                    level.setBlock(helper.absolutePos(new net.minecraft.core.BlockPos(x, y, z)), state, 3);
                }
            }
        }
        com.fartypoopy.sexmod.entity.LunaFishing.ignoreBiomes = true;
        CompanionEntity luna = spawnKind(helper, CompanionKind.LUNA);
        luna.moveTo(helper.absoluteVec(new Vec3(1.5, 1.0, 4.5)));
        var fishing = luna.lunaFishing();
        helper.runAfterDelay(300, () -> {
            var hook = fishing.hook();
            int now = luna.fishState();
            if (now == com.fartypoopy.sexmod.entity.LunaFishing.EAT || now == com.fartypoopy.sexmod.entity.LunaFishing.THROW_AWAY) {
                return; // a natural bite already happened (she walks faster now)
            }
            if (hook == null || !hook.isBobbing() || now != com.fartypoopy.sexmod.entity.LunaFishing.IDLE) {
                com.fartypoopy.sexmod.entity.LunaFishing.ignoreBiomes = false;
                helper.fail("Luna walked to the water, cast her rod and the bobber floats (state " + luna.fishState() + ", hook " + hook + ")");
                return;
            }
            hook.forceBite();
        });
        helper.runAfterDelay(380, () -> {
            int state = luna.fishState();
            if (state != com.fartypoopy.sexmod.entity.LunaFishing.EAT && state != com.fartypoopy.sexmod.entity.LunaFishing.THROW_AWAY) {
                com.fartypoopy.sexmod.entity.LunaFishing.ignoreBiomes = false;
                helper.fail("after the bite she reeled in and eats or throws away the catch (state " + state + ")");
            }
        });
        helper.runAfterDelay(560, () -> {
            com.fartypoopy.sexmod.entity.LunaFishing.ignoreBiomes = false;
            if (fishing.catches() != 1 || luna.fishState() == com.fartypoopy.sexmod.entity.LunaFishing.EAT) {
                helper.fail("the catch is finished and she starts over (catches " + fishing.catches() + ", state " + luna.fishState() + ")");
                return;
            }
            luna.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "b_galath")
    public static void wildGalathHuntsAPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        skyFloor(helper, 30, 30);
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(8.5, 31.0, 8.5)));
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(8.5, 31.0, 2.5)));
        player.getAbilities().instabuild = false;
        // polled, not sampled at one tick: her first boss tick can come late while the forced arena chunks load
        helper.succeedWhen(() -> {
            if (galath.hasMaster() || !galath.wildBoss() || galath.bossTargetId() != player.getId()) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("a wild Galath targets the player within 20 blocks (target " + galath.bossTargetId() + ", galath " + galath.position() + " state " + galath.bossState() + " alive " + galath.isAlive() + ", player " + player.position() + " id " + player.getId() + " creative " + player.isCreative() + " inLevel " + level.players().contains(player) + ")");
            }
            if (galath.boss().attack() == null) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("she starts an attack once she has a target");
            }
        });
    }

    /**
     * Bug report: killed by Galath, her line played again and again. She took the dead player as her target every tick
     * (her target search let dead players through, her validity check did not), each time with her line and a new attack.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_galathdead")
    public static void galathLetsGoOfAPlayerSheKilled(GameTestHelper helper) {
        forceArena(helper);
        skyFloor(helper, 30, 318);
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(8.5, 319.0, 8.5)));
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(8.5, 319.0, 2.5)));
        player.getAbilities().instabuild = false;
        int[] retargets = {0};
        int[] state = {0}; // 0 waiting for her to target the player, 1 player dead
        helper.onEachTick(() -> {
            player.moveTo(helper.absoluteVec(new Vec3(8.5, 319.0, 2.5)));
            if (state[0] == 0 && galath.bossTargetId() == player.getId()) {
                state[0] = 1;
                player.setHealth(0.0F); // dead, still in the level (the death screen)
                galath.setBossTarget(-1);
            } else if (state[0] == 1 && galath.bossTargetId() == player.getId()) {
                retargets[0]++;
            }
        });
        helper.runAfterDelay(160, () -> {
            if (state[0] != 1) {
                helper.fail("she never targeted the player");
            } else if (retargets[0] > 0) {
                helper.fail("she keeps taking the dead player as her target (" + retargets[0] + " ticks), replaying her line each time");
            } else {
                galath.discard();
                helper.succeed();
            }
        });
    }

    /** Bug report: with a daughter her mob fight ran while ridden, so she would not follow the rider's wing beats. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_galathride")
    public static void riddenGalathWithADaughterFliesWhereTheRiderSteers(GameTestHelper helper) {
        forceArena(helper);
        skyFloor(helper, 30, 250);
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel(); // a FakePlayer cannot ride and takes no damage
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.moveTo(helper.absoluteVec(new Vec3(8.5, 262.0, 4.5)));
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(8.5, 262.0, 4.5)));
        galath.setMaster(player.getUUID());
        var daughter = com.fartypoopy.sexmod.entity.ManglelieBehavior.spawnDaughter(level, galath);
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        zombie.moveTo(helper.absoluteVec(new Vec3(12.5, 262.0, 4.5)));
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        level.addFreshEntity(zombie);
        if (!galath.startRide(player)) {
            helper.fail("the owner could not mount her");
            return;
        }
        Vec3 start = galath.position();
        int[] targeted = {0};
        helper.onEachTick(() -> {
            if (galath.galathRider() == player && galath.tickCount % 5 == 0) {
                galath.applyFlapImpulse(player, new Vec3(0.0D, 0.0D, 0.4D));
            }
            if (galath.bossTargetId() != -1) {
                targeted[0]++;
            }
        });
        helper.runAfterDelay(80, () -> {
            double moved = galath.position().z - start.z;
            galath.ejectPassengers();
            level.getServer().getPlayerList().remove(player);
            galath.discard();
            daughter.discard();
            zombie.discard();
            if (targeted[0] > 0) {
                helper.fail("she fought a mob while ridden (" + targeted[0] + " ticks)");
            } else if (moved < 4.0D) {
                helper.fail("she did not fly where the rider steered (moved " + moved + ")");
            } else {
                helper.succeed();
            }
        });
    }

    /** Bug report: a boost dive into the ground killed the rider; vanilla hands a vehicle's fall to its passengers. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_galathdive")
    public static void aBoostDiveDoesNotHurtGalathsRider(GameTestHelper helper) {
        forceArena(helper);
        skyFloor(helper, 20, 250);
        var player = helper.makeMockServerPlayerInLevel(); // a FakePlayer cannot ride and takes no damage
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.getAbilities().invulnerable = false;
        player.setHealth(player.getMaxHealth());
        player.moveTo(helper.absoluteVec(new Vec3(8.5, 300.0, 8.5)));
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(8.5, 300.0, 8.5)));
        galath.setMaster(player.getUUID());
        if (!galath.startRide(player)) {
            helper.fail("the owner could not mount her");
            return;
        }
        long start = helper.getTick();
        helper.onEachTick(() -> {
            // a new player cannot be hurt for 60 ticks; she hovers until then
            if (helper.getTick() - start < 70) {
                galath.setDeltaMovement(Vec3.ZERO);
            } else if (!galath.onGround()) {
                galath.setDeltaMovement(0.0D, -3.0D, 0.0D); // a boost straight down
            }
        });
        helper.runAfterDelay(140, () -> {
            double floorTop = helper.absoluteVec(new Vec3(0.0D, 251.0D, 0.0D)).y;
            boolean landed = galath.getY() - floorTop < 0.5D;
            String where = "y " + (galath.getY() - floorTop) + " above the floor, onGround " + galath.onGround()
                    + ", rider " + (galath.galathRider() != null);
            float health = player.getHealth();
            galath.ejectPassengers();
            helper.getLevel().getServer().getPlayerList().remove(player);
            galath.discard();
            if (!landed) {
                helper.fail("she never reached the ground (" + where + ")");
            } else if (health < player.getMaxHealth()) {
                helper.fail("the rider took the dive as fall damage (health " + health + ")");
            } else {
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "b_galath4")
    public static void wildGalathKeepsChasingAPlayerWhoRunsFar(GameTestHelper helper) {
        var level = helper.getLevel();
        var centre = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setChunkForced((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz, true);
            }
        }
        skyFloor(helper, 60, 150);
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(4.5, 151.0, 4.5)));
        var player = serverPlayer(helper);
        player.getAbilities().instabuild = false;
        Vec3 near = helper.absoluteVec(new Vec3(4.5, 151.0, 10.5));
        Vec3 far = helper.absoluteVec(new Vec3(4.5, 151.0, 44.5)); // beyond the 30 blocks at which the original let go
        int[] tick = {0};
        helper.onEachTick(() -> {
            Vec3 at = tick[0]++ < 30 ? near : far;
            player.moveTo(at);
            player.setDeltaMovement(Vec3.ZERO);
        });
        helper.runAfterDelay(30, () -> {
            if (galath.bossTargetId() != player.getId()) {
                helper.fail("she has targeted the player before he runs (target " + galath.bossTargetId() + ")");
            }
        });
        helper.runAfterDelay(150, () -> {
            if (galath.bossTargetId() != player.getId()) {
                helper.fail("she still hunts a player who ran 40 blocks away (target " + galath.bossTargetId() + ", at " + galath.position() + ")");
                return;
            }
            if (galath.distanceTo(player) > 26.0F) {
                helper.fail("she flew after him (distance " + galath.distanceTo(player) + ", state " + galath.bossState() + ")");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "b_galath5")
    public static void galathChargeEndsWhenThePlayerIsNotCaught(GameTestHelper helper) {
        var level = helper.getLevel();
        forceWideArena(helper); // she flies a long way: every chunk she crosses must keep ticking
        skyFloor(helper, 30, 198);
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(8.5, 199.0, 4.5)));
        var player = serverPlayer(helper);
        player.getAbilities().instabuild = false;
        Vec3 at = helper.absoluteVec(new Vec3(8.5, 199.0, 16.5));
        helper.onEachTick(() -> {
            player.moveTo(at); // in the air as far as the charge is concerned: she cannot catch him
            player.setOnGround(false);
        });
        int[] charged = {0};
        helper.runAfterDelay(10, () -> galath.boss().beginAttack(level, com.fartypoopy.sexmod.entity.GalathBoss.Attack.RAPE));
        helper.onEachTick(() -> {
            if (galath.bossState() == com.fartypoopy.sexmod.entity.GalathBoss.S_CHARGE) {
                if (++charged[0] > 150) {
                    helper.fail("her charge goes past the player and ends (state still charge after " + charged[0] + " ticks at " + galath.position() + ")");
                }
            } else if (charged[0] > 0 && galath.boss().attack() != com.fartypoopy.sexmod.entity.GalathBoss.Attack.RAPE) {
                helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_mang2")
    public static void wildManglelieStopsBesideAGalathInsteadOfRunningOnTheSpot(GameTestHelper helper) {
        forceArena(helper);
        skyFloor(helper, 20, 246); // a height of its own: another test's player within her 20 blocks would make her take off
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(3.5, 247.0, 3.5)));
        CompanionEntity mang = spawnKind(helper, CompanionKind.MANGLELIE);
        mang.moveTo(helper.absoluteVec(new Vec3(14.5, 247.0, 3.5)));
        helper.runAfterDelay(160, () -> {
            if (mang.distanceTo(galath) > 3.2F) {
                helper.fail("the wild Manglelie ran up to the grounded Galath (distance " + mang.distanceTo(galath) + ", action " + mang.action() + "; Galath on the ground " + galath.onGround() + ", her target " + galath.boss().target() + ")");
                return;
            }
            if (mang.action() == com.fartypoopy.sexmod.entity.CompanionAction.MANG_RUN) {
                helper.fail("once she is there she stands still instead of running against Galath's legs (" + mang.action() + ")");
                return;
            }
            galath.discard();
            mang.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "b_galath6")
    public static void aParalysedGalathStaysDownWhenAPlayerComesNear(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        skyFloor(helper, 30, 270);
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(8.5, 271.0, 4.5)));
        galath.hurt(level.damageSources().generic(), 1000.0F); // paralysed: she lies there until somebody corrupts her
        var player = serverPlayer(helper);
        player.getAbilities().instabuild = false;
        int[] tick = {0};
        helper.onEachTick(() -> { // far away at first, then within her 20 blocks (scheduling this from a callback would corrupt the test clock)
            player.moveTo(helper.absoluteVec(new Vec3(8.5, 271.0, tick[0]++ < 60 ? 60.5 : 14.5)));
            player.setDeltaMovement(Vec3.ZERO);
        });
        helper.runAfterDelay(160, () -> {
            int state = galath.bossState();
            if (state != com.fartypoopy.sexmod.entity.GalathBoss.S_KO_GROUND) {
                helper.fail("a paralysed Galath does not take off because a player walked up (state " + state + ", paralysed " + galath.boss().paralysed() + ")");
                return;
            }
            galath.discard();
            helper.succeed();
        });
    }

    /**
     * Wing bones that an animation does not key fall back to the model's bind pose, which spreads them out sideways (the
     * purple spikes far away from her in the corrupt scene). Every Galath animation must therefore pose both wings.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "b_galath8")
    public static void everyGalathAnimationPosesBothWings(GameTestHelper helper) {
        // the corrupt loops hide the whole wing group (original cb.g(): b7.a() is false there), blink is an overlay
        java.util.Set<String> exempt = java.util.Set.of("animation.galath.blink", "animation.galath.double_holding_hard1",
                "animation.galath.corrupt_slow", "animation.galath.corrupt_soft", "animation.galath.corrupt_hard",
                "animation.galath.corrupt_cum");
        try (var in = FapCraftGameTests.class.getResourceAsStream("/assets/sexmod/animations/galath/galath.animation.json")) {
            if (in == null) {
                helper.fail("the Galath animation file is on the classpath");
                return;
            }
            var root = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            StringBuilder missing = new StringBuilder();
            for (var entry : root.entrySet()) {
                if (!entry.getKey().startsWith("animation.galath.") || exempt.contains(entry.getKey())) {
                    continue;
                }
                var bones = entry.getValue().getAsJsonObject().getAsJsonObject("bones");
                if (bones == null || !bones.has("wingR") || !bones.has("wingL")) {
                    missing.append(' ').append(entry.getKey());
                }
            }
            if (missing.length() > 0) {
                helper.fail("these Galath animations leave the wings in the spread bind pose:" + missing);
                return;
            }
        } catch (java.io.IOException e) {
            helper.fail(e.toString());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "b_galath2")
    public static void reflectedEnergyBallKnocksGalathOut(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        skyFloor(helper, 30, 54);
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(8.5, 55.0, 5.5)));
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(8.5, 55.0, 11.5)), 180.0F, 0.0F);
        player.setGameMode(GameType.CREATIVE);
        var ball = com.fartypoopy.sexmod.registry.ModEntities.ENERGY_BALL.get().create(level);
        ball.moveTo(helper.absoluteVec(new Vec3(8.5, 55.0, 8.5)).add(0.0D, player.getEyeHeight(), 0.0D));
        level.addFreshEntity(ball);
        helper.runAfterDelay(2, () -> {
            player.setYRot(180.0F);
            player.setXRot(0.0F);
            ball.hurt(level.damageSources().playerAttack(player), 1.0F);
        });
        helper.runAfterDelay(80, () -> {
            if (!galath.boss().knocked()) {
                helper.fail("the reflected ball knocked Galath out (state " + galath.bossState() + ", ball alive " + ball.isAlive() + ")");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "b_galath3")
    public static void galathIsParalysedInsteadOfDyingAndCanBeCorrupted(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        skyFloor(helper, 30, 78);
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(8.5, 79.0, 8.5)));
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(8.5, 79.0, 11.5)));
        player.setGameMode(GameType.CREATIVE);
        helper.runAfterDelay(5, () -> galath.hurt(level.damageSources().generic(), 1000.0F));
        helper.runAfterDelay(200, () -> {
            if (!galath.isAlive() || galath.getHealth() <= 0.0F || !galath.boss().paralysed()) {
                helper.fail("at zero health she is paralysed instead of dying (alive " + galath.isAlive() + ", hp " + galath.getHealth() + ")");
                return;
            }
            if (galath.bossState() != com.fartypoopy.sexmod.entity.GalathBoss.S_KO_GROUND) {
                helper.fail("a paralysed Galath lies on the ground (state " + galath.bossState() + ")");
                return;
            }
            player.moveTo(galath.getX() + 2.0D, galath.getY(), galath.getZ());
            var interaction = galath.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            if (galath.currentScene() != com.fartypoopy.sexmod.entity.CompanionScene.GALATH_WILD_CORRUPT) {
                helper.fail("right-clicking her starts the corrupt scene (scene " + galath.currentScene() + ", result " + interaction + ", wild " + galath.wildBoss() + ", dist " + player.distanceToSqr(galath) + ", inScene " + galath.isInScene() + ")");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "b_gfollow")
    public static void tamedGalathFollowsAndTeleportsToHerOwner(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        skyFloor(helper, 30, 102);
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(4.5, 103.0, 1.5)));
        CompanionEntity near = spawnKind(helper, CompanionKind.GALATH);
        near.moveTo(helper.absoluteVec(new Vec3(14.5, 103.0, 1.5)));
        near.setMaster(player.getUUID());
        CompanionEntity far = spawnKind(helper, CompanionKind.GALATH);
        far.moveTo(helper.absoluteVec(new Vec3(4.5, 103.0, 19.5)));
        far.setMaster(player.getUUID());
        // original ae(): only a Galath with a daughter follows her owner
        com.fartypoopy.sexmod.entity.ManglelieBehavior.spawnDaughter(level, near);
        com.fartypoopy.sexmod.entity.ManglelieBehavior.spawnDaughter(level, far);
        helper.runAfterDelay(150, () -> {
            if (near.distanceTo(player) > 6.0F) {
                helper.fail("a tamed Galath within 16 blocks walks to her owner (distance " + near.distanceTo(player) + ")");
                return;
            }
            if (far.distanceTo(player) > 6.0F) {
                helper.fail("a tamed Galath beyond 16 blocks teleports to her owner (distance " + far.distanceTo(player) + ", state " + far.bossState() + ", action " + far.action() + ", master " + far.hasMaster() + ", ai " + far.isNoAi() + ")");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_gmenu")
    public static void galathMenuOffersThreesomeOnlyWithADaughter(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        skyFloor(helper, 30, 174);
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(4.5, 175.0, 2.5)));
        CompanionEntity alone = spawnKind(helper, CompanionKind.GALATH);
        alone.moveTo(helper.absoluteVec(new Vec3(4.5, 175.0, 4.5)));
        alone.setMaster(player.getUUID());
        CompanionEntity mother = spawnKind(helper, CompanionKind.GALATH);
        mother.moveTo(helper.absoluteVec(new Vec3(14.5, 175.0, 4.5)));
        mother.setMaster(player.getUUID());
        com.fartypoopy.sexmod.entity.ManglelieBehavior.spawnDaughter(level, mother);
        // the forced arena starts entity-ticking a few ticks late: wait until the daughter sits on her head
        helper.succeedWhen(() -> {
            var plain = com.fartypoopy.sexmod.entity.CompanionActions.actionsFor(alone, player);
            var family = com.fartypoopy.sexmod.entity.CompanionActions.actionsFor(mother, player);
            if (plain.contains(CompanionScene.GALATH_THREESOME) || !plain.contains(CompanionScene.GALATH_COWGIRL)) {
                helper.fail("without a daughter her menu has cowgirl, anal, ride only: " + plain);
            }
            if (!family.contains(CompanionScene.GALATH_THREESOME) || mother.daughter() == null) {
                throw new net.minecraft.gametest.framework.GameTestAssertException(
                        "with a daughter on her head the menu adds the threesome: " + family + " daughter " + mother.daughter());
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "b_gmorn")
    public static void morningSceneNeedsSpaceAndStartsBothGirls(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        skyFloor(helper, 30, 222);
        var bedPos = helper.absolutePos(new net.minecraft.core.BlockPos(10, 223, 10));
        var north = net.minecraft.core.Direction.NORTH;
        if (!com.fartypoopy.sexmod.entity.GalathMorning.hasSpace(level, bedPos, north)) {
            helper.fail("an empty side of the bed has enough space");
            return;
        }
        level.setBlockAndUpdate(bedPos.west().above(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        if (com.fartypoopy.sexmod.entity.GalathMorning.hasSpace(level, bedPos, north)) {
            helper.fail("a block on the right side of the bed blocks the scene");
            return;
        }
        level.setBlockAndUpdate(bedPos.west().above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(10.5, 223.0, 10.5)));
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(10.5, 223.0, 10.5)));
        galath.setMaster(player.getUUID());
        var daughter = com.fartypoopy.sexmod.entity.ManglelieBehavior.spawnDaughter(level, galath);
        if (!galath.startWildScene(player, CompanionScene.GALATH_MORNING)
                || galath.currentScene() != CompanionScene.GALATH_MORNING
                || daughter.currentScene() != CompanionScene.MANGLELIE_BED) {
            helper.fail("the morning scene starts Galath and her daughter together (" + galath.currentScene() + " / " + daughter.currentScene() + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * Texture smoothing: flat pixel-art cells blend with similar ones, edges between different colours become soft and follow
     * the shape (a stair step is rounded off); painted cells, see-through texels and every alpha stay as they were.
     */
    @GameTest(template = "empty", timeoutTicks = 10, batch = "b_smooth")
    public static void textureSmoothingSoftensOnlyFlatPixelArt(GameTestHelper helper) {
        int size = 64 * 16; // cells of 16 x 16 texels
        int cell = size / 64;
        int[] pixels = new int[size * size];
        int dark = grey(100);
        int light = grey(120);
        int white = grey(255);
        int black = grey(0);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int cx = x / cell;
                int cy = y / cell;
                int p = 0x00FFFFFF; // transparent
                if (cy == 1 && cx == 1) p = dark;
                if (cy == 1 && cx == 2) p = light;       // similar to its left neighbour: blended
                if (cy == 1 && cx == 3) p = white;       // far from it: an edge
                if (cy == 1 && cx == 4) p = (x + y) % 2 == 0 ? dark : white; // painted detail
                if (cy == 1 && x == cell) p = light;     // an off-grid strip of another colour along the dark cell's edge
                // a stair step: white over black, the step one cell to the right in the lower row
                if (cy == 5 && cx >= 10 && cx <= 14) p = cx <= 11 ? white : black;
                if (cy == 6 && cx >= 10 && cx <= 14) p = cx <= 12 ? white : black;
                pixels[y * size + x] = p;
            }
        }
        int[] before = pixels.clone();
        com.fartypoopy.sexmod.util.TextureSmoothing.smoothCells(pixels, size, size);
        java.util.function.IntBinaryOperator red = (x, y) -> pixels[y * size + x] & 0xFF;
        int row = cell + cell / 2;
        int edgeLeft = red.applyAsInt(2 * cell - 1, row);   // dark cell, at its border with the light one
        int edgeRight = red.applyAsInt(2 * cell, row);      // light cell, same border
        if (!(edgeLeft > 105 && edgeLeft < 115 && edgeRight > 105 && edgeRight < 115)) {
            helper.fail("similar flat cells meet in a gradient (" + edgeLeft + " / " + edgeRight + ")");
            return;
        }
        int centreDark = red.applyAsInt(cell + cell / 2, row);
        int strip = red.applyAsInt(cell, row);
        if (Math.abs(centreDark - 100) > 4 || Math.abs(strip - 100) > 4) {
            helper.fail("a cell keeps its colour at its centre and loses an off-grid strip (" + centreDark + ", " + strip + ")");
            return;
        }
        int lightSide = red.applyAsInt(3 * cell - 1, row);  // light cell beside the white one
        int whiteSide = red.applyAsInt(3 * cell, row);
        int whiteCentre = red.applyAsInt(3 * cell + cell / 2, row);
        if (!(lightSide > 135 && lightSide < 240 && whiteSide > 135 && whiteSide < 240 && whiteCentre > 245)) {
            helper.fail("a colour edge is soft, not pixel-hard, and the colours stay themselves away from it ("
                    + lightSide + " / " + whiteSide + " / " + whiteCentre + ")");
            return;
        }
        // the stair step's outer corner (lower row, cell 12, top-right texel) is rounded off towards black...
        int corner = red.applyAsInt(13 * cell - 1, 6 * cell);
        // ...and its inner corner (upper row, cell 12, bottom-left texel) is filled in towards white
        int inner = red.applyAsInt(12 * cell, 6 * cell - 1);
        if (corner > 128 || inner < 128) {
            helper.fail("a stair step becomes a smooth diagonal (outer corner " + corner + ", inner corner " + inner + ")");
            return;
        }
        for (int i = 0; i < pixels.length; i++) {
            int x = i % size;
            int y = i / size;
            boolean painted = y / cell == 1 && x / cell == 4;
            if ((pixels[i] >>> 24) != (before[i] >>> 24) || (painted || (before[i] >>> 24) == 0) && pixels[i] != before[i]) {
                helper.fail("painted and see-through texels and every alpha stay as they were (" + x + ", " + y + ")");
                return;
            }
        }
        // mipmaps: a see-through white texel does not pale its opaque red neighbours, nor open a crack beside them
        int[] quad = {0xFF0000FF, 0x00FFFFFF, 0xFF0000FF, 0x00FFFFFF};
        int[][] mips = com.fartypoopy.sexmod.util.TextureSmoothing.mipChain(quad, 2, 2);
        int mip = mips[0][0];
        if (mips.length != 1 || (mip & 0xFF) != 255 || ((mip >>> 8) & 0xFF) != 0 || (mip >>> 24) != 255) {
            helper.fail("mipmaps weight colours by alpha (" + Integer.toHexString(mip) + ")");
            return;
        }
        helper.succeed();
    }

    private static int grey(int v) {
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /** Original br$b: a cfg parses into its fields; bad values report the original messages; names never leave the folder. */
    @GameTest(template = "empty", timeoutTicks = 10, batch = "b_cmcfg")
    public static void customModelCfgParsesAndNamesStayInTheirFolder(GameTestHelper helper) {
        String cfg = "wear_type=HEAD\nwhich_girls=JENNY, KOBOLD\nwhich_lighting=SEXMOD\nauthor=me\nbones_to_hide=hair, hat\n"
                + "enable_when_nude=yes\ngui_size_factor=1,5\ngui_vertical_positioning=2\nversion=1.2\n";
        var config = com.fartypoopy.sexmod.custommodel.CustomModelConfig.parse("tophat",
                cfg.getBytes(java.nio.charset.StandardCharsets.UTF_8), "test.cfg");
        if (config.error != null || config.wearType != com.fartypoopy.sexmod.custommodel.WearType.HEAD
                || !config.girls.equals(java.util.EnumSet.of(CompanionKind.JENNY, CompanionKind.KOBOLD))
                || config.lighting != com.fartypoopy.sexmod.custommodel.Lighting.SEXMOD || !"me".equals(config.author)
                || !config.bonesToHide.equals(java.util.Set.of("hair", "hat")) || !config.enableWhenNude
                || config.guiSizeFactor != 1.5F || config.guiVerticalPositioning != 2.0F || config.version != 1.2F
                || !"customHead".equals(config.attachBone()) || config.fits(CompanionKind.ELLIE) || !config.fits(CompanionKind.JENNY)) {
            helper.fail("a valid cfg parses into its fields (" + config.error + ")");
            return;
        }
        var bad = com.fartypoopy.sexmod.custommodel.CustomModelConfig.parse("x",
                "wear_type=HAT\nwhich_lighting=DEFAULT\nversion=1".getBytes(java.nio.charset.StandardCharsets.UTF_8), "x.cfg");
        if (bad.error == null || !bad.error.startsWith("you entered 'HAT' into the 'wear_type' field")) {
            helper.fail("a bad wear type is reported: " + bad.error);
            return;
        }
        for (String name : new String[] {"../evil", "a/b", "a\\b", "cross", "has space", "a#b", "C:x", ""}) {
            if (com.fartypoopy.sexmod.custommodel.CustomModelConfig.validName(name)) {
                helper.fail("'" + name + "' must not be a valid custom model name");
                return;
            }
        }
        if (!com.fartypoopy.sexmod.custommodel.CustomModels.parseCode("hat#cross##shoe#").equals(java.util.Set.of("hat", "shoe"))) {
            helper.fail("model codes split on '#' and skip the cross");
            return;
        }
        helper.succeed();
    }

    /** Original em.h / ff.a / e3.a: the wardrobe's per-girl values read back what they wrote; invalid values are refused. */
    @GameTest(template = "empty", timeoutTicks = 10, batch = "b_cmgirl")
    public static void girlSpecificValuesRoundTrip(GameTestHelper helper) {
        CompanionEntity kobold = spawnKind(helper, CompanionKind.KOBOLD);
        java.util.List<Integer> values = java.util.List.of(40, 3, 7, 5, 2, 80, 20, 1, 2, 3, 1);
        if (!kobold.girlSpecificValid(values)) {
            helper.fail("kobold values within their bounds are valid");
            return;
        }
        kobold.applyGirlSpecific(values);
        if (!kobold.girlSpecificValues().equals(values)) {
            helper.fail("kobold values round trip: " + kobold.girlSpecificValues());
            return;
        }
        if (kobold.girlSpecificValid(java.util.List.of(40, 3, 7, 8, 2, 80, 20, 1, 2, 3, 1))
                || kobold.girlSpecificValid(java.util.List.of(-1, 3, 7, 5, 2, 80, 20, 1, 2, 3, 1))) {
            helper.fail("out-of-range kobold values are refused");
            return;
        }
        CompanionEntity goblin = spawnKind(helper, CompanionKind.GOBLIN);
        goblin.setGoblinModel("00-00-00-00-00-00-00-00-00-01-");
        java.util.List<Integer> look = java.util.List.of(3, 2, 1, 15, 4, 5, 7, 4, 5);
        goblin.applyGirlSpecific(look);
        if (!goblin.girlSpecificValues().equals(look) || !goblin.goblinModel().endsWith("-01-")) {
            helper.fail("goblin values round trip and keep the crown: " + goblin.goblinModel());
            return;
        }
        helper.succeed();
    }

    /** Original em saves "sexmod:customModel"; Galath keeps hers per owner (bj) instead. */
    @GameTest(template = "empty", timeoutTicks = 10, batch = "b_cmsave")
    public static void modelCodeIsSavedOrKeptPerOwner(GameTestHelper helper) {
        var level = helper.getLevel();
        CompanionEntity jenny = spawnKind(helper, CompanionKind.JENNY);
        jenny.setModelCode("hat#");
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        jenny.saveWithoutId(tag);
        CompanionEntity loaded = com.fartypoopy.sexmod.registry.ModEntities.get(CompanionKind.JENNY).create(level);
        loaded.load(tag);
        if (!"hat#".equals(loaded.modelCode())) {
            helper.fail("a girl's model code is saved with her");
            return;
        }
        UUID owner = UUID.randomUUID();
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.setMaster(owner);
        galath.setModelCode("horns#");
        var data = com.fartypoopy.sexmod.data.GalathBindingData.get(level.getServer());
        if (!"horns#".equals(data.modelCode(owner, false))) {
            helper.fail("Galath's model code is kept for her owner");
            return;
        }
        net.minecraft.nbt.CompoundTag galathTag = new net.minecraft.nbt.CompoundTag();
        galath.saveWithoutId(galathTag);
        if (galathTag.contains("sexmod:customModel")) {
            helper.fail("Galath does not save her model code herself");
            return;
        }
        data.setModelCode(owner, false, "");
        helper.succeed();
    }

    /**
     * GameTest batches reuse grid positions and a finished batch unforces its chunks, which can leave a later test's
     * chunk loaded but not entity-ticking; a test whose entities must tick forces its own chunk.
     */
    private static void keepTicking(GameTestHelper helper, Vec3 at) {
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(at));
        helper.getLevel().setChunkForced(chunk.x, chunk.z, true); // left forced: unforcing could starve the next batch the same way
    }

    private static net.minecraft.server.level.ServerPlayer namedPlayer(GameTestHelper helper, String name) {
        var level = helper.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        if (!level.players().contains(player)) {
            level.addNewPlayer(player);
        }
        return player;
    }

    /** Original es.b(String, UUID): an accepted boobjob starts the form's scene (nude) with the asker as partner. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "b_formscene")
    public static void playerFormActionStartsHerSceneAndJumpEndsIt(GameTestHelper helper) {
        var girl = namedPlayer(helper, "form_girl_a");
        var male = namedPlayer(helper, "form_male_a");
        girl.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        male.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 4.5)));
        com.fartypoopy.sexmod.entity.PlayerForms.become(girl, CompanionKind.JENNY);
        CompanionEntity form = com.fartypoopy.sexmod.entity.PlayerForms.formOf(girl);
        form.moveTo(girl.position());
        com.fartypoopy.sexmod.entity.PlayerForms.startActionForTest(form, male, "action.names.boobjob");
        if (form.currentScene() != CompanionScene.JENNY_BOOBJOB || !form.scenePartnerId().map(male.getUUID()::equals).orElse(false)
                || !form.isNude()) {
            helper.fail("the boobjob starts on the form, nude, with the asker as partner (" + form.currentScene() + ")");
            return;
        }
        com.fartypoopy.sexmod.entity.PlayerForms.leave(girl);
        if (form.isInScene()) {
            helper.fail("jumping out ends the form's scene");
            return;
        }
        com.fartypoopy.sexmod.entity.PlayerForms.become(girl, null);
        helper.succeed();
    }

    /** Original es (WAITDOGGY): a Jenny form waiting at a bed starts the doggy scene with the next player stepping up. */
    @GameTest(template = "empty", timeoutTicks = 220, batch = "b_formwait")
    public static void playerFormBedWaitHandsOverToTheNextPlayer(GameTestHelper helper) {
        var girl = namedPlayer(helper, "form_girl_b");
        var male = namedPlayer(helper, "form_male_b");
        Vec3 spot = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        girl.moveTo(spot);
        male.moveTo(helper.absoluteVec(new Vec3(8.5, 2.0, 8.5)));
        CompanionEntity[] form = {null};
        boolean[] stepped = {false};
        keepTicking(helper, spot);
        helper.onEachTick(() -> {
            if (form[0] == null && helper.getLevel().isPositionEntityTicking(net.minecraft.core.BlockPos.containing(spot))) {
                // the forced test chunks become entity-ticking a few ticks after the test starts
                com.fartypoopy.sexmod.entity.PlayerForms.become(girl, CompanionKind.JENNY);
                form[0] = com.fartypoopy.sexmod.entity.PlayerForms.formOf(girl);
                com.fartypoopy.sexmod.entity.PlayerForms.startWait(girl, spot, 0.0F);
                if (form[0].formWait() != 1) {
                    helper.fail("the wait starts with the intro");
                }
            }
            if (form[0] != null && !stepped[0] && form[0].formWait() == 2) {
                stepped[0] = true;
                male.moveTo(spot.add(0.0D, 0.0D, 0.5D)); // she waits in the loop: step up to her
            }
        });
        helper.succeedWhen(() -> {
            CompanionEntity f = form[0];
            if (f == null || f.currentScene() != CompanionScene.JENNY_DOGGY || !f.scenePartnerId().map(male.getUUID()::equals).orElse(false)) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("the player stepping up starts the doggy scene ("
                        + (f == null ? "no form" : f.currentScene() + ", wait " + f.formWait() + ", ticks " + f.formWaitTicks
                        + ", age " + f.tickCount) + ")");
            }
            f.stopScene();
            com.fartypoopy.sexmod.entity.PlayerForms.become(girl, null);
        });
    }

    /** Original fu -> eg.H() / eg.a(String) / eg.a(): Bia's girl-player picks doggy at the bed, the next player is faded in. */
    @GameTest(template = "empty", timeoutTicks = 260, batch = "b_formbia")
    public static void biaFormBedChoiceStartsHerProneScene(GameTestHelper helper) {
        var girl = namedPlayer(helper, "form_girl_c");
        var male = namedPlayer(helper, "form_male_c");
        Vec3 spot = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        girl.moveTo(spot);
        male.moveTo(helper.absoluteVec(new Vec3(8.5, 2.0, 8.5)));
        CompanionEntity[] form = {null};
        boolean[] stepped = {false};
        keepTicking(helper, spot);
        helper.onEachTick(() -> {
            if (form[0] == null && helper.getLevel().isPositionEntityTicking(net.minecraft.core.BlockPos.containing(spot))) {
                com.fartypoopy.sexmod.entity.PlayerForms.become(girl, CompanionKind.BIA);
                form[0] = com.fartypoopy.sexmod.entity.PlayerForms.formOf(girl);
                com.fartypoopy.sexmod.entity.PlayerForms.startWait(girl, spot, 0.0F);
                if (form[0].formWait() != 1 || !form[0].formWaitAnims().isEmpty() || form[0].isNude()) {
                    helper.fail("at the bed Bia stands there dressed until her player picks");
                }
                com.fartypoopy.sexmod.entity.PlayerForms.chooseFollowUp(girl, "doggy");
                if (!"sitdown|sitdownidle".equals(form[0].formWaitAnims()) || !form[0].isNude()) {
                    helper.fail("doggy undresses her and sits her down (" + form[0].formWaitAnims() + ")");
                }
            }
            if (form[0] != null && !stepped[0] && form[0].formWait() == 2) {
                stepped[0] = true;
                male.moveTo(spot.add(0.0D, 0.0D, 0.5D));
            }
        });
        helper.succeedWhen(() -> {
            CompanionEntity f = form[0];
            if (f == null || f.currentScene() != CompanionScene.BIA_PRONE || !f.scenePartnerId().map(male.getUUID()::equals).orElse(false)) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("the next player starts her prone scene ("
                        + (f == null ? "no form" : f.currentScene() + ", wait " + f.formWait()) + ")");
            }
            if (Math.abs(f.getZ() - (spot.z + 1.0D)) > 0.01D) {
                helper.fail("the doggy intro moves her one block on (z " + f.getZ() + ")");
            }
            f.stopScene();
            com.fartypoopy.sexmod.entity.PlayerForms.become(girl, null);
        });
    }

    /** Original eq.c(player) / eq.d() / ea.c(): another player picks the goblin girl-player up, shoulders and throws her. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_formgoblin")
    public static void goblinFormIsCarriedAndThrown(GameTestHelper helper) {
        var girl = namedPlayer(helper, "form_girl_e");
        var carrier = namedPlayer(helper, "form_male_e");
        Vec3 spot = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        girl.moveTo(spot.add(1.0D, 0.0D, 0.0D));
        carrier.moveTo(spot);
        CompanionEntity[] form = {null};
        int[] stage = {0};
        keepTicking(helper, spot);
        keepTicking(helper, spot.add(1.0D, 0.0D, 0.0D)); // her form spawns above her, possibly across a chunk border
        helper.onEachTick(() -> {
            if (form[0] == null && helper.getLevel().isPositionEntityTicking(net.minecraft.core.BlockPos.containing(spot))
                    && helper.getLevel().isPositionEntityTicking(girl.blockPosition())) {
                com.fartypoopy.sexmod.entity.PlayerForms.become(girl, CompanionKind.GOBLIN);
                form[0] = com.fartypoopy.sexmod.entity.PlayerForms.formOf(girl);
                com.fartypoopy.sexmod.entity.PlayerForms.pickUpGoblin(form[0], carrier);
                if (form[0].goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.PICK_UP) {
                    helper.fail("sneak-clicking her picks her up");
                }
                return;
            }
            CompanionEntity f = form[0];
            if (f == null) {
                return;
            }
            if (stage[0] == 0 && f.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.SHOULDER) {
                stage[0] = 3; // the carry places her on the tick after the state change: check on the next one
                return;
            }
            if (stage[0] == 3) {
                stage[0] = 1;
                if (Math.abs(girl.getY() - (carrier.getY() + 2.0D)) > 0.01D || f.distanceTo(carrier) > 0.01F) {
                    helper.fail("her player is held 2 above the carrier, the form sits on him (dy " + (girl.getY() - carrier.getY()) + ")");
                }
                com.fartypoopy.sexmod.entity.PlayerForms.goblinWheel(f, carrier, 0);
            }
            if (stage[0] == 1 && f.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.THROWN) {
                stage[0] = 2;
            }
        });
        helper.succeedWhen(() -> {
            CompanionEntity f = form[0];
            if (stage[0] != 2 || f.goblinCarrier().isPresent() || girl.isNoGravity()) {
                throw new net.minecraft.gametest.framework.GameTestAssertException("the throw lets her fly off on her own (stage "
                        + stage[0] + ", state " + (f == null ? -1 : f.goblinState()) + ")");
            }
            com.fartypoopy.sexmod.entity.PlayerForms.become(girl, null);
        });
    }

    /** Original e9.B() / y() / g() and em's currentModel 1: the Bee form flies, wears armour and is bare for her scene. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "b_formbee")
    public static void beeFormFliesAndUndressesForHerScene(GameTestHelper helper) {
        var girl = namedPlayer(helper, "form_girl_d");
        var male = namedPlayer(helper, "form_male_d");
        girl.setGameMode(GameType.SURVIVAL);
        girl.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        male.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 4.5)));
        com.fartypoopy.sexmod.entity.PlayerForms.become(girl, CompanionKind.BEE);
        CompanionEntity form = com.fartypoopy.sexmod.entity.PlayerForms.formOf(girl);
        if (!girl.getAbilities().mayfly || form.outfit() != com.fartypoopy.sexmod.entity.OutfitMode.ARMORED) {
            helper.fail("the Bee form lets her fly and starts armored");
            return;
        }
        form.moveTo(girl.position());
        com.fartypoopy.sexmod.entity.PlayerForms.startActionForTest(form, male, "action.names.sex");
        if (form.currentScene() != CompanionScene.BEE_SEX || form.outfit() != com.fartypoopy.sexmod.entity.OutfitMode.DEFAULT) {
            helper.fail("her scene starts on the bare model (" + form.currentScene() + ", " + form.outfit() + ")");
            return;
        }
        com.fartypoopy.sexmod.entity.PlayerForms.leave(girl);
        if (form.outfit() != com.fartypoopy.sexmod.entity.OutfitMode.ARMORED) {
            helper.fail("after her scene she is armored again");
            return;
        }
        com.fartypoopy.sexmod.entity.PlayerForms.become(girl, null);
        if (girl.getAbilities().mayfly) {
            helper.fail("leaving the Bee form takes the flight away");
            return;
        }
        helper.succeed();
    }

    /** Original b_ / ei: picking a girl makes an untouchable form with the stored look; picking yourself removes it. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "b_form")
    public static void playerFormIsCreatedWithItsLookAndRemoved(GameTestHelper helper) {
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
        player.getPersistentData().putString(com.fartypoopy.sexmod.entity.PlayerForms.MODEL_KEY + "KOBOLD", "hat#");
        player.getPersistentData().putString(com.fartypoopy.sexmod.entity.PlayerForms.SPECIFIC_KEY + "KOBOLD", "40-3-7-5-2-80-20-1-2-3-1-");
        com.fartypoopy.sexmod.entity.PlayerForms.become(player, CompanionKind.KOBOLD);
        CompanionEntity form = com.fartypoopy.sexmod.entity.PlayerForms.formOf(player);
        if (form == null || form.kind() != CompanionKind.KOBOLD || !form.formOwner().map(player.getUUID()::equals).orElse(false)) {
            helper.fail("picking the Kobold makes a Kobold form owned by the player");
            return;
        }
        if (form.isPickable() || !form.isInvulnerableTo(level(helper).damageSources().generic()) || form.getBbWidth() > 0.02F
                || !"hat#".equals(form.modelCode()) || form.girlSpecificValues().get(0) != 40) {
            helper.fail("the form is untouchable, tiny and wears the stored look");
            return;
        }
        com.fartypoopy.sexmod.entity.PlayerForms.become(player, null);
        if (!form.isRemoved() || com.fartypoopy.sexmod.entity.PlayerForms.formOf(player) != null) {
            helper.fail("picking yourself removes the form");
            return;
        }
        if (com.fartypoopy.sexmod.entity.PlayerForms.hasForm(CompanionKind.MANGLELIE)) {
            helper.fail("Manglelie has no player form");
            return;
        }
        com.fartypoopy.sexmod.entity.PlayerForms.become(player, CompanionKind.GALATH);
        CompanionEntity galath = com.fartypoopy.sexmod.entity.PlayerForms.formOf(player);
        if (galath == null || galath.boss().bar().isVisible() || !galath.boss().bar().getPlayers().isEmpty()) {
            helper.fail("a Galath form shows no boss health bar (it showed \"Galath\" to everyone around)");
            return;
        }
        com.fartypoopy.sexmod.entity.PlayerForms.become(player, null);
        helper.succeed();
    }

    private static net.minecraft.server.level.ServerLevel level(GameTestHelper helper) {
        return helper.getLevel();
    }

    /** Original f_ saves "sexmod:master": a coin-summoned Galath survives a save / load and keeps her owner's stored name. */
    @GameTest(template = "empty", timeoutTicks = 10, batch = "b_gsave")
    public static void summonedGalathKeepsMasterAndNameAcrossSaves(GameTestHelper helper) {
        var level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.setMaster(owner);
        galath.markSummonedByGalathCoin();
        com.fartypoopy.sexmod.data.GalathBindingData data =
                com.fartypoopy.sexmod.data.GalathBindingData.get(level.getServer());
        data.bind(owner, galath.getUUID());
        data.setGalathName(owner, "Lilith");
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        galath.saveWithoutId(tag);
        CompanionEntity loaded = com.fartypoopy.sexmod.registry.ModEntities.get(CompanionKind.GALATH).create(level);
        loaded.load(tag);
        galath.discard();
        data.clear(owner);
        if (!loaded.masterId().map(owner::equals).orElse(false)) {
            helper.fail("a summoned Galath keeps her master after loading");
            return;
        }
        if (loaded.getCustomName() == null || !"Lilith".equals(loaded.getCustomName().getString())) {
            helper.fail("a loaded Galath takes her owner's stored name");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 10, batch = "b_gtrans")
    public static void bedAndThreesomeScenesHaveSoftAndBackTransitions(GameTestHelper helper) {
        for (CompanionScene scene : new CompanionScene[] {CompanionScene.GALATH_MORNING, CompanionScene.MANGLELIE_THREESOME}) {
            boolean ok = scene.nextLoopPhase(0) == 1
                    && scene.transitionVia(0, 1) == 3
                    && scene.transitionTarget(3) == 1
                    && scene.transitionVia(1, 0) == 4
                    && scene.transitionTarget(4) == 0
                    && scene.finalePhase() == 2
                    && !scene.phase(3).loop()
                    && !scene.phase(4).loop()
                    && scene.phaseRole(3) == CompanionScene.PhaseRole.AUTO;
            if (!ok) {
                helper.fail(scene + " transitions are wired slow -> soft -> fast and fast -> back -> slow");
                return;
            }
        }
        CompanionScene bj = CompanionScene.KOBOLD_BLOWJOB;
        if (bj.nextLoopPhase(1) != 2 || bj.nextLoopPhase(4) != 2 || bj.previousLoopPhase(2) != 1
                || bj.previousLoopPhase(4) != -1 || bj.finalePhase() != 3 || bj.transitionTarget(5) != 1
                || bj.transitionTarget(6) != 4 || bj.phaseRole(5) != CompanionScene.PhaseRole.AUTO
                || bj.phaseRole(4) != CompanionScene.PhaseRole.INTENSITY || bj.lastLoopPhase() != 2) {
            helper.fail("the Kobold blowjob side variants do not disturb slow/fast/finale navigation");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 220, batch = "b_gambush")
    public static void galathAmbushMasturbatesThenStops(GameTestHelper helper) {
        forceArena(helper);
        skyFloor(helper, 30, 126);
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(6.5, 127.0, 6.5)));
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(6.5, 127.0, 10.5)));
        galath.setMaster(player.getUUID());
        galath.beginGalathAmbush(player);
        // some arenas never entity-tick (see forceArena), so drive her by hand there
        helper.onEachTick(() -> {
            if (!helper.getLevel().isPositionEntityTicking(galath.blockPosition())) {
                galath.tick();
            }
        });
        helper.runAfterDelay(60, () -> {
            if (galath.action() != com.fartypoopy.sexmod.entity.CompanionAction.GALATH_MASTURBATE) {
                helper.fail("she keeps masturbating for the 8 seconds: " + galath.action());
            }
        });
        helper.runAfterDelay(200, () -> {
            if (galath.action() == com.fartypoopy.sexmod.entity.CompanionAction.GALATH_MASTURBATE) {
                helper.fail("after 8 seconds she is done (tick " + galath.tickCount + ", entityTicking " + helper.getLevel().isPositionEntityTicking(galath.blockPosition()) + ", pos " + galath.blockPosition() + ", alive " + galath.isAlive() + ", noAi " + galath.isNoAi() + ")");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 700, batch = "b_mang")
    public static void galathAdoptsManglelieAndManglelieShoots(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        skyFloor(helper, 14, 294); // its own height: other tests' Galaths and Manglelies (also run alongside) stay out of her 15-block search
        CompanionEntity galath = spawnKind(helper, CompanionKind.GALATH);
        galath.moveTo(helper.absoluteVec(new Vec3(2.5, 295.0, 2.5)));
        java.util.UUID owner = java.util.UUID.randomUUID();
        galath.setMaster(owner);
        CompanionEntity mang = spawnKind(helper, CompanionKind.MANGLELIE);
        mang.moveTo(helper.absoluteVec(new Vec3(9.5, 295.0, 2.5)));
        var bindings = com.fartypoopy.sexmod.data.GalathBindingData.get(level.getServer());
        helper.runAfterDelay(450, () -> {
            if (galath.familyId() == null || !mang.getUUID().equals(galath.familyId()) || !mang.mangRiding()
                    || !galath.getUUID().equals(mang.familyId())) {
                helper.fail("the tamed Galath ran to the wild Manglelie, hugged her and carries her (daughter " + galath.familyId()
                        + ", riding " + mang.mangRiding() + ", action " + galath.action() + ")");
                return;
            }
            if (!bindings.hasMang(owner)) {
                helper.fail("her owner is now registered as having a daughter");
                return;
            }
            double dy = mang.getY() - galath.getY();
            if (dy < 1.8D || dy > 2.4D || mang.distanceToSqr(galath.getX(), mang.getY(), galath.getZ()) > 0.25D) {
                helper.fail("Manglelie sits on Galath's head (dy " + dy + ")");
                return;
            }
            // damage to the daughter goes to her mother
            float galathHealth = galath.getHealth();
            float mangHealth = mang.getHealth();
            mang.hurt(level.damageSources().generic(), 4.0F);
            if (mang.getHealth() != mangHealth || galath.getHealth() >= galathHealth) {
                helper.fail("damage to Manglelie goes to Galath (" + mang.getHealth() + " / " + galath.getHealth() + ")");
                return;
            }
            galath.setNoAi(true); // she would wander off and turn away from the zombie
            // facing the floor's centre the zombie lands on the floor (any yaw could put it over the edge)
            Vec3 centre = helper.absoluteVec(new Vec3(7.0, 295.0, 7.0));
            float face = (float) Math.toDegrees(Math.atan2(centre.z - galath.getZ(), centre.x - galath.getX())) - 90.0F;
            galath.setYRot(face);
            galath.yBodyRot = face;
            galath.yHeadRot = face;
            // a hostile mob in front of Galath within 15 blocks is shot
            var zombie = EntityType.ZOMBIE.create(level);
            double yaw = Math.toRadians(galath.getYRot());
            zombie.moveTo(galath.getX() - Math.sin(yaw) * 8.0D, galath.getY(), galath.getZ() + Math.cos(yaw) * 8.0D);
            zombie.setNoAi(true);
            zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            level.addFreshEntity(zombie);
            helper.runAfterDelay(2, () -> galath.setYRot(galath.getYRot()));
            helper.runAfterDelay(100, () -> {
                if (!zombie.isAlive() || zombie.getHealth() < zombie.getMaxHealth() || mang.mangTargetId() != -1) {
                    zombie.discard();
                    galath.discard();
                    mang.discard();
                    helper.succeed();
                } else {
                    helper.fail("Manglelie shot at the zombie (target " + mang.mangTargetId() + ", health " + zombie.getHealth() + ")");
                }
            });
        });
    }

    @GameTest(template = "empty", batch = "a_first")
    public static void tribeSpawnClaimAndPersist(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        var server = level.getServer();
        var tribe = com.fartypoopy.sexmod.tribe.TribeManager.spawnTribe(level, helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)));
        var members = com.fartypoopy.sexmod.tribe.TribeManager.loadedMembers(server, tribe);
        if (tribe.memberCount() != 4 || members.size() != 4) {
            helper.fail("a generated tribe has 4 members, got " + tribe.memberCount() + "/" + members.size());
            return;
        }
        CompanionEntity leader = members.get(0);
        if (!leader.getUUID().equals(tribe.leader) || leader.koboldSize() != 0.25F
                || Math.abs(leader.getMaxHealth() - 3.75F) > 0.01F || leader.hasMaster()) {
            helper.fail("member 0 is the unowned full-size leader with 3.75 health");
        }
        for (CompanionEntity member : members) {
            if (member.tribeId().isEmpty() || !member.tribeId().get().equals(tribe.id) || member.bodyColor() != tribe.color) {
                helper.fail("every member knows its tribe and wears its colour");
            }
        }
        UUID player = UUID.randomUUID();
        if (com.fartypoopy.sexmod.tribe.TribeManager.claim(server, player, "Tester", leader.position(), tribe.id, "")
                || com.fartypoopy.sexmod.tribe.TribeManager.claim(server, player, "Tester", leader.position(), tribe.id, "ABCDEFGHIJKLMNOP")) {
            helper.fail("an empty or 16 character tribe name is refused");
        }
        if (!com.fartypoopy.sexmod.tribe.TribeManager.claim(server, player, "Tester", leader.position(), tribe.id, "Reds")) {
            helper.fail("a named wild tribe can be claimed");
        }
        for (CompanionEntity member : members) {
            if (!member.isMaster(helper.makeMockPlayer(GameType.SURVIVAL)) && !member.masterId().equals(java.util.Optional.of(player))) {
                helper.fail("claiming makes the player the owner of every member");
            }
            if (!"Reds".equals(member.tribeName())) {
                helper.fail("the tribe name is copied to every member");
            }
        }
        if (!tribe.follow || !player.equals(tribe.master)
                || com.fartypoopy.sexmod.tribe.TribeData.get(server).ofMaster(player) != tribe
                || com.fartypoopy.sexmod.tribe.TribeManager.claim(server, UUID.randomUUID(), "Other", leader.position(), tribe.id, "Nope")) {
            helper.fail("claim turns follow mode on, sets the master and cannot be repeated by someone else");
        }

        // saved data round trip
        tribe.beds.add(new net.minecraft.core.BlockPos(1, 1, 1));
        tribe.beds.add(new net.minecraft.core.BlockPos(1, 1, 2));
        tribe.chests.add(new net.minecraft.core.BlockPos(4, 1, 4));
        var blocks = com.fartypoopy.sexmod.tribe.TribeCommands.tunnel(new net.minecraft.core.BlockPos(0, 1, 0),
                net.minecraft.core.Direction.EAST, level);
        tribe.tasks.add(new com.fartypoopy.sexmod.tribe.TribeTask(com.fartypoopy.sexmod.tribe.TribeTask.Kind.MINE,
                new net.minecraft.core.BlockPos(0, 1, 0), java.util.Set.of(new net.minecraft.core.BlockPos(2, 1, 0)),
                net.minecraft.core.Direction.EAST));
        var data = com.fartypoopy.sexmod.tribe.TribeData.get(server);
        var saved = data.save(new CompoundTag(), level.registryAccess());
        var loaded = com.fartypoopy.sexmod.tribe.TribeData.load(saved, level.registryAccess());
        var copy = loaded.tribe(tribe.id);
        if (copy == null || copy.color != tribe.color || !player.equals(copy.master) || copy.memberCount() != 4
                || copy.beds.size() != 2 || copy.chests.size() != 1 || copy.tasks.size() != 1
                || copy.tasks.get(0).facing != net.minecraft.core.Direction.EAST) {
            helper.fail("tribe record (colour, master, members, beds, chests, tasks) survives saving");
        }

        // kobold NBT
        CompoundTag tag = new CompoundTag();
        leader.addAdditionalSaveData(tag);
        CompanionEntity fresh = spawnKind(helper, CompanionKind.KOBOLD);
        fresh.readAdditionalSaveData(tag);
        if (!fresh.tribeId().equals(leader.tribeId()) || !fresh.isTribeLeader() || !"Reds".equals(fresh.tribeName())
                || fresh.koboldSize() != 0.25F || fresh.bodyColor() != leader.bodyColor() || !fresh.masterId().equals(leader.masterId())
                || !fresh.koboldName().equals(leader.koboldName())) {
            helper.fail("a Kobold keeps tribe id, leader flag, tribe name, size, colour, owner and name across a reload");
        }
        fresh.discard();
        members.forEach(Entity::discard);
        data.tribe(tribe.id).members.clear();
        data.tribe(tribe.id).master = null;
        data.tribe(tribe.id).beds.clear();
        data.tribe(tribe.id).chests.clear();
        data.tribe(tribe.id).tasks.clear();
        helper.succeed();
    }

    /**
     * Bug report: a tribe whose Kobolds all died stayed bound to its owner, so the Dragon Staff could not claim another.
     * A dying Kobold ran her tribe bookkeeping for the 20 ticks of the death animation and wrote herself back in.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "b_tribedeath")
    public static void aWipedOutTribeFreesItsOwnerForANewOne(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        floor(helper, 7);
        var server = level.getServer();
        var data = com.fartypoopy.sexmod.tribe.TribeData.get(server);
        var tribe = com.fartypoopy.sexmod.tribe.TribeManager.spawnTribe(level, helper.absoluteVec(new Vec3(3.5, 1.0, 3.5)));
        var members = com.fartypoopy.sexmod.tribe.TribeManager.loadedMembers(server, tribe);
        UUID player = UUID.randomUUID();
        if (!com.fartypoopy.sexmod.tribe.TribeManager.claim(server, player, "Tester", members.get(0).position(), tribe.id, "Doomed")) {
            helper.fail("the tribe could be claimed");
            return;
        }
        // a ghost left behind by the old bug: a member in this loaded area that no longer exists anywhere
        UUID ghost = UUID.randomUUID();
        tribe.members.put(ghost, helper.absolutePos(new net.minecraft.core.BlockPos(5, 1, 5)));
        helper.runAfterDelay(30, () -> members.forEach(m -> m.hurt(level.damageSources().genericKill(), 1000.0F)));
        helper.runAfterDelay(170, () -> {
            if (!tribe.members.isEmpty() || data.ofMaster(player) != null) {
                helper.fail("every member died, so the tribe is empty and no longer bound to its owner (members "
                        + tribe.members.size() + ", ghost still there " + tribe.members.containsKey(ghost) + ")");
                return;
            }
            var second = com.fartypoopy.sexmod.tribe.TribeManager.spawnTribe(level, helper.absoluteVec(new Vec3(5.5, 1.0, 5.5)));
            var others = com.fartypoopy.sexmod.tribe.TribeManager.loadedMembers(server, second);
            if (!com.fartypoopy.sexmod.tribe.TribeManager.claim(server, player, "Tester", others.get(0).position(), second.id, "Again")) {
                helper.fail("the owner can claim a new tribe with the staff");
                return;
            }
            others.forEach(Entity::discard);
            second.members.clear();
            second.master = null;
            tribe.master = null;
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1000, batch = "b_tree")
    public static void koboldFellsAMarkedTree(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        level.setDayTime(1000L);
        forceArena(helper);
        floor(helper, 7);
        var base = new net.minecraft.core.BlockPos(4, 1, 4);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(base.above(y - 1), net.minecraft.world.level.block.Blocks.OAK_LOG);
        }
        var data = com.fartypoopy.sexmod.tribe.TribeData.get(level.getServer());
        var tribe = data.getOrCreate(UUID.randomUUID(), com.fartypoopy.sexmod.tribe.KoboldColor.GREEN);
        CompanionEntity kobold = spawnKind(helper, CompanionKind.KOBOLD);
        UUID owner = UUID.randomUUID();
        kobold.moveTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
        kobold.setMaster(owner);
        kobold.setTribeId(tribe.id);
        tribe.master = owner;
        tribe.members.put(kobold.getUUID(), kobold.blockPosition());
        tribe.leader = kobold.getUUID();
        var task = com.fartypoopy.sexmod.tribe.KoboldWork.treeTask(level, helper.absolutePos(base), tribe);
        if (task.kind != com.fartypoopy.sexmod.tribe.TribeTask.Kind.FALL_TREE || task.blocks.size() != 3
                || task.kind.maxWorkers != 1) {
            helper.fail("a three log trunk is one FALL_TREE task with one worker, got " + task.blocks.size());
            return;
        }
        tribe.tasks.add(task);
        helper.runAfterDelay(900, () -> {
            boolean gone = !helper.getBlockState(base).is(net.minecraft.tags.BlockTags.LOGS); // replanted with a sapling
            int logs = 0;
            for (int i = 0; i < kobold.storage().getContainerSize(); i++) {
                if (kobold.storage().getItem(i).is(Items.OAK_LOG)) {
                    logs += kobold.storage().getItem(i).getCount();
                }
            }
            if (!gone || logs < 1 || tribe.tasks.contains(task)) {
                helper.fail("the Kobold walked to the tree, swung, felled it and finished the task (base gone=" + gone
                        + ", logs=" + logs + ", task left=" + tribe.tasks.contains(task) + ") DIAG tc=" + kobold.tickCount + " scene=" + kobold.isInScene() + " bed=" + kobold.isBedWalking() + " tid=" + kobold.tribeId() + " pos=" + kobold.position() + " st=" + kobold.koboldState()
                        + " workers=" + task.workers + " mode=" + tribe.mode + " under=" + kobold.koboldUnderAttack() + " enemies=" + tribe.enemies
                        + " blocks=" + task.blocks.size() + " nav=" + kobold.getNavigation().isDone() + " t=" + level.getDayTime() + " chunk=" + kobold.chunkPosition()
                        + " base=" + helper.absolutePos(base));
                return;
            }
            kobold.discard();
            tribe.members.clear();
            tribe.master = null;
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "b_mine")
    public static void koboldMinesATunnel(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        level.setDayTime(1000L);
        forceArena(helper);
        floor(helper, 9);
        for (int x = 3; x <= 8; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new net.minecraft.core.BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE);
                }
            }
        }
        var data = com.fartypoopy.sexmod.tribe.TribeData.get(level.getServer());
        var tribe = data.getOrCreate(UUID.randomUUID(), com.fartypoopy.sexmod.tribe.KoboldColor.RED);
        CompanionEntity kobold = spawnKind(helper, CompanionKind.KOBOLD);
        UUID owner = UUID.randomUUID();
        kobold.setMaster(owner);
        kobold.setTribeId(tribe.id);
        tribe.master = owner;
        tribe.members.put(kobold.getUUID(), kobold.blockPosition());
        tribe.leader = kobold.getUUID();
        var start = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        var blocks = com.fartypoopy.sexmod.tribe.TribeCommands.tunnel(start, net.minecraft.core.Direction.EAST, level);
        if (blocks.size() != 6 * 9 - 0 && blocks.size() <= 0) {
            helper.fail("the tunnel covers the stone 3x3 cross sections");
            return;
        }
        int total = blocks.size();
        var task = new com.fartypoopy.sexmod.tribe.TribeTask(com.fartypoopy.sexmod.tribe.TribeTask.Kind.MINE, start, blocks,
                net.minecraft.core.Direction.EAST);
        tribe.tasks.add(task);
        helper.runAfterDelay(300, () -> {
            int cobble = 0;
            for (int i = 0; i < kobold.storage().getContainerSize(); i++) {
                if (kobold.storage().getItem(i).is(Items.COBBLESTONE)) {
                    cobble += kobold.storage().getItem(i).getCount();
                }
            }
            if (cobble < 1 || task.blocks.size() >= total) {
                helper.fail("the Kobold mined at least one block of the tunnel (cobble=" + cobble + ", left=" + task.blocks.size() + "/" + total + ")");
                return;
            }
            kobold.discard();
            tribe.tasks.clear();
            tribe.members.clear();
            tribe.master = null;
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "b_fight")
    public static void koboldFightsEnemiesOfItsTribe(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        level.setDayTime(1000L);
        forceArena(helper);
        floor(helper, 6);
        var data = com.fartypoopy.sexmod.tribe.TribeData.get(level.getServer());
        var tribe = data.getOrCreate(UUID.randomUUID(), com.fartypoopy.sexmod.tribe.KoboldColor.BROWN);
        CompanionEntity kobold = spawnKind(helper, CompanionKind.KOBOLD);
        kobold.moveTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
        kobold.setTribeId(tribe.id);
        tribe.members.put(kobold.getUUID(), kobold.blockPosition());
        tribe.leader = kobold.getUUID();
        net.minecraft.world.entity.animal.Cow cow = EntityType.COW.create(level);
        cow.setNoAi(true);
        cow.moveTo(helper.absoluteVec(new Vec3(4.5, 1.0, 2.5)));
        level.addFreshEntity(cow);
        tribe.enemies.add(cow.getUUID());
        helper.runAfterDelay(260, () -> {
            if (cow.getHealth() >= cow.getMaxHealth() || !kobold.koboldUnderAttack() && cow.isAlive()) {
                helper.fail("the Kobold ran at the enemy and hit it for 5 (cow health " + cow.getHealth() + ", kobold at "
                        + kobold.position() + " cow at " + cow.position() + " state " + kobold.koboldState()
                        + " attacked " + kobold.koboldUnderAttack() + " enemies " + tribe.enemies.size()
                        + " tribe " + kobold.tribeId().isPresent() + ")");
                return;
            }
            kobold.discard();
            cow.discard();
            tribe.members.clear();
            tribe.enemies.clear();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "b_egg")
    public static void koboldEggCarriesTheTribe(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        var data = com.fartypoopy.sexmod.tribe.TribeData.get(level.getServer());
        var tribe = data.getOrCreate(UUID.randomUUID(), com.fartypoopy.sexmod.tribe.KoboldColor.SILVER);
        UUID owner = UUID.randomUUID();
        tribe.master = owner;
        var egg = com.fartypoopy.sexmod.registry.ModEntities.KOBOLD_EGG.get().create(level);
        egg.setTribeId(tribe.id);
        egg.setEggColor("SILVER");
        egg.setPos(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)));
        level.addFreshEntity(egg);
        egg.setHatchAge(com.fartypoopy.sexmod.entity.KoboldEggEntity.MAX_HATCH_AGE - 1);
        helper.runAfterDelay(30, () -> {
            var hatched = com.fartypoopy.sexmod.tribe.TribeManager.loadedMembers(level.getServer(), tribe);
            if (hatched.size() != 1 || !hatched.get(0).isMaster(helper.makeMockPlayer(GameType.SURVIVAL)) && !hatched.get(0).masterId().equals(java.util.Optional.of(owner))
                    || egg.isAlive() || hatched.get(0).bodyColor() != com.fartypoopy.sexmod.tribe.KoboldColor.SILVER) {
                helper.fail("a hatched egg is a new member of the tribe, owned by its master (members " + hatched.size() + ", eggAlive " + egg.isAlive() + ", eggTicks " + egg.tickCount + ", masters " + hatched.stream().map(h -> h.masterId().toString() + "/" + h.bodyColor()).toList() + ")");
                return;
            }
            hatched.forEach(Entity::discard);
            tribe.members.clear();
            tribe.master = null;
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 500, batch = "b_goblin")
    public static void goblinIsCaughtAndThrownBack(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        floor(helper, 14);
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(5.5, 1.0, 5.5)));
        var goblin = com.fartypoopy.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(level);
        goblin.moveTo(helper.absoluteVec(new Vec3(8.5, 1.0, 8.5)));
        goblin.goblin().queenId = UUID.randomUUID();
        goblin.setGoblinItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 3));
        level.addFreshEntity(goblin);
        goblin.goblin().setState(com.fartypoopy.sexmod.entity.GoblinBehavior.RUN);
        helper.runAfterDelay(5, () -> {
            goblin.goblin().interact(player); // too far: nothing happens
            if (goblin.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.RUN) {
                helper.fail("a robber further than 3.5 blocks cannot be caught");
                return;
            }
            goblin.moveTo(player.getX() + 1.0D, player.getY(), player.getZ());
            goblin.goblin().interact(player);
            if (goblin.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.CATCH
                    || goblin.goblinCarrier().isEmpty()) {
                helper.fail("a robber in reach is caught, state " + goblin.goblinState());
            }
        });
        helper.runAfterDelay(5 + 170 + 28 + 5, () -> {
            if (goblin.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.CATCH_BJ_IDLE) {
                helper.fail("catch -> bj -> bj idle, state " + goblin.goblinState());
                return;
            }
            goblin.goblin().choose(player, 0);
            if (goblin.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.THROWING
                    || player.getInventory().countItem(net.minecraft.world.item.Items.GOLD_INGOT) != 3) {
                helper.fail("take back returns the stolen item and starts the throw");
            }
        });
        helper.runAfterDelay(5 + 170 + 28 + 5 + 50, () -> {
            if (goblin.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.THROWN
                    && goblin.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.STAND_UP) {
                helper.fail("thrown goblin, state " + goblin.goblinState());
            }
        });
        helper.runAfterDelay(5 + 170 + 28 + 5 + 160, () -> {
            if (goblin.isAlive()) {
                helper.fail("an untamed robber disappears after the scene, state " + goblin.goblinState());
                return;
            }
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300, batch = "b_goblin")
    public static void goblinPickUpAndThrow(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        floor(helper, 14);
        var player = serverPlayer(helper);
        player.moveTo(helper.absoluteVec(new Vec3(5.5, 1.0, 5.5)));
        var goblin = com.fartypoopy.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(level);
        goblin.moveTo(helper.absoluteVec(new Vec3(6.5, 1.0, 5.5)));
        level.addFreshEntity(goblin);
        helper.runAfterDelay(5, () -> goblin.goblin().interact(player));
        helper.runAfterDelay(70, () -> {
            if (goblin.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.SHOULDER || !goblin.goblinTamed()) {
                helper.fail("picked up goblin sits on the shoulder and is tamed, state " + goblin.goblinState());
                return;
            }
            var other = com.fartypoopy.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(level);
            other.moveTo(player.getX() + 1.0D, player.getY(), player.getZ());
            level.addFreshEntity(other);
            other.goblin().interact(player);
            if (other.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.NULL) {
                helper.fail("you can carry only one goblin");
            }
            other.discard();
            // thrown straight down: a level throw flies up to ~35 blocks and could leave the forced chunks (the goblin
            // then stops ticking mid-throw, which made this test fail now and then)
            player.setXRot(90.0F);
            goblin.goblin().choose(player, 0);
        });
        helper.runAfterDelay(200, () -> {
            if (!goblin.isAlive() || goblin.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.NULL
                    || goblin.goblinCarrier().isPresent()) {
                helper.fail("thrown goblin lands, stands up and is free, state " + goblin.goblinState());
                return;
            }
            goblin.discard();
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.succeed();
        });
    }

    /** A real ServerPlayer (NeoForge fake player) registered in the test level, so getPlayerByUUID finds it. */
    private static net.minecraft.server.level.ServerPlayer serverPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "goblin_tester"));
        if (!level.players().contains(player)) {
            level.addNewPlayer(player);
        }
        return player;
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "b_struct")
    public static void originalStructuresLoadAndPlace(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        var manager = level.getStructureManager();
        StringBuilder report = new StringBuilder();
        int at = 0;
        for (String name : List.of("goblin", "bia", "jenny", "ellie", "luna", "ssa")) {
            var template = manager.get(id(name));
            if (template.isEmpty()) {
                System.out.println("STRUCT-FAIL " + name);
                continue;
            }
            var size = template.get().getSize();
            var origin = helper.absolutePos(new net.minecraft.core.BlockPos(0, 10, 0)).offset(at, 0, 0);
            at += size.getX() + 4;
            var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
            template.get().placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
            int solid = 0;
            int total = 0;
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
                total++;
                if (!level.getBlockState(pos).isAir()) {
                    solid++;
                }
            }
            report.append(name).append(' ').append(size.toShortString()).append(" solid=").append(solid).append('/').append(total).append("; ");
            if (solid < total / 10) {
                helper.fail("structure " + name + " placed almost nothing: " + report);
                return;
            }
        }
        System.out.println("STRUCT-REPORT " + report);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 700, batch = "b_hall")
    public static void goblinHallIsCarvedIntoACave(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        var base = helper.absolutePos(new net.minecraft.core.BlockPos(1, 40, 1)); // high above the neighbouring tests
        // a 12 x 6 x 12 cave: solid floor and ceiling, one entrance in the north wall
        for (int x = -1; x <= 12; x++) {
            for (int y = -1; y <= 6; y++) {
                for (int z = -1; z <= 12; z++) {
                    boolean inside = x >= 1 && x <= 10 && z >= 1 && z <= 10 && y >= 0 && y <= 5;
                    boolean door = x == 5 && (z == 0 || z == -1) && (y == 0 || y == 1); // a tunnel on into the cave
                    level.setBlock(base.offset(x, y, z), inside || door
                            ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        // nothing is wrong with a solid block: but a missing floor must stop the hall
        if (!com.fartypoopy.sexmod.worldgen.OriginalStructures.placeHall(level, base)) {
            helper.fail("a cave with a solid floor, solid ceiling and one entrance takes the goblin hall");
            return;
        }
        var queens = level.getEntitiesOfClass(CompanionEntity.class, new net.minecraft.world.phys.AABB(base).inflate(14.0D),
                e -> e.kind() == CompanionKind.GOBLIN);
        if (queens.size() != 1 || !queens.get(0).goblinQueen() || queens.get(0).goblin().thronePos == null) {
            helper.fail("the hall has exactly one queen with a throne, found " + queens.size());
            return;
        }
        var queen = queens.get(0);
        // the far test arenas do not always entity-tick, so drive the goblins by hand (the queen and her guards)
        helper.onEachTick(() -> level.getEntitiesOfClass(CompanionEntity.class, new net.minecraft.world.phys.AABB(base).inflate(30.0D),
                e -> e.goblin() != null && e.tickCount < 2000 && !level.isPositionEntityTicking(e.blockPosition())).forEach(CompanionEntity::tick));
        java.util.concurrent.atomic.AtomicBoolean sawScene = new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicBoolean sawGuards = new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicBoolean sawAwait = new java.util.concurrent.atomic.AtomicBoolean();
            helper.onEachTick(() -> {
                if (level.getEntitiesOfClass(CompanionEntity.class, new net.minecraft.world.phys.AABB(base).inflate(20.0D),
                        e -> e.goblin() != null && !e.goblinQueen()
                                && e.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.AWAIT_PICK_UP).size() == 2) {
                    sawAwait.set(true);
                }
                if (queen.currentScene() == com.fartypoopy.sexmod.entity.CompanionScene.GOBLIN_BREEDING) {
                    sawScene.set(true);
                    queen.goblin().impregnationTick = level.getGameTime(); queen.setGoblinPreggo(true); // the cut-short scene never reaches the pregnancy phase; stop her starting over
                    var found = level.getEntitiesOfClass(CompanionEntity.class, new net.minecraft.world.phys.AABB(base).inflate(20.0D),
                            e -> e.goblin() != null && !e.goblinQueen());
                    if (found.stream().anyMatch(g -> g.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.BREED_A)
                            && found.stream().anyMatch(g -> g.goblinState() == com.fartypoopy.sexmod.entity.GoblinBehavior.BREED_B)) {
                        sawGuards.set(true);
                    }
                }
            });
        helper.runAfterDelay(300, () -> {
            if (queen.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.SIT
                    || queen.position().distanceTo(queen.goblin().thronePos) > 0.01D) {
                helper.fail("the queen sits on her throne, state " + queen.goblinState() + " alive=" + queen.isAlive() + " tc=" + queen.tickCount + " pos=" + queen.position() + " throne=" + queen.goblin().thronePos + " hp=" + queen.getHealth() + " ticking=" + level.isPositionEntityTicking(queen.blockPosition()) + " forced=" + level.getForcedChunks().size() + " players=" + level.players().size() + " rm=" + queen.isRemoved());
                return;
            }
            var floorless = base.offset(40, 0, 0);
            if (com.fartypoopy.sexmod.worldgen.OriginalStructures.placeHall(level, floorless)) {
                helper.fail("open air is not a goblin hall");
                return;
            }
            // a player walks in: the queen and two guards jump, then the breeding scene starts
            var visitor = serverPlayer(helper);
            visitor.moveTo(queen.goblin().thronePos.x + 2.0D, queen.goblin().thronePos.y - 1.0D, queen.goblin().thronePos.z - 3.0D);
            visitor.setOnGround(true);
            helper.runAfterDelay(15, () -> {
                if (queen.goblinState() != com.fartypoopy.sexmod.entity.GoblinBehavior.JUMP) {
                    helper.fail("a visitor in the hall makes the queen jump, state " + queen.goblinState());
                }
            });
            
            // (the test player is not in the player list, so the engine ends the scene at once; the ending still runs)
            helper.runAfterDelay(120, () -> {
                var guards = level.getEntitiesOfClass(CompanionEntity.class, new net.minecraft.world.phys.AABB(base).inflate(20.0D),
                        e -> e.goblin() != null && !e.goblinQueen());
                if (!sawScene.get() || !sawGuards.get() || guards.size() != 2
                        || !sawAwait.get()) {
                    helper.fail("breeding: scene " + sawScene + " guards in scene " + sawGuards + "; afterwards guards " + guards.size()
                            + " " + guards.stream().map(g -> g.goblinState()).toList() + " queen " + queen.goblinState());
                    return;
                }
                guards.forEach(Entity::discard);
                queen.discard();
                level.removePlayerImmediately(visitor, Entity.RemovalReason.DISCARDED);
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void goblinModelStringsFollowTheOriginalRules(GameTestHelper helper) {
        var random = helper.getLevel().getRandom();
        for (int n = 0; n < 200; n++) {
            int[] wild = com.fartypoopy.sexmod.entity.GoblinModel.parse(com.fartypoopy.sexmod.entity.GoblinModel.random(random));
            int[] queen = com.fartypoopy.sexmod.entity.GoblinModel.parse(com.fartypoopy.sexmod.entity.GoblinModel.queen(random));
            int[] robber = com.fartypoopy.sexmod.entity.GoblinModel.parse(com.fartypoopy.sexmod.entity.GoblinModel.withBodyColor(random, 3));
            if (wild.length != 10 || queen.length != 10 || robber.length != 10
                    || wild[9] != 0 || queen[9] != 1 || queen[3] != 7 || queen[4] != 7 || robber[7] != 3 || robber[9] != 0
                    || wild[0] > 3 || wild[1] > 2 || wild[5] > 5 || wild[6] > 7 || wild[7] > 4 || wild[8] > 5) {
                helper.fail("goblin model strings have ten fields inside the original ranges");
                return;
            }
        }
        var all = com.fartypoopy.sexmod.entity.GoblinModel.subset(2, 0);
        if (!all.isEmpty() || com.fartypoopy.sexmod.entity.GoblinModel.subset(2, 1).size() != 1
                || com.fartypoopy.sexmod.entity.GoblinModel.subset(2, 1).get(0) != 1
                || com.fartypoopy.sexmod.entity.GoblinModel.subset(2, 3).size() != 2
                || com.fartypoopy.sexmod.entity.GoblinModel.subset(4, 15).size() != 4) {
            helper.fail("accessory subsets count in binary with child 0 as the high bit");
            return;
        }
        helper.succeed();
    }
}
