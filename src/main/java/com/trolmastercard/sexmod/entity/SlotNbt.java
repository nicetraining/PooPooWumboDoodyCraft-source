package com.trolmastercard.sexmod.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Slot-preserving container serialization. Vanilla {@code SimpleContainer.createTag} drops empty
 * slots, so a helmet in slot 2 would reload into slot 0 or 1. Old saves in that compact
 * format are still read.
 */
final class SlotNbt {
    private SlotNbt() {
    }

    static ListTag save(Container container, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte) slot);
                list.add(stack.save(registries, entry));
            }
        }
        return list;
    }

    static void load(SimpleContainer container, ListTag list, HolderLookup.Provider registries) {
        boolean slotted = true;
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag entry) || !entry.contains("Slot")) {
                slotted = false;
                break;
            }
        }
        if (!slotted) {
            container.fromTag(list, registries);
            return;
        }
        container.clearContent();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            int slot = entry.getByte("Slot") & 255;
            if (slot < container.getContainerSize()) {
                ItemStack.parse(registries, entry).ifPresent(stack -> container.setItem(slot, stack));
            }
        }
    }
}
