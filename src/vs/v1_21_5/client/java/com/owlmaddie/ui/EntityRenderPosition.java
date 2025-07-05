// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.ui;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class EntityRenderPosition {
    public static Vec3d getInterpolatedPosition(Entity entity, float partialTicks) {
        double x = MathHelper.lerp(partialTicks, entity.lastRenderX, entity.getX());
        double y = MathHelper.lerp(partialTicks, entity.lastRenderY, entity.getY());
        double z = MathHelper.lerp(partialTicks, entity.lastRenderZ, entity.getZ());
        return new Vec3d(x, y, z);
    }
}