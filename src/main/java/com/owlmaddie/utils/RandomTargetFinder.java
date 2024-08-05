package com.owlmaddie.utils;

import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * The {@code RandomTargetFinder} class generates random targets around an entity (the LEAD behavior uses this)
 */
public class RandomTargetFinder {
    private static final Random random = new Random();

    public static Vec3d findRandomTarget(MobEntity entity, double maxAngleOffset, double minDistance, double maxDistance) {
        Vec3d entityPos = entity.getPos();
        Vec3d initialDirection = getLookDirection(entity);

        for (int attempt = 0; attempt < 10; attempt++) {
            Vec3d constrainedDirection = getConstrainedDirection(initialDirection, maxAngleOffset);
            Vec3d target = getTargetInDirection(entity, constrainedDirection, minDistance, maxDistance);

            if (entity instanceof PathAwareEntity) {
                Vec3d validTarget = FuzzyTargeting.findFrom((PathAwareEntity) entity, (int) maxDistance, (int) maxDistance / 2, target);

                if (validTarget != null && isWithinDistance(entityPos, validTarget, minDistance, maxDistance)) {
                    Path path = entity.getNavigation().findPathTo(validTarget.x, validTarget.y, validTarget.z, 1);
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

    private static Vec3d getLookDirection(MobEntity entity) {
        float yaw = entity.getYaw() * ((float) Math.PI / 180F);
        float pitch = entity.getPitch() * ((float) Math.PI / 180F);
        float x = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        float y = -MathHelper.sin(pitch);
        float z = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        return new Vec3d(x, y, z);
    }

    private static Vec3d getConstrainedDirection(Vec3d initialDirection, double maxAngleOffset) {
        double currentAngle = Math.atan2(initialDirection.z, initialDirection.x);

        double randomHorizontalAngleOffset = (random.nextDouble() * Math.toRadians(maxAngleOffset)) - Math.toRadians(maxAngleOffset / 2);
        double constrainedAngle = currentAngle + randomHorizontalAngleOffset;

        double x = Math.cos(constrainedAngle);
        double z = Math.sin(constrainedAngle);

        return new Vec3d(x, initialDirection.y, z).normalize();
    }

    private static Vec3d getTargetInDirection(MobEntity entity, Vec3d direction, double minDistance, double maxDistance) {
        double distance = minDistance + entity.getRandom().nextDouble() * (maxDistance - minDistance);
        return entity.getPos().add(direction.multiply(distance));
    }

    private static boolean isWithinDistance(Vec3d entityPos, Vec3d targetPos, double minDistance, double maxDistance) {
        double distance = entityPos.squaredDistanceTo(targetPos);
        return distance >= minDistance * minDistance && distance <= maxDistance * maxDistance;
    }
}
