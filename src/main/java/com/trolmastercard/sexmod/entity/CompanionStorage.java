package com.trolmastercard.sexmod.entity;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;

/** The 27-slot chest carried by storage companions; closes if the companion is gone or too far. */
public final class CompanionStorage extends SimpleContainer {
    public static final int SIZE = 27;

    private final CompanionEntity owner;

    public CompanionStorage(CompanionEntity owner) {
        super(SIZE);
        this.owner = owner;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.owner.isAlive()
                && !this.owner.isRemoved()
                && player.distanceToSqr(this.owner) <= 64.0D
                && this.owner.canUsePanel(player);
    }
}
