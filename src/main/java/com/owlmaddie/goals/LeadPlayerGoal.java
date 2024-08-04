package com.owlmaddie.goals;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.controls.LookControls;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
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
        this.totalWaypoints = random.nextInt(51) + 10;
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
                ChatDataManager.EntityChatData chatData = chatDataManager.getOrCreateChatData(this.entity.getUuidAsString());
                if (!chatData.characterSheet.isEmpty() && chatData.auto_generated < chatDataManager.MAX_AUTOGENERATE_RESPONSES) {
                    ServerPackets.generate_chat("N/A", chatData, (ServerPlayerEntity) this.targetEntity, this.entity, arrivedMessage, true);
                }
            });

            // Stop navigation
            this.entity.getNavigation().stop();

        } else if (this.currentTarget == null || this.entity.squaredDistanceTo(this.currentTarget) < 2 * 2 || ticksSinceLastWaypoint >= 75) {
            // Set next waypoint
            setNewTarget();
            moveToTarget();
            ticksSinceLastWaypoint = 0;

        } else if (!(this.entity instanceof PathAwareEntity)) {
            moveToTarget();
        }
    }

    private void moveToTarget() {
        if (this.currentTarget != null) {
            // Make the entity look at the player without moving towards them
            LookControls.lookAtPosition(this.currentTarget, this.entity);

            Path path = this.entity.getNavigation().findPathTo(this.currentTarget.x, this.currentTarget.y, this.currentTarget.z, 2);
            if (path != null) {
                LOGGER.info("Start moving towards waypoint PATH");
                this.entity.getNavigation().startMovingAlong(path, this.speed);

            } else {
                // Move towards the target for non-path aware entities
                Vec3d entityPos = this.entity.getPos();
                Vec3d moveDirection = this.currentTarget.subtract(entityPos).normalize();

                // Calculate current speed from the entity's current velocity
                double currentSpeed = this.entity.getVelocity().horizontalLength();

                // Gradually adjust speed towards the target speed
                currentSpeed = MathHelper.stepTowards((float)currentSpeed, (float)this.speed, (float) (0.005 * (this.speed / Math.max(currentSpeed, 0.1))));

                // Apply movement with the adjusted speed towards the target
                Vec3d newVelocity = new Vec3d(moveDirection.x * currentSpeed, this.entity.getVelocity().y, moveDirection.z * currentSpeed);

                this.entity.setVelocity(newVelocity);
                this.entity.velocityModified = true;
            }
        }
    }

    private void setNewTarget() {
        // Increment waypoint
        currentWaypoint++;
        LOGGER.info("Waypoint " + currentWaypoint + " / " + this.totalWaypoints);
        this.currentTarget = findNextWaypoint();
        emitParticleAt(this.currentTarget, ParticleTypes.FLAME);
    }

    private Vec3d findNextWaypoint() {
        LOGGER.info("Create waypoint position");

        Vec3d entityPos = this.entity.getPos();
        Vec3d currentDirection;

        // Check if currentTarget is null
        if (this.currentTarget == null) {
            // If currentTarget is null, use the entity's facing direction
            double yaw = this.entity.getYaw() * (Math.PI / 180); // Convert to radians
            currentDirection = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        } else {
            // Calculate the current direction vector
            currentDirection = this.currentTarget.subtract(entityPos).normalize();
        }

        // Calculate the angle of the current direction
        double currentAngle = Math.atan2(currentDirection.z, currentDirection.x);

        // Generate a random angle within ±45 degrees of the current direction
        double randomAngleOffset = (random.nextDouble() * (Math.PI / 4)) - (Math.PI / 8); // ±45 degrees
        double angle = currentAngle + randomAngleOffset;

        // Calculate the random distance
        double distance = 16 + random.nextDouble() * 2;

        // Calculate new coordinates based on the limited angle
        double x = entityPos.x + distance * Math.cos(angle);
        double y = entityPos.y + (random.nextDouble() * 10 - 5); // Similar y-coordinate depth
        double z = entityPos.z + distance * Math.sin(angle);

        return new Vec3d(x, y, z);
    }

    private void emitParticleAt(Vec3d position, ParticleEffect particleType) {
        ServerWorld serverWorld = (ServerWorld) this.entity.getWorld();
        serverWorld.spawnParticles(particleType, position.x, position.y, position.z, 3, 0, 0, 0, 0);
    }
}