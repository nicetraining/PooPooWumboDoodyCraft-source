package com.fartypoopy.sexmod.registry;

import com.fartypoopy.sexmod.FapCraft;
import com.fartypoopy.sexmod.menu.CompanionInventoryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, FapCraft.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<CompanionInventoryMenu>> COMPANION_INVENTORY =
            MENUS.register("companion_inventory", () -> IMenuTypeExtension.create(CompanionInventoryMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ChestMenu>> COMPANION_STORAGE =
            MENUS.register("companion_storage", () -> IMenuTypeExtension.create(
                    (containerId, inventory, data) -> new ChestMenu(
                            ModMenus.COMPANION_STORAGE.get(), containerId, inventory, new SimpleContainer(27), 3)));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
