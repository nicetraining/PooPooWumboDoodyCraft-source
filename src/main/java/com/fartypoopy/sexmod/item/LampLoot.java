package com.fartypoopy.sexmod.item;

import com.google.common.collect.ImmutableList;
import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.registry.ModItems;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * Original ap.LootTableLoadEvent: the lamp is added with weight 5 to "pool3", else "pool2", of the abandoned mineshaft,
 * desert pyramid, simple dungeon and woodland mansion chests. In 1.12 that was the three-roll junk pool of the
 * mineshaft, dungeon and mansion (none had a pool3), and the desert pyramid had neither pool, so it never got the lamp.
 * 1.21 added armour-trim pools (a pool3 in the mansion, a pool2 in the pyramid), so the 1.12 result is kept instead:
 * "pool2" of the first three, nothing in the pyramid.
 */
@EventBusSubscriber(modid = FapCraft.MOD_ID)
public final class LampLoot {
    private static final Set<String> TABLES = Set.of(
            "minecraft:chests/abandoned_mineshaft",
            "minecraft:chests/simple_dungeon",
            "minecraft:chests/woodland_mansion");

    private LampLoot() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!TABLES.contains(event.getName().toString())) {
            return;
        }
        LootPool pool = event.getTable().getPool("pool2");
        if (pool == null) {
            return;
        }
        try {
            Field field = LootPool.class.getDeclaredField("entries");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<LootPoolEntryContainer> entries = (List<LootPoolEntryContainer>) field.get(pool);
            field.set(pool, ImmutableList.<LootPoolEntryContainer>builder()
                    .addAll(entries)
                    .add(LootItem.lootTableItem(ModItems.ALLIES_LAMP.get()).setWeight(5).build())
                    .build());
        } catch (ReflectiveOperationException exception) {
            FapCraft.LOGGER.error("could not add the Allies Lamp to {}", event.getName(), exception);
        }
    }
}
