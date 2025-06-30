// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.controls.ISquidEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;

/** Exposes forceSwimVector(...) by calling the old public API. */
@Mixin(SquidEntity.class)
public abstract class MixinSquidEntity implements ISquidEntity {
    @Override
    public void forceSwimVector(Vec3d vec) {
        // 1.20: the public method still exists
        ((SquidEntity)(Object)this)
            .setSwimmingVector((float)vec.x, (float)vec.y, (float)vec.z);
    }
}
