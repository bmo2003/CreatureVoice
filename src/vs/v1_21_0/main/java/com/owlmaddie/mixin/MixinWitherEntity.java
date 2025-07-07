// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC – unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.utils.WitherEntityAccessor;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 1.21+ mixin: bridges the new
 * {@code dropEquipment(ServerWorld, DamageSource, boolean)}
 * to our legacy 3-arg accessor.
 */
@Mixin(WitherBoss.class)
public abstract class MixinWitherEntity implements WitherEntityAccessor {

    /**
     * Shadow the new signature:
     * protected void dropCustomDeathLoot(ServerLevel, DamageSource, boolean)
     */
    @Shadow
    protected abstract void dropCustomDeathLoot(ServerLevel world,
                                                DamageSource source,
                                                boolean allowDrops);

    /**
     * Legacy interface method. We ignore lootingMultiplier (dropped in 1.21)
     * and forward into the new hook.
     */
    @Override
    public void callDropEquipment(DamageSource source,
                                  int lootingMultiplier,
                                  boolean allowDrops) {
        // level() returns a Level; cast to ServerLevel
        ServerLevel world = (ServerLevel) ((WitherBoss)(Object)this).level();
        dropCustomDeathLoot(world, source, allowDrops);
    }
}
