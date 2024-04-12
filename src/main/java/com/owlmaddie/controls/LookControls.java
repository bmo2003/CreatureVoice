package com.owlmaddie.controls;

import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
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
        if (entity instanceof SlimeEntity) {
            handleSlimeLook((SlimeEntity) entity, player);
        } else if (entity instanceof SquidEntity) {
            handleSquidLook((SquidEntity) entity, player);
        } else if (entity instanceof FlyingEntity) {
            handleFlyingEntity((FlyingEntity) entity, player);
        } else {
            // Make the entity look at the player
            entity.getLookControl().lookAt(player, 10.0F, (float)entity.getMaxLookPitchChange());
        }
    }

    private static void handleSlimeLook(SlimeEntity slime, ServerPlayerEntity player) {
        float yawChange = calculateYawChangeToPlayer(slime, player);
        ((SlimeEntity.SlimeMoveControl) slime.getMoveControl()).look(slime.getYaw() + yawChange, false);
    }

    private static void handleSquidLook(SquidEntity squid, ServerPlayerEntity player) {
        Vec3d toPlayer = calculateNormalizedDirection(squid, player);
        float initialSwimStrength = 0.15f;
        squid.setSwimmingVector(
                (float) toPlayer.x * initialSwimStrength,
                (float) toPlayer.y * initialSwimStrength,
                (float) toPlayer.z * initialSwimStrength
        );

        double distanceToPlayer = squid.getPos().distanceTo(player.getPos());
        if (distanceToPlayer < 3.5F) {
            // Stop motion when close
            squid.setVelocity(0,0,0);
        }
    }

    // Ghast, Phantom, etc...
    private static void handleFlyingEntity(FlyingEntity flyingEntity, ServerPlayerEntity player) {
        Vec3d playerPosition = player.getPos();
        Vec3d flyingPosition = flyingEntity.getPos();
        Vec3d toPlayer = playerPosition.subtract(flyingPosition).normalize();

        // Calculate the yaw to align the flyingEntity's facing direction with the movement direction
        float targetYaw = (float)(MathHelper.atan2(toPlayer.z, toPlayer.x) * (180 / Math.PI) - 90);
        flyingEntity.setYaw(targetYaw);

        // Look at player while adjusting yaw
        flyingEntity.getLookControl().lookAt(player, 10.0F, (float)flyingEntity.getMaxLookPitchChange());

        float initialSpeed = 0.15F;
        flyingEntity.setVelocity(
                (float) toPlayer.x * initialSpeed,
                (float) toPlayer.y * initialSpeed,
                (float) toPlayer.z * initialSpeed
        );

        double distanceToPlayer = flyingEntity.getPos().distanceTo(player.getPos());
        if (distanceToPlayer < 9F) {
            // Stop motion when close
            flyingEntity.setVelocity(0, 0, 0);
        }
    }

    public static float calculateYawChangeToPlayer(MobEntity entity, ServerPlayerEntity player) {
        Vec3d toPlayer = calculateNormalizedDirection(entity, player);
        float targetYaw = (float) Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90.0F;
        float yawDifference = MathHelper.wrapDegrees(targetYaw - entity.getYaw());
        return MathHelper.clamp(yawDifference, -10.0F, 10.0F);
    }

    public static Vec3d calculateNormalizedDirection(MobEntity entity, ServerPlayerEntity player) {
        Vec3d playerPos = player.getPos();
        Vec3d entityPos = entity.getPos();
        return playerPos.subtract(entityPos).normalize();
    }
}