package com.owlmaddie.mixin;

import com.owlmaddie.utils.VillagerEntityAccessor;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerGossips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The {@code MixinVillagerEntity} class adds an accessor to expose the gossip system of {@link VillagerEntity}.
 * This allows external classes to retrieve and interact with a villager's gossip data.
 */
@Mixin(VillagerEntity.class)
public abstract class MixinVillagerEntity implements VillagerEntityAccessor {

    @Shadow
    private VillagerGossips gossip;

    @Override
    // Access a Villager's gossip system
    public VillagerGossips getGossip() {
        return this.gossip;
    }
}
