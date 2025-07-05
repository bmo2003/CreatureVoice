// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.chat;

import com.owlmaddie.utils.VillagerEntityAccessor;
import net.minecraft.village.VillagerGossipType;
import java.util.UUID;

/**
 * Override for 1.21.5 — uses the renamed VillagerGossipType.
 */
public class GossipTypeHelper {
    public static final VillagerGossipType MAJOR_POSITIVE   = VillagerGossipType.MAJOR_POSITIVE;
    public static final VillagerGossipType MINOR_POSITIVE   = VillagerGossipType.MINOR_POSITIVE;
    public static final VillagerGossipType MINOR_NEGATIVE   = VillagerGossipType.MINOR_NEGATIVE;
    public static final VillagerGossipType MAJOR_NEGATIVE   = VillagerGossipType.MAJOR_NEGATIVE;

    public static void startGossip(VillagerEntityAccessor villager, UUID playerId,
                                   VillagerGossipType type, int amount) {
        villager.getGossip().startGossip(playerId, type, amount);
    }
}
