// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC – unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.utils.WitherEntityAccessor;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 1.21+ mixin: bridges the new
 * {@code dropEquipment(ServerWorld, DamageSource, boolean)}
 * to our legacy 3-arg accessor.
 */
@Mixin(WitherEntity.class)
public abstract class MixinWitherEntity implements WitherEntityAccessor {

    // New 1.21 signature
    @Shadow
    protected abstract void dropEquipment(ServerWorld world,
                                          DamageSource source,
                                          boolean causedByPlayer);

    /** Keeps old API; {@code lootingMultiplier} is obsolete in 1.21. */
    @Override
    public void callDropEquipment(DamageSource source,
                                  int lootingMultiplier,
                                  boolean allowDrops) {
        ServerWorld world = (ServerWorld) ((WitherEntity) (Object) this).getWorld();
        dropEquipment(world, source, allowDrops);
    }
}
