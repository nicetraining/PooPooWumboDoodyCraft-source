package com.fartypoopy.sexmod.client;

import com.fartypoopy.sexmod.entity.CompanionEntity;

import java.util.UUID;

/** Client lookups used by common event handlers (only called on the client side). */
public final class ClientFormAccess {
    private ClientFormAccess() {
    }

    public static CompanionEntity formOf(UUID owner) {
        return PlayerFormClient.formOf(owner);
    }
}
