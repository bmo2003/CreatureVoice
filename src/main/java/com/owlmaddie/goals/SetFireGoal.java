// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.goals;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code SetFireGoal} class makes a mob walk toward a target building and
 * set a flammable block on fire, then flee the scene.
 *
 * The mob walks to the building, scans for a flammable block every 5 ticks,
 * lights ONE fire, then adds a persistent GoToPositionGoal to flee ~15 blocks
 * away from the building so Brain.tick() can't override the escape.
 *
 * If no flammable blocks are found within 100 ticks (~5 seconds) of arriving
 * at the building, the goal aborts — preventing the mob from standing around
 * looking foolish at a stone house.
 *
 * Brain-based mobs (villagers) have their navigation overridden at END_SERVER_TICK
 * via the FIRE_MOBS map, just like GoToPositionGoal and FleePlayerGoal.
 */
public class SetFireGoal extends Goal {
    // Registered in start(), cleared in stop(). NpcLifeManager reads this at
    // END_SERVER_TICK to re-issue moveTo after Brain.tick() clears it.
    public static final ConcurrentHashMap<UUID, SetFireGoal> FIRE_MOBS = new ConcurrentHashMap<>();

    private final Mob entity;
    private final BlockPos targetPos;
    private final double speed;
    private int tickCounter = 0;
    private boolean fired = false;  // true once a fire has been placed

    // After the mob arrives within scan range of the building, count how many ticks
    // pass without finding a flammable block. Abort after 100 ticks (~5 seconds) so
    // the mob doesn't stand around doing nothing at a stone/brick building.
    private int nearBuildingTicks = 0;
    private static final int NO_FLAMMABLE_TIMEOUT = 100;

    public SetFireGoal(Mob entity, BlockPos targetPos, double speed) {
        this.entity = entity;
        this.targetPos = targetPos;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public void start() {
        FIRE_MOBS.put(entity.getUUID(), this);
    }

    @Override
    public boolean canUse() {
        // Must match canContinueToUse conditions — otherwise the GoalSelector restarts
        // this goal every tick after it stops (canUse=true → start → canContinueToUse=false
        // → stop → queues flee goal → next tick replaces it → mob stands still forever).
        return !fired && !entity.isOnFire() && nearBuildingTicks < NO_FLAMMABLE_TIMEOUT;
    }

    @Override
    public boolean canContinueToUse() {
        // Abort if the mob is on fire — it walked into the flames
        if (entity.isOnFire()) return false;
        // Done after lighting one fire
        if (fired) return false;
        // Abort if timed out near building with no flammable blocks (stone house)
        if (nearBuildingTicks >= NO_FLAMMABLE_TIMEOUT) return false;
        return true;
    }

    @Override
    public void stop() {
        FIRE_MOBS.remove(entity.getUUID());
        // Flee away from the building using a persistent GoToPositionGoal so Brain.tick()
        // can't override the escape route. The mob resumes normal behavior on arrival
        // (no StayGoal — just an empty callback so it doesn't freeze in place forever).
        Vec3 mobPos = entity.position();
        double dx = mobPos.x() - (targetPos.getX() + 0.5);
        double dz = mobPos.z() - (targetPos.getZ() + 0.5);
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.1) {
            // Flee 20 blocks away. Use the mob's current Y — pathfinder handles elevation changes.
            BlockPos fleePos = BlockPos.containing(
                    mobPos.x() + (dx / len) * 20,
                    mobPos.y(),
                    mobPos.z() + (dz / len) * 20);
            // No-op callback so the mob resumes normal behavior instead of standing still forever
            EntityBehaviorManager.addGoal(entity,
                    new GoToPositionGoal(entity, fleePos, speed * 1.5, () -> {}),
                    GoalPriority.FLEE_PLAYER);
        } else {
            // Mob is standing right at the building center — pick an arbitrary direction
            BlockPos fleePos = BlockPos.containing(
                    mobPos.x() + 20, mobPos.y(), mobPos.z() + 20);
            EntityBehaviorManager.addGoal(entity,
                    new GoToPositionGoal(entity, fleePos, speed * 1.5, () -> {}),
                    GoalPriority.FLEE_PLAYER);
        }
    }

    @Override
    public void tick() {
        tickCounter++;

        // Navigate toward the building — re-issue every tick to beat Brain AI
        double dx = targetPos.getX() + 0.5 - entity.getX();
        double dz = targetPos.getZ() + 0.5 - entity.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq > 3 * 3) {
            // Still walking toward the building
            entity.getNavigation().moveTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5,
                    speed);
        } else {
            // Within scan range — count ticks to detect stone house timeout
            nearBuildingTicks++;
        }

        // Every 5 ticks (~0.25 seconds) try to ignite a nearby flammable block
        if (tickCounter % 5 == 0) {
            tryIgniteNearby();
        }
    }

    public Mob getEntity() { return entity; }
    public BlockPos getTargetPos() { return targetPos; }
    public double getSpeed() { return speed; }

    private void tryIgniteNearby() {
        Level level = entity.level();
        BlockPos mobPos = entity.blockPosition();

        // Scan a 7x6x7 box around the mob for the first flammable block that has air above
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    BlockPos checkPos = mobPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);
                    BlockPos firePos = checkPos.above();

                    // Flammable = logs, planks, leaves, wooden stairs/slabs/fences (all standard tags).
                    // Fire is placed on the empty block above — it then spreads naturally.
                    boolean flammable = state.is(BlockTags.LOGS)
                            || state.is(BlockTags.PLANKS)
                            || state.is(BlockTags.LEAVES)
                            || state.is(BlockTags.WOOL)
                            || state.is(BlockTags.WOODEN_STAIRS)
                            || state.is(BlockTags.WOODEN_SLABS)
                            || state.is(BlockTags.WOODEN_FENCES);
                    if (flammable && level.isEmptyBlock(firePos)) {
                        level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                        fired = true; // One fire is enough — fire spreads naturally, mob flees
                        return;
                    }
                }
            }
        }
    }
}
