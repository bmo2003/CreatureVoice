package com.owlmaddie.controls;

import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.SquidEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * The {@code LookControls} class enables entities to look at the player,
 * with specific adjustments for certain mobs like Slimes and Squids.
 */
public class LookControls {
    public static void lookAtPlayer(ServerPlayerEntity player, MobEntity entity) {
        lookAtPosition(player.getPos(), entity);
    }

    public static void lookAtPosition(Vec3d targetPos, MobEntity entity) {
        if (entity instanceof SlimeEntity) {
            handleSlimeLook((SlimeEntity) entity, targetPos);
        } else if (entity instanceof SquidEntity) {
            handleSquidLook((SquidEntity) entity, targetPos);
        } else if (entity instanceof GhastEntity) {
            handleFlyingEntity(entity, targetPos, 10F);
        } else if (entity instanceof FlyingEntity || entity instanceof VexEntity) {
            handleFlyingEntity(entity, targetPos, 4F);
        } else {
            // Make the entity look at the player
            entity.getLookControl().lookAt(targetPos.x, targetPos.y, targetPos.z, 10.0F, (float)entity.getMaxLookPitchChange());
        }
    }

    private static void handleSlimeLook(SlimeEntity slime, Vec3d targetPos) {
        float yawChange = calculateYawChange(slime, targetPos);
        ((SlimeEntity.SlimeMoveControl) slime.getMoveControl()).look(slime.getYaw() + yawChange, false);
    }

    private static void handleSquidLook(SquidEntity squid, Vec3d targetPos) {
        Vec3d toPlayer = calculateNormalizedDirection(squid, targetPos);
        float initialSwimStrength = 0.15f;
        squid.setSwimmingVector(
                (float) toPlayer.x * initialSwimStrength,
                (float) toPlayer.y * initialSwimStrength,
                (float) toPlayer.z * initialSwimStrength
        );

        double distanceToPlayer = squid.getPos().distanceTo(targetPos);
        if (distanceToPlayer < 3.5F) {
            // Stop motion when close
            squid.setVelocity(0,0,0);
        }
    }

    // Ghast, Phantom, etc...
    private static void handleFlyingEntity(MobEntity flyingEntity, Vec3d targetPos, float stopDistance) {
        Vec3d flyingPosition = flyingEntity.getPos();
        Vec3d toPlayer = targetPos.subtract(flyingPosition).normalize();

        // Calculate the yaw to align the flyingEntity's facing direction with the movement direction
        float targetYaw = (float)(MathHelper.atan2(toPlayer.z, toPlayer.x) * (180 / Math.PI) - 90);
        flyingEntity.setYaw(targetYaw);

        // Look at player while adjusting yaw
        flyingEntity.getLookControl().lookAt(targetPos.x, targetPos.y, targetPos.z, 10.0F, (float)flyingEntity.getMaxLookPitchChange());

        float initialSpeed = 0.15F;
        flyingEntity.setVelocity(
                (float) toPlayer.x * initialSpeed,
                (float) toPlayer.y * initialSpeed,
                (float) toPlayer.z * initialSpeed
        );

        double distanceToPlayer = flyingEntity.getPos().distanceTo(targetPos);
        if (distanceToPlayer < stopDistance) {
            // Stop motion when close
            flyingEntity.setVelocity(0, 0, 0);
        }
    }

    public static float calculateYawChange(MobEntity entity, Vec3d targetPos) {
        Vec3d toPlayer = calculateNormalizedDirection(entity, targetPos);
        float targetYaw = (float) Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90.0F;
        float yawDifference = MathHelper.wrapDegrees(targetYaw - entity.getYaw());
        return MathHelper.clamp(yawDifference, -10.0F, 10.0F);
    }

    public static Vec3d calculateNormalizedDirection(MobEntity entity, Vec3d targetPos) {
        Vec3d entityPos = entity.getPos();
        return targetPos.subtract(entityPos).normalize();
    }
}