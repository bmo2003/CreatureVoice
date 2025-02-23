package com.owlmaddie.utils;

import net.minecraft.entity.damage.DamageSource;

/**
 * Accessor interface for WitherEntity to allow calling dropEquipment externally.
 */
public interface WitherEntityAccessor {
    void callDropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops);
}
