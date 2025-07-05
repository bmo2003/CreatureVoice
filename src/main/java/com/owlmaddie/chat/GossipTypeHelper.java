// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.chat;

import com.owlmaddie.utils.VillagerEntityAccessor;
import net.minecraft.village.VillageGossipType;
import java.util.UUID;

/**
 * Facade for gossip types on <1.21.5 — uses the old VillageGossipType.
 */
public class GossipTypeHelper {
    public static final VillageGossipType MAJOR_POSITIVE   = VillageGossipType.MAJOR_POSITIVE;
    public static final VillageGossipType MINOR_POSITIVE   = VillageGossipType.MINOR_POSITIVE;
    public static final VillageGossipType MINOR_NEGATIVE   = VillageGossipType.MINOR_NEGATIVE;
    public static final VillageGossipType MAJOR_NEGATIVE   = VillageGossipType.MAJOR_NEGATIVE;

    public static void startGossip(VillagerEntityAccessor villager, UUID playerId,
                                   VillageGossipType type, int amount) {
        villager.getGossip().startGossip(playerId, type, amount);
    }
}
