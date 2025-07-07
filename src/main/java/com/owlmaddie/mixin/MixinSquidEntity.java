// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.controls.ISquidEntity;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/** Exposes forceSwimVector(...) by calling the old public API. */
@Mixin(Squid.class)
public abstract class MixinSquidEntity implements ISquidEntity {
    @Override
    public void forceSwimVector(Vec3 vec) {
        // 1.20: the public method still exists
        ((Squid)(Object)this)
            .setMovementVector((float)vec.x, (float)vec.y, (float)vec.z);
    }
}
