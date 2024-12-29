package com.owlmaddie.goals;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
import com.owlmaddie.controls.LookControls;
import com.owlmaddie.network.ServerPackets;
import com.owlmaddie.particle.LeadParticleEffect;
import com.owlmaddie.utils.RandomTargetFinder;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Random;

/**
 * The {@code LeadPlayerGoal} class instructs a Mob Entity to lead the player to a random location, consisting
 * of many random waypoints. It supports PathAware and NonPathAware entities.
 */
public class LeadPlayerGoal extends PlayerBaseGoal {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    private final MobEntity entity;
    private final double speed;
    private final Random random = new Random();
    private int currentWaypoint = 0;
    private int totalWaypoints;
    private Vec3d currentTarget = null;
    private boolean foundWaypoint = false;
    private int ticksSinceLastWaypoint = 0;

    public LeadPlayerGoal(ServerPlayerEntity player, MobEntity entity, double speed) {
        super(player);
        this.entity = entity;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        this.totalWaypoints = random.nextInt(14) + 6;
    }

    @Override
    public boolean canStart() {
        return super.canStart() && !foundWaypoint && this.entity.squaredDistanceTo(this.targetEntity) <= 16 * 16 && !foundWaypoint;
    }

    @Override
    public boolean shouldContinue() {
        return super.canStart() && !foundWaypoint && this.entity.squaredDistanceTo(this.targetEntity) <= 16 * 16 && !foundWaypoint;
    }

    @Override
    public void tick() {
        ticksSinceLastWaypoint++;

        if (this.entity.squaredDistanceTo(this.targetEntity) > 16 * 16) {
            this.entity.getNavigation().stop();
            return;
        }

        // Are we there yet?
        if (currentWaypoint >= totalWaypoints && !foundWaypoint) {
            foundWaypoint = true;
            LOGGER.info("Tick: You have ARRIVED at your destination");

            ServerPackets.scheduler.scheduleTask(() -> {
                // Prepare a message about the interaction
                String arrivedMessage = "<You have arrived at your destination>";

                ChatDataManager chatDataManager = ChatDataManager.getServerInstance();
                EntityChatData chatData = chatDataManager.getOrCreateChatData(this.entity.getUuidAsString(), this.targetEntity.getDisplayName().getString());
                if (!chatData.characterSheet.isEmpty() && chatData.auto_generated < chatDataManager.MAX_AUTOGENERATE_RESPONSES) {
                    ServerPackets.generate_chat("N/A", chatData, (ServerPlayerEntity) this.targetEntity, this.entity, arrivedMessage, true);
                }
            });

            // Stop navigation
            this.entity.getNavigation().stop();

        } else if (this.currentTarget == null || this.entity.squaredDistanceTo(this.currentTarget) < 2 * 2 || ticksSinceLastWaypoint >= 20 * 10) {
            // Set next waypoint
            setNewTarget();
            moveToTarget();
            ticksSinceLastWaypoint = 0;

        } else {
            moveToTarget();
        }
    }

    private void moveToTarget() {
        if (this.currentTarget != null) {
            if (this.entity instanceof PathAwareEntity) {
                 if (!this.entity.getNavigation().isFollowingPath()) {
                     Path path = this.entity.getNavigation().findPathTo(this.currentTarget.x, this.currentTarget.y, this.currentTarget.z, 1);
                     if (path != null) {
                         LOGGER.debug("Start moving along path");
                         this.entity.getNavigation().startMovingAlong(path, this.speed);
                     }
                 }
            } else {
                // Make the entity look at the player without moving towards them
                LookControls.lookAtPosition(this.currentTarget, this.entity);

                // Move towards the target for non-path aware entities
                Vec3d entityPos = this.entity.getPos();
                Vec3d moveDirection = this.currentTarget.subtract(entityPos).normalize();

                // Calculate current speed from the entity's current velocity
                double currentSpeed = this.entity.getVelocity().horizontalLength();

                // Gradually adjust speed towards the target speed
                currentSpeed = MathHelper.stepTowards((float) currentSpeed, (float) this.speed, (float) (0.005 * (this.speed / Math.max(currentSpeed, 0.1))));

                // Apply movement with the adjusted speed towards the target
                Vec3d newVelocity = new Vec3d(moveDirection.x * currentSpeed, moveDirection.y * currentSpeed, moveDirection.z * currentSpeed);

                this.entity.setVelocity(newVelocity);
                this.entity.velocityModified = true;
            }
        }
    }

    private void setNewTarget() {
        // Increment waypoint
        currentWaypoint++;
        LOGGER.info("Waypoint " + currentWaypoint + " / " + this.totalWaypoints);
        this.currentTarget = RandomTargetFinder.findRandomTarget(this.entity, 30, 24, 36);
        if (this.currentTarget != null) {
            emitParticlesAlongRaycast(this.entity.getPos(), this.currentTarget);
        }

        // Stop following current path (if any)
        this.entity.getNavigation().stop();
    }

    private void emitParticleAt(Vec3d position, double angle) {
        if (this.entity.getWorld() instanceof ServerWorld) {
            ServerWorld serverWorld = (ServerWorld) this.entity.getWorld();

            // Pass the angle using the "speed" argument, with deltaX, deltaY, deltaZ set to 0
            LeadParticleEffect effect = new LeadParticleEffect(angle);
            serverWorld.spawnParticles(effect, position.x, position.y + 0.05, position.z, 1, 0, 0, 0, 0);
        }
    }

    private void emitParticlesAlongRaycast(Vec3d start, Vec3d end) {
        // Calculate the direction vector from the entity (start) to the target (end)
        Vec3d direction = end.subtract(start);

        // Calculate the angle in the XZ-plane using atan2 (this is in radians)
        double angleRadians = Math.atan2(direction.z, direction.x);

        // Convert from radians to degrees
        double angleDegrees = Math.toDegrees(angleRadians);

        // Convert the calculated angle to Minecraft's yaw system:
        double minecraftYaw = (360 - (angleDegrees + 90)) % 360;

        // Correct the 180-degree flip
        minecraftYaw = (minecraftYaw + 180) % 360;
        if (minecraftYaw < 0) {
            minecraftYaw += 360;
        }

        // Emit particles along the ray from startRange to endRange
        double distance = start.distanceTo(end);
        double startRange = Math.min(5, distance);;
        double endRange = Math.min(startRange + 10, distance);
        for (double d = startRange; d <= endRange; d += 5) {
            Vec3d pos = start.add(direction.normalize().multiply(d));
            emitParticleAt(pos, Math.toRadians(minecraftYaw));  // Convert back to radians for rendering
        }
    }
}