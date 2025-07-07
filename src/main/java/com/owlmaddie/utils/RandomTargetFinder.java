// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code RandomTargetFinder} class generates random targets around an entity (the LEAD behavior uses this)
 */
public class RandomTargetFinder {
    private static final Random random = new Random();

    public static Vec3 findRandomTarget(Mob entity, double maxAngleOffset, double minDistance, double maxDistance) {
        Vec3 entityPos = entity.position();
        Vec3 initialDirection = getLookDirection(entity);

        for (int attempt = 0; attempt < 10; attempt++) {
            Vec3 constrainedDirection = getConstrainedDirection(initialDirection, maxAngleOffset);
            Vec3 target = getTargetInDirection(entity, constrainedDirection, minDistance, maxDistance);

            if (entity instanceof PathfinderMob) {
                Vec3 validTarget = LandRandomPos.getPosTowards((PathfinderMob) entity, (int) maxDistance, (int) maxDistance, target);

                if (validTarget != null && isWithinDistance(entityPos, validTarget, minDistance, maxDistance)) {
                    Path path = entity.getNavigation().createPath(validTarget.x, validTarget.y, validTarget.z, 4);
                    if (path != null) {
                        return validTarget;
                    }
                }
            } else {
                if (isWithinDistance(entityPos, target, minDistance, maxDistance)) {
                    return target;
                }
            }
        }

        return getTargetInDirection(entity, initialDirection, minDistance, maxDistance);
    }

    private static Vec3 getLookDirection(Mob entity) {
        float yaw = entity.getYRot() * ((float) Math.PI / 180F);
        float pitch = entity.getXRot() * ((float) Math.PI / 180F);
        float x = -Mth.sin(yaw) * Mth.cos(pitch);
        float y = -Mth.sin(pitch);
        float z = Mth.cos(yaw) * Mth.cos(pitch);
        return new Vec3(x, y, z);
    }

    private static Vec3 getConstrainedDirection(Vec3 initialDirection, double maxAngleOffset) {
        double randomYawAngleOffset = (random.nextDouble() * Math.toRadians(maxAngleOffset)) - Math.toRadians(maxAngleOffset / 2);
        double randomPitchAngleOffset = (random.nextDouble() * Math.toRadians(maxAngleOffset)) - Math.toRadians(maxAngleOffset / 2);

        // Apply the yaw rotation (around the Y axis)
        double cosYaw = Math.cos(randomYawAngleOffset);
        double sinYaw = Math.sin(randomYawAngleOffset);
        double xYaw = initialDirection.x * cosYaw - initialDirection.z * sinYaw;
        double zYaw = initialDirection.x * sinYaw + initialDirection.z * cosYaw;

        // Apply the pitch rotation (around the X axis)
        double cosPitch = Math.cos(randomPitchAngleOffset);
        double sinPitch = Math.sin(randomPitchAngleOffset);
        double yPitch = initialDirection.y * cosPitch - zYaw * sinPitch;
        double zPitch = zYaw * cosPitch + initialDirection.y * sinPitch;
        return new Vec3(xYaw, yPitch, zPitch).normalize();
    }

    private static Vec3 getTargetInDirection(Mob entity, Vec3 direction, double minDistance, double maxDistance) {
        double distance = minDistance + entity.getRandom().nextDouble() * (maxDistance - minDistance);
        return entity.position().add(direction.scale(distance));
    }

    private static boolean isWithinDistance(Vec3 entityPos, Vec3 targetPos, double minDistance, double maxDistance) {
        double distance = entityPos.distanceToSqr(targetPos);
        return distance >= minDistance * minDistance && distance <= maxDistance * maxDistance;
    }
}
