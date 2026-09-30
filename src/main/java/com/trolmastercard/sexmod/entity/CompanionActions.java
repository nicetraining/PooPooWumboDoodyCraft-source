package com.trolmastercard.sexmod.entity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.trolmastercard.sexmod.FapCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** Item costs for scene actions offered in the companion action panel (both sides read the same jar resource). */
public final class CompanionActions {
    private static final Map<CompanionScene, ItemStack> COSTS = load();

    private CompanionActions() {
    }

    /** The stack a player must hand over to start the scene, or {@link ItemStack#EMPTY} if it is free. */
    public static ItemStack costOf(CompanionScene scene) {
        return COSTS.getOrDefault(scene, ItemStack.EMPTY).copy();
    }

    public static boolean canAfford(Player player, CompanionScene scene) {
        ItemStack cost = costOf(scene);
        return cost.isEmpty() || player.getAbilities().instabuild || count(player, cost.getItem()) >= cost.getCount();
    }

    /** Removes the cost from the player's inventory; call only after {@link #canAfford}. */
    public static void pay(Player player, CompanionScene scene) {
        ItemStack cost = costOf(scene);
        if (cost.isEmpty() || player.getAbilities().instabuild) {
            return;
        }
        int remaining = cost.getCount();
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(cost.getItem())) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
    }

    private static int count(Player player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
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
}
