// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.utils.VillagerEntityAccessor;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The {@code MixinVillagerEntity} class adds an accessor to expose the gossip system of {@link Villager}.
 * This allows external classes to retrieve and interact with a villager's gossip data.
 */
@Mixin(Villager.class)
public abstract class MixinVillagerEntity implements VillagerEntityAccessor {

    @Shadow
    private GossipContainer gossips;

    @Override
    public GossipContainer getGossip() {
        return this.gossips;
    }
}
