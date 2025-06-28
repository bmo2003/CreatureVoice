// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC BY-NC 4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.mixin;

import com.owlmaddie.utils.ISquidEntity;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes forceSwimVector(...) by writing the private swimVec field. */
@Mixin(SquidEntity.class)
public abstract class MixinSquidEntity implements ISquidEntity {
    // shadow the private field
    @Shadow private Vec3d swimVec;

    @Override
    public void forceSwimVector(Vec3d vec) {
        this.swimVec = vec;
    }
}
