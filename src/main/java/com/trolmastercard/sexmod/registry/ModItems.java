package com.trolmastercard.sexmod.registry;

import com.trolmastercard.sexmod.FapCraft;
import com.trolmastercard.sexmod.entity.CompanionKind;
import com.trolmastercard.sexmod.item.AlliesLampItem;
import com.trolmastercard.sexmod.item.DragonStaffItem;
import com.trolmastercard.sexmod.item.GalathCoinItem;
import com.trolmastercard.sexmod.item.KoboldEggItem;
import com.trolmastercard.sexmod.item.LegacyPurposeItem;
import com.trolmastercard.sexmod.item.LunaRodItem;
import com.trolmastercard.sexmod.item.NpcEditorWandItem;
import com.trolmastercard.sexmod.item.TribeEggItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FapCraft.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FapCraft.MOD_ID);

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
                    new Item.Properties().stacksTo(1),
                    "winchester",
                    2
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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FAPCRAFT_TAB = CREATIVE_TABS.register(
            "fapcraft",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sexmod"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> spawnEgg(CompanionKind.JENNY).get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ALLIES_LAMP.get());
                        output.accept(DRAGON_STAFF.get());
                        output.accept(TRIBE_EGG.get());
                        output.accept(GALATH_COIN.get());
                        output.accept(NPC_EDITOR_WAND.get());
                        output.accept(KOBOLD_EGG_ITEM.get());
                        output.accept(WINCHESTER.get());
                        output.accept(LUNA_ROD.get());
                        SPAWN_EGGS.values().forEach(egg -> output.accept(egg.get()));
                    })
                    .build()
    );

    private ModItems() {
    }

    public static DeferredItem<DeferredSpawnEggItem> spawnEgg(CompanionKind kind) {
        return SPAWN_EGGS.get(kind);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
