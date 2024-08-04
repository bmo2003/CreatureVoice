package com.owlmaddie.goals;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
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
            LOGGER.info("destination reached");

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
        }
    }

    private void setNewTarget() {
        boolean targetFound = false;

        if (this.entity instanceof PathAwareEntity) {
            int attempts = 0;
            while (attempts < 3 && !targetFound) {
                Vec3d target = FuzzyTargeting.findFrom((PathAwareEntity) this.entity, 16, 6, this.entity.getPos());
                if (target != null) {
                    currentWaypoint++;
                    this.currentTarget = target;
                    LOGGER.info("Waypoint " + currentWaypoint + " / " + this.totalWaypoints);
                    targetFound = true;
                }
                attempts++;
            }
        }

        if (!targetFound) {
            // Fallback if no target found after 3 attempts
            currentWaypoint++;
            LOGGER.info("Waypoint " + currentWaypoint + " / " + this.totalWaypoints);

            double distance = 20 + random.nextDouble() * 80;
            double angle = random.nextDouble() * 2 * Math.PI;
            double x = this.targetEntity.getX() + distance * Math.cos(angle);
            double y = this.targetEntity.getY() + (random.nextDouble() * 10 - 5); // Similar y-coordinate depth
            double z = this.targetEntity.getZ() + distance * Math.sin(angle);
            this.currentTarget = new Vec3d(x, y, z);
        }
    }

    private void moveToTarget() {
        if (this.currentTarget != null) {
            if (this.entity instanceof PathAwareEntity) {
                int attempts = 0;

                while (attempts < 3) {
                    Path path = this.entity.getNavigation().findPathTo(this.currentTarget.x, this.currentTarget.y, this.currentTarget.z, 1);
                    if (path != null) {
                        this.entity.getNavigation().startMovingAlong(path, this.speed);
                        return;
                    }
                     attempts++;
                }
            } else {
                // Move directly towards the target for non-path aware entities
                Vec3d entityPos = this.entity.getPos();
                Vec3d moveDirection = this.currentTarget.subtract(entityPos).normalize();
                this.entity.setVelocity(moveDirection.x * this.speed, this.entity.getVelocity().y, moveDirection.z * this.speed);
                this.entity.velocityModified = true;
            }
        }
    }

    @Override
    public void start() {
        moveToTarget();
    }
}