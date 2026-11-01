package com.fartypoopy.sexmod.menu;

import com.fartypoopy.sexmod.entity.CompanionEntity;
import com.fartypoopy.sexmod.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;


/** Original d4 (six equipment slots) and ca (Luna: the rod slot in front), above the player inventory. */
public final class CompanionInventoryMenu extends AbstractContainerMenu {

    /** Container size held by every companion; menus with no rod slot simply do not show slot 6. */
    private static final int CONTAINER_SIZE = 7;
    private static final int[] SIX_SLOT_X = {31, 50, 72, 91, 110, 129};
    private static final int[] ROD_SLOT_X = {41, 59, 81, 100, 119, 138, 22};

    private final boolean rodSlot;
    private final int companionSlotCount;

    private final int companionEntityId;
    private final Player viewingPlayer;

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

    /** Original d4 / ca: the menu never closes on its own. */
    @Override
    public boolean stillValid(Player player) {
        return this.companion() != null;
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
