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

import java.util.EnumSet;

/**
 * The {@code SetFireGoal} class makes a mob walk toward a target building and
 * set flammable blocks on fire as it gets close — simulating an arsonist.
 *
 * Every 5 ticks the goal scans a small area around the mob for flammable blocks
 * (wood, leaves, etc.) and places a fire block on top of one. Fire then spreads
 * naturally via Minecraft's own fire propagation. Navigation is re-issued every
 * tick to override Brain-based AI (villagers) so the mob actually reaches the building.
 */
public class SetFireGoal extends Goal {
    private final Mob entity;
    private final BlockPos targetPos;
    private final double speed;
    private int tickCounter = 0;

    // No FireBlock reference needed — flammability is checked via block tags below.

    public SetFireGoal(Mob entity, BlockPos targetPos, double speed) {
        this.entity = entity;
        this.targetPos = targetPos;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void tick() {
        tickCounter++;

        // Keep navigating toward the building — re-issue every tick to beat Brain AI
        double dx = targetPos.getX() + 0.5 - entity.getX();
        double dz = targetPos.getZ() + 0.5 - entity.getZ();
        if (dx * dx + dz * dz > 3 * 3) {
            entity.getNavigation().moveTo(
                    targetPos.getX() + 0.5,
                    targetPos.getY(),
                    targetPos.getZ() + 0.5,
                    speed);
        }

        // Every 5 ticks (~0.25 seconds) try to set one nearby flammable block on fire
        if (tickCounter % 5 == 0) {
            tryIgniteNearby();
        }
    }

    private void tryIgniteNearby() {
        Level level = entity.level();
        BlockPos mobPos = entity.blockPosition();

        // Scan a 7×6×7 box around the mob for the first flammable block that has air above
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    BlockPos checkPos = mobPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);
                    BlockPos firePos = checkPos.above();

                    // Flammable = logs, planks, leaves, wooden stairs/slabs/fences (all standard tags).
                    // Fire is placed on the empty block above a flammable block — it then spreads naturally.
                    boolean flammable = state.is(BlockTags.LOGS)
                            || state.is(BlockTags.PLANKS)
                            || state.is(BlockTags.LEAVES)
                            || state.is(BlockTags.WOOL)
                            || state.is(BlockTags.WOODEN_STAIRS)
                            || state.is(BlockTags.WOODEN_SLABS)
                            || state.is(BlockTags.WOODEN_FENCES);
                    if (flammable && level.isEmptyBlock(firePos)) {
                        level.setBlockAndUpdate(firePos, Blocks.FIRE.defaultBlockState());
                        return; // One ignition per 5 ticks — fire spreads on its own
                    }
                }
            }
        }
    }
}
