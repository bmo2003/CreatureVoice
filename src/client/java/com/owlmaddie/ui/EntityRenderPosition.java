// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class EntityRenderPosition {
    public static Vec3 getInterpolatedPosition(Entity entity, float partialTicks) {
        double x = Mth.lerp(partialTicks, entity.xo, entity.position().x);
        double y = Mth.lerp(partialTicks, entity.yo, entity.position().y);
        double z = Mth.lerp(partialTicks, entity.zo, entity.position().z);
        return new Vec3(x, y, z);
    }
}