package com.trolmastercard.sexmod.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.registry.ModPotions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Item costs for scene actions offered in the companion action panel (both sides read the same jar resource). */
public final class CompanionActions {
    private static final Map<CompanionScene, ItemStack> COSTS = load();
    private static final Map<CompanionKind, Menu> MENUS = loadMenus();

    /** One kind's right-click panel: whether the follow/home/equipment column shows, and its scene actions. */
    public record Menu(boolean fixedButtons, List<CompanionScene> actions) {
    }

    /** Scene actions this player is offered; the Kobold's third action is only for its owner. */
    public static List<CompanionScene> actionsFor(CompanionEntity companion, Player player) {
        Menu menu = MENUS.get(companion.kind());
        List<CompanionScene> all = menu == null ? CompanionScene.forKind(companion.kind()) : menu.actions();
        List<CompanionScene> result = new ArrayList<>();
        for (CompanionScene scene : all) {
            if (scene == CompanionScene.KOBOLD_MATING_PRESS && !companion.isMaster(player)) {
                continue;
            }
            result.add(scene);
        }
        return result;
    }

    public static boolean showsFixedButtons(CompanionKind kind) {
        Menu menu = MENUS.get(kind);
        return menu == null || menu.fixedButtons();
    }

    private CompanionActions() {
    }

    /** The listed price of a scene, ignoring who is asking. */
    public static ItemStack costOf(CompanionScene scene) {
        return COSTS.getOrDefault(scene, ItemStack.EMPTY).copy();
    }

    /** What this player actually has to pay: Kobold prices are waived for its owner and while it is under the horny effect. */
    public static ItemStack costFor(CompanionEntity companion, Player player, CompanionScene scene) {
        if (scene.kind() == CompanionKind.KOBOLD
                && (companion.isMaster(player) || companion.hasEffect(ModPotions.HORNY_EFFECT))) {
            return ItemStack.EMPTY;
        }
        return costOf(scene);
    }

    /** Like the original, one single stack must hold the whole price; creative players pay nothing. */
    public static boolean canAfford(Player player, CompanionEntity companion, CompanionScene scene) {
        ItemStack cost = costFor(companion, player, scene);
        return cost.isEmpty() || player.getAbilities().instabuild || findStack(player, cost) != null;
    }

    /** Removes the price from the first stack that can cover it; call only after {@link #canAfford}. */
    public static void pay(Player player, CompanionEntity companion, CompanionScene scene) {
        ItemStack cost = costFor(companion, player, scene);
        if (cost.isEmpty() || player.getAbilities().instabuild) {
            return;
        }
        ItemStack stack = findStack(player, cost);
        if (stack != null) {
            stack.shrink(cost.getCount());
        }
    }

    private static ItemStack findStack(Player player, ItemStack cost) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(cost.getItem()) && stack.getCount() >= cost.getCount()) {
                return stack;
            }
        }
        return null;
    }

    private static Map<CompanionScene, ItemStack> load() {
        Map<CompanionScene, ItemStack> costs = new EnumMap<>(CompanionScene.class);
        try (InputStream stream = CompanionActions.class.getResourceAsStream("/data/sexmod/companion_action_costs.json")) {
            if (stream == null) {
                return costs;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                CompanionScene scene = CompanionScene.byName(entry.getKey());
                if (scene == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject cost = entry.getValue().getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(cost.get("item").getAsString()));
                costs.put(scene, new ItemStack(item, Math.max(1, cost.get("count").getAsInt())));
            }
        } catch (Exception exception) {
            FapCraft.LOGGER.error("Could not read companion action costs", exception);
        }
        return costs;
    }

    private static Map<CompanionKind, Menu> loadMenus() {
        Map<CompanionKind, Menu> menus = new EnumMap<>(CompanionKind.class);
        try (InputStream stream = CompanionActions.class.getResourceAsStream("/data/sexmod/companion_menus.json")) {
            if (stream == null) {
                return menus;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                CompanionKind kind = CompanionKind.byName(entry.getKey().toLowerCase(java.util.Locale.ROOT));
                JsonObject menu = entry.getValue().getAsJsonObject();
                List<CompanionScene> actions = new ArrayList<>();
                for (JsonElement name : menu.getAsJsonArray("actions")) {
                    CompanionScene scene = CompanionScene.byName(name.getAsString());
                    if (scene != null && scene.kind() == kind) {
                        actions.add(scene);
                    }
                }
                menus.put(kind, new Menu(menu.get("fixed").getAsBoolean(), List.copyOf(actions)));
            }
        } catch (Exception exception) {
            FapCraft.LOGGER.error("Could not read companion menus", exception);
        }
        return menus;
    }
}
