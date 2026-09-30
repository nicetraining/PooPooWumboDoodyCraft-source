package com.trolmastercard.sexmod.client;

import com.trolmastercard.sexmod.entity.CompanionEntity;
import net.minecraft.client.Minecraft;

/** Entry points called from common code on the logical client; kept in one class so servers never load screens. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openActionPanel(CompanionEntity companion) {
        Minecraft.getInstance().setScreen(new CompanionActionScreen(companion));
    }
}
