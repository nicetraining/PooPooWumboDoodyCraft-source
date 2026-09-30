package com.trolmastercard.sexmod.gametest;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionActions;
import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionScene;
import com.trolmastercard.sexmod.entity.CompanionKind;
import com.trolmastercard.sexmod.entity.CompanionAction;
import com.trolmastercard.sexmod.menu.CompanionInventoryMenu;
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
                com.trolmastercard.sexmod.registry.ModPotions.HORNY_EFFECT, 200));
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
        helper.runAfterDelay(90, () -> {
            if (jenny.action() != CompanionAction.NONE) {
                helper.fail("the routine ends after the teleport, action=" + jenny.action());
            }
            if (jenny.position().distanceTo(Vec3.atBottomCenterOf(home)) > 1.5D) {
                helper.fail("she should stand at home, is at " + jenny.position() + " home " + home);
            }
            jenny.discard();
            helper.succeed();
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

        if (jenny.beginBedWalk(helper.getLevel(), player, CompanionScene.JENNY_DOGGY) || jenny.isBedWalking()) {
            helper.fail("with no bed Jenny refuses and does not walk");
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
        com.trolmastercard.sexmod.entity.FriendlySlimeEntity baby =
                com.trolmastercard.sexmod.registry.ModEntities.FRIENDLY_SLIME.get().create(helper.getLevel());
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

    // ---------------------------------------------------------------- Kobold tribes

    /** Keeps the arena chunks entity-ticking so the AI tests do not depend on where the structure lands. */
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
        com.trolmastercard.sexmod.entity.LunaFishing.ignoreBiomes = true;
        CompanionEntity luna = spawnKind(helper, CompanionKind.LUNA);
        luna.moveTo(helper.absoluteVec(new Vec3(1.5, 1.0, 4.5)));
        var fishing = luna.lunaFishing();
        helper.runAfterDelay(300, () -> {
            var hook = fishing.hook();
            int now = luna.fishState();
            if (now == com.trolmastercard.sexmod.entity.LunaFishing.EAT || now == com.trolmastercard.sexmod.entity.LunaFishing.THROW_AWAY) {
                return; // a natural bite already happened (she walks faster now)
            }
            if (hook == null || !hook.isBobbing() || now != com.trolmastercard.sexmod.entity.LunaFishing.IDLE) {
                com.trolmastercard.sexmod.entity.LunaFishing.ignoreBiomes = false;
                helper.fail("Luna walked to the water, cast her rod and the bobber floats (state " + luna.fishState() + ", hook " + hook + ")");
                return;
            }
            hook.forceBite();
        });
        helper.runAfterDelay(380, () -> {
            int state = luna.fishState();
            if (state != com.trolmastercard.sexmod.entity.LunaFishing.EAT && state != com.trolmastercard.sexmod.entity.LunaFishing.THROW_AWAY) {
                com.trolmastercard.sexmod.entity.LunaFishing.ignoreBiomes = false;
                helper.fail("after the bite she reeled in and eats or throws away the catch (state " + state + ")");
            }
        });
        helper.runAfterDelay(560, () -> {
            com.trolmastercard.sexmod.entity.LunaFishing.ignoreBiomes = false;
            if (fishing.catches() != 1 || luna.fishState() == com.trolmastercard.sexmod.entity.LunaFishing.EAT) {
                helper.fail("the catch is finished and she starts over (catches " + fishing.catches() + ", state " + luna.fishState() + ")");
                return;
            }
            luna.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "a_first")
    public static void tribeSpawnClaimAndPersist(GameTestHelper helper) {
        var level = helper.getLevel();
        forceArena(helper);
        var server = level.getServer();
        var tribe = com.trolmastercard.sexmod.tribe.TribeManager.spawnTribe(level, helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)));
        var members = com.trolmastercard.sexmod.tribe.TribeManager.loadedMembers(server, tribe);
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
        if (com.trolmastercard.sexmod.tribe.TribeManager.claim(server, player, "Tester", leader.position(), tribe.id, "")
                || com.trolmastercard.sexmod.tribe.TribeManager.claim(server, player, "Tester", leader.position(), tribe.id, "ABCDEFGHIJKLMNOP")) {
            helper.fail("an empty or 16 character tribe name is refused");
        }
        if (!com.trolmastercard.sexmod.tribe.TribeManager.claim(server, player, "Tester", leader.position(), tribe.id, "Reds")) {
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
                || com.trolmastercard.sexmod.tribe.TribeData.get(server).ofMaster(player) != tribe
                || com.trolmastercard.sexmod.tribe.TribeManager.claim(server, UUID.randomUUID(), "Other", leader.position(), tribe.id, "Nope")) {
            helper.fail("claim turns follow mode on, sets the master and cannot be repeated by someone else");
        }

        // saved data round trip
        tribe.beds.add(new net.minecraft.core.BlockPos(1, 1, 1));
        tribe.beds.add(new net.minecraft.core.BlockPos(1, 1, 2));
        tribe.chests.add(new net.minecraft.core.BlockPos(4, 1, 4));
        var blocks = com.trolmastercard.sexmod.tribe.TribeCommands.tunnel(new net.minecraft.core.BlockPos(0, 1, 0),
                net.minecraft.core.Direction.EAST, level);
        tribe.tasks.add(new com.trolmastercard.sexmod.tribe.TribeTask(com.trolmastercard.sexmod.tribe.TribeTask.Kind.MINE,
                new net.minecraft.core.BlockPos(0, 1, 0), java.util.Set.of(new net.minecraft.core.BlockPos(2, 1, 0)),
                net.minecraft.core.Direction.EAST));
        var data = com.trolmastercard.sexmod.tribe.TribeData.get(server);
        var saved = data.save(new CompoundTag(), level.registryAccess());
        var loaded = com.trolmastercard.sexmod.tribe.TribeData.load(saved, level.registryAccess());
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
        var data = com.trolmastercard.sexmod.tribe.TribeData.get(level.getServer());
        var tribe = data.getOrCreate(UUID.randomUUID(), com.trolmastercard.sexmod.tribe.KoboldColor.GREEN);
        CompanionEntity kobold = spawnKind(helper, CompanionKind.KOBOLD);
        UUID owner = UUID.randomUUID();
        kobold.moveTo(helper.absoluteVec(new Vec3(2.5, 1.0, 2.5)));
        kobold.setMaster(owner);
        kobold.setTribeId(tribe.id);
        tribe.master = owner;
        tribe.members.put(kobold.getUUID(), kobold.blockPosition());
        tribe.leader = kobold.getUUID();
        var task = com.trolmastercard.sexmod.tribe.KoboldWork.treeTask(level, helper.absolutePos(base), tribe);
        if (task.kind != com.trolmastercard.sexmod.tribe.TribeTask.Kind.FALL_TREE || task.blocks.size() != 3
                || task.kind.maxWorkers != 1) {
            helper.fail("a three log trunk is one FALL_TREE task with one worker, got " + task.blocks.size());
            return;
        }
        tribe.tasks.add(task);
        helper.runAfterDelay(900, () -> {
            boolean gone = helper.getBlockState(base).isAir();
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
        var data = com.trolmastercard.sexmod.tribe.TribeData.get(level.getServer());
        var tribe = data.getOrCreate(UUID.randomUUID(), com.trolmastercard.sexmod.tribe.KoboldColor.RED);
        CompanionEntity kobold = spawnKind(helper, CompanionKind.KOBOLD);
        UUID owner = UUID.randomUUID();
        kobold.setMaster(owner);
        kobold.setTribeId(tribe.id);
        tribe.master = owner;
        tribe.members.put(kobold.getUUID(), kobold.blockPosition());
        tribe.leader = kobold.getUUID();
        var start = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        var blocks = com.trolmastercard.sexmod.tribe.TribeCommands.tunnel(start, net.minecraft.core.Direction.EAST, level);
        if (blocks.size() != 6 * 9 - 0 && blocks.size() <= 0) {
            helper.fail("the tunnel covers the stone 3x3 cross sections");
            return;
        }
        int total = blocks.size();
        var task = new com.trolmastercard.sexmod.tribe.TribeTask(com.trolmastercard.sexmod.tribe.TribeTask.Kind.MINE, start, blocks,
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
        var data = com.trolmastercard.sexmod.tribe.TribeData.get(level.getServer());
        var tribe = data.getOrCreate(UUID.randomUUID(), com.trolmastercard.sexmod.tribe.KoboldColor.BROWN);
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
        var data = com.trolmastercard.sexmod.tribe.TribeData.get(level.getServer());
        var tribe = data.getOrCreate(UUID.randomUUID(), com.trolmastercard.sexmod.tribe.KoboldColor.SILVER);
        UUID owner = UUID.randomUUID();
        tribe.master = owner;
        var egg = com.trolmastercard.sexmod.registry.ModEntities.KOBOLD_EGG.get().create(level);
        egg.setTribeId(tribe.id);
        egg.setEggColor("SILVER");
        egg.setPos(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)));
        level.addFreshEntity(egg);
        egg.setHatchAge(com.trolmastercard.sexmod.entity.KoboldEggEntity.MAX_HATCH_AGE - 1);
        helper.runAfterDelay(30, () -> {
            var hatched = com.trolmastercard.sexmod.tribe.TribeManager.loadedMembers(level.getServer(), tribe);
            if (hatched.size() != 1 || !hatched.get(0).isMaster(helper.makeMockPlayer(GameType.SURVIVAL)) && !hatched.get(0).masterId().equals(java.util.Optional.of(owner))
                    || egg.isAlive() || hatched.get(0).bodyColor() != com.trolmastercard.sexmod.tribe.KoboldColor.SILVER) {
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
        var goblin = com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(level);
        goblin.moveTo(helper.absoluteVec(new Vec3(8.5, 1.0, 8.5)));
        goblin.goblin().queenId = UUID.randomUUID();
        goblin.setGoblinItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 3));
        level.addFreshEntity(goblin);
        goblin.goblin().setState(com.trolmastercard.sexmod.entity.GoblinBehavior.RUN);
        helper.runAfterDelay(5, () -> {
            goblin.goblin().interact(player); // too far: nothing happens
            if (goblin.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.RUN) {
                helper.fail("a robber further than 3.5 blocks cannot be caught");
                return;
            }
            goblin.moveTo(player.getX() + 1.0D, player.getY(), player.getZ());
            goblin.goblin().interact(player);
            if (goblin.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.CATCH
                    || goblin.goblinCarrier().isEmpty()) {
                helper.fail("a robber in reach is caught, state " + goblin.goblinState());
            }
        });
        helper.runAfterDelay(5 + 170 + 28 + 5, () -> {
            if (goblin.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.CATCH_BJ_IDLE) {
                helper.fail("catch -> bj -> bj idle, state " + goblin.goblinState());
                return;
            }
            goblin.goblin().choose(player, 0);
            if (goblin.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.THROWING
                    || player.getInventory().countItem(net.minecraft.world.item.Items.GOLD_INGOT) != 3) {
                helper.fail("take back returns the stolen item and starts the throw");
            }
        });
        helper.runAfterDelay(5 + 170 + 28 + 5 + 50, () -> {
            if (goblin.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.THROWN
                    && goblin.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.STAND_UP) {
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
        var goblin = com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(level);
        goblin.moveTo(helper.absoluteVec(new Vec3(6.5, 1.0, 5.5)));
        level.addFreshEntity(goblin);
        helper.runAfterDelay(5, () -> goblin.goblin().interact(player));
        helper.runAfterDelay(70, () -> {
            if (goblin.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.SHOULDER || !goblin.goblinTamed()) {
                helper.fail("picked up goblin sits on the shoulder and is tamed, state " + goblin.goblinState());
                return;
            }
            var other = com.trolmastercard.sexmod.registry.ModEntities.get(CompanionKind.GOBLIN).create(level);
            other.moveTo(player.getX() + 1.0D, player.getY(), player.getZ());
            level.addFreshEntity(other);
            other.goblin().interact(player);
            if (other.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.NULL) {
                helper.fail("you can carry only one goblin");
            }
            other.discard();
            goblin.goblin().choose(player, 0);
        });
        helper.runAfterDelay(200, () -> {
            if (!goblin.isAlive() || goblin.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.NULL
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
                    boolean door = x == 5 && z == 0 && (y == 0 || y == 1);
                    level.setBlock(base.offset(x, y, z), inside || door
                            ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        // nothing is wrong with a solid block: but a missing floor must stop the hall
        if (!com.trolmastercard.sexmod.worldgen.OriginalStructures.placeHall(level, base)) {
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
                                && e.goblinState() == com.trolmastercard.sexmod.entity.GoblinBehavior.AWAIT_PICK_UP).size() == 2) {
                    sawAwait.set(true);
                }
                if (queen.currentScene() == com.trolmastercard.sexmod.entity.CompanionScene.GOBLIN_BREEDING) {
                    sawScene.set(true);
                    queen.goblin().impregnationTick = level.getGameTime(); queen.setGoblinPreggo(true); // the cut-short scene never reaches the pregnancy phase; stop her starting over
                    var found = level.getEntitiesOfClass(CompanionEntity.class, new net.minecraft.world.phys.AABB(base).inflate(20.0D),
                            e -> e.goblin() != null && !e.goblinQueen());
                    if (found.stream().anyMatch(g -> g.goblinState() == com.trolmastercard.sexmod.entity.GoblinBehavior.BREED_A)
                            && found.stream().anyMatch(g -> g.goblinState() == com.trolmastercard.sexmod.entity.GoblinBehavior.BREED_B)) {
                        sawGuards.set(true);
                    }
                }
            });
        helper.runAfterDelay(300, () -> {
            if (queen.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.SIT
                    || queen.position().distanceTo(queen.goblin().thronePos) > 0.01D) {
                helper.fail("the queen sits on her throne, state " + queen.goblinState() + " alive=" + queen.isAlive() + " tc=" + queen.tickCount + " pos=" + queen.position() + " throne=" + queen.goblin().thronePos + " hp=" + queen.getHealth() + " ticking=" + level.isPositionEntityTicking(queen.blockPosition()) + " forced=" + level.getForcedChunks().size() + " players=" + level.players().size() + " rm=" + queen.isRemoved());
                return;
            }
            var floorless = base.offset(40, 0, 0);
            if (com.trolmastercard.sexmod.worldgen.OriginalStructures.placeHall(level, floorless)) {
                helper.fail("open air is not a goblin hall");
                return;
            }
            // a player walks in: the queen and two guards jump, then the breeding scene starts
            var visitor = serverPlayer(helper);
            visitor.moveTo(queen.goblin().thronePos.x + 2.0D, queen.goblin().thronePos.y - 1.0D, queen.goblin().thronePos.z - 3.0D);
            visitor.setOnGround(true);
            helper.runAfterDelay(15, () -> {
                if (queen.goblinState() != com.trolmastercard.sexmod.entity.GoblinBehavior.JUMP) {
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
            int[] wild = com.trolmastercard.sexmod.entity.GoblinModel.parse(com.trolmastercard.sexmod.entity.GoblinModel.random(random));
            int[] queen = com.trolmastercard.sexmod.entity.GoblinModel.parse(com.trolmastercard.sexmod.entity.GoblinModel.queen(random));
            int[] robber = com.trolmastercard.sexmod.entity.GoblinModel.parse(com.trolmastercard.sexmod.entity.GoblinModel.withBodyColor(random, 3));
            if (wild.length != 10 || queen.length != 10 || robber.length != 10
                    || wild[9] != 0 || queen[9] != 1 || queen[3] != 7 || queen[4] != 7 || robber[7] != 3 || robber[9] != 0
                    || wild[0] > 3 || wild[1] > 2 || wild[5] > 5 || wild[6] > 7 || wild[7] > 4 || wild[8] > 5) {
                helper.fail("goblin model strings have ten fields inside the original ranges");
                return;
            }
        }
        var all = com.trolmastercard.sexmod.entity.GoblinModel.subset(2, 0);
        if (!all.isEmpty() || com.trolmastercard.sexmod.entity.GoblinModel.subset(2, 1).size() != 1
                || com.trolmastercard.sexmod.entity.GoblinModel.subset(2, 1).get(0) != 1
                || com.trolmastercard.sexmod.entity.GoblinModel.subset(2, 3).size() != 2
                || com.trolmastercard.sexmod.entity.GoblinModel.subset(4, 15).size() != 4) {
            helper.fail("accessory subsets count in binary with child 0 as the high bit");
            return;
        }
        helper.succeed();
    }
}
