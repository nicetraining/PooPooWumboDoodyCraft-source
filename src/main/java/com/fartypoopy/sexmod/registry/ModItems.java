package com.fartypoopy.sexmod.registry;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.entity.CompanionKind;
import com.fartypoopy.sexmod.item.AlliesLampItem;
import com.fartypoopy.sexmod.item.DragonStaffItem;
import com.fartypoopy.sexmod.item.GalathCoinItem;
import com.fartypoopy.sexmod.item.KoboldEggItem;
import com.fartypoopy.sexmod.item.LegacyPurposeItem;
import com.fartypoopy.sexmod.item.LunaRodItem;
import com.fartypoopy.sexmod.item.NpcEditorWandItem;
import com.fartypoopy.sexmod.item.TribeEggItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FapCraft.MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, FapCraft.MOD_ID);

    public static final DeferredItem<AlliesLampItem> ALLIES_LAMP = ITEMS.register(
            "allies_lamp",
            () -> new AlliesLampItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<DragonStaffItem> DRAGON_STAFF = ITEMS.register(
            "dragon_staff",
            () -> new DragonStaffItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<TribeEggItem> TRIBE_EGG = ITEMS.register(
            "tribe_egg",
            () -> new TribeEggItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<GalathCoinItem> GALATH_COIN = ITEMS.register(
            "galath_coin",
            () -> new GalathCoinItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<NpcEditorWandItem> NPC_EDITOR_WAND = ITEMS.register(
            "npc_editor_wand",
            () -> new NpcEditorWandItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<KoboldEggItem> KOBOLD_EGG_ITEM = ITEMS.register(
            "kobold_egg_item",
            () -> new KoboldEggItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredItem<LegacyPurposeItem> WINCHESTER = ITEMS.register(
            "winchester",
            () -> new LegacyPurposeItem(
                    new Item.Properties(),
                    "winchester",
                    0
            )
    );
    public static final DeferredItem<LunaRodItem> LUNA_ROD = ITEMS.register(
            "luna_rod",
            () -> new LunaRodItem(new Item.Properties().durability(64))
    );
    private static final Map<CompanionKind, DeferredItem<DeferredSpawnEggItem>> SPAWN_EGGS =
            new EnumMap<>(CompanionKind.class);

    static {
        for (CompanionKind kind : CompanionKind.values()) {
            if (kind == CompanionKind.ALLIE || kind == CompanionKind.KOBOLD) {
                continue; // original bi: Allie and Kobold are registered without a spawn egg
            }
            SPAWN_EGGS.put(kind, ITEMS.register(
                    kind.spawnEggPath(),
                    () -> new DeferredSpawnEggItem(
                            () -> ModEntities.get(kind),
                            kind.primaryEggColor(),
                            kind.secondaryEggColor(),
                            new Item.Properties()
                    )
            ));
        }
    }

    /** Not in the original (kept at the user's request): one tab with every item and spawn egg of the mod. */
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.CreativeModeTab> FAPCRAFT_TAB =
            CREATIVE_TABS.register("fapcraft", () -> net.minecraft.world.item.CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.sexmod"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> spawnEgg(CompanionKind.JENNY).get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ALLIES_LAMP.get());
                        output.accept(DRAGON_STAFF.get());
                        output.accept(TRIBE_EGG.get());
                        output.accept(GALATH_COIN.get());
                        output.accept(NPC_EDITOR_WAND.get());
                        output.accept(KOBOLD_EGG_ITEM.get());
                        output.accept(LUNA_ROD.get());
                        output.accept(WINCHESTER.get());
                        SPAWN_EGGS.values().forEach(egg -> output.accept(egg.get()));
                    })
                    .build());

    /**
     * The original had no tab of its own: allies_lamp and tribe_egg sat in Misc (1.21: tools and utilities, next to the
     * other usable odds and ends), npc_editor_wand and dragon_staff in Tools, the spawn eggs with the vanilla eggs. The
     * Galath coin, Kobold egg, Luna's rod and the Winchester were in no tab at all.
     */
    private static void fillTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ALLIES_LAMP.get());
            event.accept(TRIBE_EGG.get());
            event.accept(NPC_EDITOR_WAND.get());
            event.accept(DRAGON_STAFF.get());
        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            SPAWN_EGGS.values().forEach(egg -> event.accept(egg.get()));
        }
    }

    private ModItems() {
    }

    public static DeferredItem<DeferredSpawnEggItem> spawnEgg(CompanionKind kind) {
        return SPAWN_EGGS.get(kind);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(ModItems::fillTabs);
    }
}
