package org.vivecraft.gameplay.trackers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;

public class CombatTracker extends Tracker{

    public CombatTracker(Minecraft mc) {
        super(mc);
    }

    @Override
    public boolean isActive(ClientPlayerEntity player) {
        return false;
    }

    @Override
    public void doProcess(ClientPlayerEntity player) {

    }
}
