package com.trolmastercard.sexmod.menu;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import com.trolmastercard.sexmod.entity.CompanionMode;
import com.trolmastercard.sexmod.entity.CompanionScene;
import com.trolmastercard.sexmod.entity.OutfitMode;
import com.trolmastercard.sexmod.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class CompanionInventoryMenu extends AbstractContainerMenu {
    public static final int BUTTON_SCENE_PREVIOUS = 0;
    public static final int BUTTON_SCENE_NEXT = 1;
    public static final int BUTTON_SCENE_TOGGLE = 2;
    public static final int BUTTON_SCENE_SLOWER = 3;
    public static final int BUTTON_SCENE_FASTER = 4;
    public static final int BUTTON_SCENE_FINISH = 5;
    public static final int BUTTON_OUTFIT = 6;
    public static final int BUTTON_VARIANT = 7;
    public static final int BUTTON_SCALE = 8;
    public static final int BUTTON_MODE = 9;
    public static final int BUTTON_HOME = 10;
    public static final int BUTTON_STORAGE = 11;

    /** Container size held by every companion; menus with no rod slot simply do not show slot 6. */
    private static final int CONTAINER_SIZE = 7;
    private static final int[] SIX_SLOT_X = {31, 50, 72, 91, 110, 129};
    private static final int[] ROD_SLOT_X = {41, 59, 81, 100, 119, 138, 22};

    private final boolean rodSlot;
    private final int companionSlotCount;

    private final int companionEntityId;
    private final Player viewingPlayer;
    private final DataSlot selectedSceneIndex = DataSlot.standalone();

    public CompanionInventoryMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, readCompanion(playerInventory, data));
    }

    public CompanionInventoryMenu(int containerId, Inventory playerInventory, CompanionEntity companion) {
        this(containerId, playerInventory, new OpenedCompanion(companion.getId(), companion));
    }

    private CompanionInventoryMenu(
            int containerId,
            Inventory playerInventory,
            OpenedCompanion openedCompanion
    ) {
        super(ModMenus.COMPANION_INVENTORY.get(), containerId);
        this.companionEntityId = openedCompanion.entityId();
        this.viewingPlayer = playerInventory.player;
        this.addDataSlot(this.selectedSceneIndex);
        Container equipment = openedCompanion.entity() == null
                ? new SimpleContainer(CONTAINER_SIZE)
                : openedCompanion.entity().equipment();
        checkContainerSize(equipment, CONTAINER_SIZE);

        this.rodSlot = openedCompanion.entity() != null && openedCompanion.entity().kind().hasFishingRodSlot();
        this.companionSlotCount = this.rodSlot ? 7 : 6;
        int[] equipmentX = this.rodSlot ? ROD_SLOT_X : SIX_SLOT_X;
        for (int slot = 0; slot < this.companionSlotCount; slot++) {
            SlotRole role = SlotRole.values()[slot];
            this.addSlot(new Slot(equipment, slot, equipmentX[slot], 60) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return role.accepts(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    /** True for the 7-slot layout that includes the fishing-rod slot. */
    public boolean hasRodSlot() {
        return this.rodSlot;
    }

    /** The equipment slots of the original companion menus, each with its own item rule. */
    private enum SlotRole {
        WEAPON, BOW, HELMET, CHEST_PLATE, PANTS, SHOES, ROD;

        boolean accepts(ItemStack stack) {
            return switch (this) {
                case WEAPON -> stack.getItem() instanceof net.minecraft.world.item.SwordItem
                        || stack.getItem() instanceof net.minecraft.world.item.DiggerItem;
                case BOW -> stack.getItem() instanceof net.minecraft.world.item.BowItem;
                case HELMET -> armor(stack, EquipmentSlot.HEAD);
                case CHEST_PLATE -> armor(stack, EquipmentSlot.CHEST);
                case PANTS -> armor(stack, EquipmentSlot.LEGS);
                case SHOES -> armor(stack, EquipmentSlot.FEET);
                case ROD -> stack.getItem() instanceof net.minecraft.world.item.FishingRodItem;
            };
        }

        private static boolean armor(ItemStack stack, EquipmentSlot slot) {
            return stack.getItem() instanceof net.minecraft.world.item.ArmorItem item
                    && item.getEquipmentSlot() == slot;
        }
    }

    public CompanionEntity companion() {
        Entity entity = this.viewingPlayer.level().getEntity(this.companionEntityId);
        return entity instanceof CompanionEntity companion ? companion : null;
    }

    public CompanionScene selectedScene() {
        CompanionEntity companion = this.companion();
        if (companion == null) {
            return null;
        }
        List<CompanionScene> scenes = CompanionScene.forKind(companion.kind());
        return scenes.isEmpty()
                ? null
                : scenes.get(Math.floorMod(this.selectedSceneIndex.get(), scenes.size()));
    }

    @Override
    public boolean stillValid(Player player) {
        CompanionEntity companion = this.companion();
        return companion != null
                && companion.isAlive()
                && player.distanceToSqr(companion) <= 64.0D
                && companion.mayBeCommandedBy(player);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        CompanionEntity companion = this.companion();
        if (companion == null || !this.stillValid(player)) {
            return false;
        }

        List<CompanionScene> scenes = CompanionScene.forKind(companion.kind());
        switch (buttonId) {
            case BUTTON_SCENE_PREVIOUS -> this.moveSceneSelection(-1, scenes.size());
            case BUTTON_SCENE_NEXT -> this.moveSceneSelection(1, scenes.size());
            case BUTTON_SCENE_TOGGLE -> {
                if (companion.isInScene()) {
                    if (companion.canControlScene(player)) {
                        companion.stopScene();
                    }
                } else {
                    CompanionScene selected = this.selectedScene();
                    if (selected != null) {
                        companion.startScene(player, selected);
                    }
                }
            }
            case BUTTON_SCENE_SLOWER -> companion.changeSceneIntensity(player, -1);
            case BUTTON_SCENE_FASTER -> companion.changeSceneIntensity(player, 1);
            case BUTTON_SCENE_FINISH -> companion.requestSceneFinish(player);
            case BUTTON_OUTFIT -> companion.setOutfit(nextSupportedOutfit(companion));
            case BUTTON_VARIANT -> companion.setAppearanceVariant(companion.appearanceVariant() + 1);
            case BUTTON_SCALE -> companion.setAppearanceScale(
                    companion.appearanceScale() >= 1.24F
                            ? 0.75F
                            : companion.appearanceScale() + 0.25F
            );
            case BUTTON_MODE -> companion.setMode(companion.mode().next());
            case BUTTON_STORAGE -> {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    companion.openStorageMenu(serverPlayer);
                }
            }
            case BUTTON_HOME -> {
                companion.setHome(companion.blockPosition());
                companion.setMode(CompanionMode.HOME);
                player.displayClientMessage(Component.translatable("message.sexmod.home_set"), true);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void moveSceneSelection(int amount, int sceneCount) {
        if (sceneCount > 0) {
            this.selectedSceneIndex.set(Math.floorMod(this.selectedSceneIndex.get() + amount, sceneCount));
        }
    }

    private static OutfitMode nextSupportedOutfit(CompanionEntity companion) {
        OutfitMode candidate = companion.outfit();
        do {
            candidate = candidate.next();
        } while (!companion.kind().supportsOutfit(candidate));
        return candidate;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved;
        if (index < this.companionSlotCount) {
            moved = this.moveItemStackTo(stack, this.companionSlotCount, this.companionSlotCount + 36, true);
        } else {
            moved = this.moveItemStackTo(stack, 0, this.companionSlotCount, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return original;
    }

    private static OpenedCompanion readCompanion(Inventory playerInventory, RegistryFriendlyByteBuf data) {
        int entityId = data.readVarInt();
        Entity entity = playerInventory.player.level().getEntity(entityId);
        return new OpenedCompanion(entityId, entity instanceof CompanionEntity companion ? companion : null);
    }

    private record OpenedCompanion(int entityId, CompanionEntity entity) {
    }
}
