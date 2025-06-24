// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.utils.WitherEntityAccessor;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Mixin to expose the protected dropEquipment method from WitherEntity.
 */
@Mixin(WitherEntity.class)
public abstract class MixinWitherEntity implements WitherEntityAccessor {

    @Shadow
    protected abstract void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops);

    @Override
    public void callDropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        dropEquipment(source, lootingMultiplier, allowDrops);
    }
}
