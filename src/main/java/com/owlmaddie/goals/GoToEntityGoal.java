// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.goals;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

/**
 * The {@code GoToEntityGoal} makes a mob navigate toward any LivingEntity (another mob
 * or a ServerPlayer). Used by the SPEAK_TO behavior — the messenger NPC walks to a target
 * and fires the {@code onArrival} runnable on arrival (within 3.5 blocks).
 *
 * The target can be a Mob (delivery trip to target NPC) or a ServerPlayer (return trip
 * back to the player after delivering). Navigation is re-issued every tick so the
 * messenger keeps tracking moving targets.
 *
 * NOTE: This goal deliberately does NOT add a StayGoal on arrival. The caller controls
 * whether the messenger stays or continues moving — the onArrival callback should add
 * a StayGoal itself if needed, or chain a second GoToEntityGoal for the return trip.
 */
public class GoToEntityGoal extends Goal {
    private static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");

    private final Mob          messenger;   // NPC doing the walking (always a Mob)
    private final LivingEntity target;      // who to walk toward (Mob or ServerPlayer)
    private final double       speed;       // movement speed
    private final Runnable     onArrival;   // fired once when within 3.5 blocks

    private boolean delivered = false; // true after the callback has fired

    public GoToEntityGoal(Mob messenger, LivingEntity target, double speed, Runnable onArrival) {
        this.messenger = messenger;
        this.target    = target;
        this.speed     = speed;
        this.onArrival = onArrival;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !delivered && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return !delivered && target.isAlive();
    }

    @Override
    public void tick() {
        double dist = messenger.distanceTo(target);

        if (dist < 3.5) {
            // Arrived — stop navigation, make both entities look at each other, fire callback
            delivered = true;
            messenger.getNavigation().stop();

            // Messenger always looks at the target
            messenger.getLookControl().setLookAt(
                    target.getX(), target.getEyeY(), target.getZ(), 30.0f, 30.0f);

            // Target looks back only if it is a Mob (has a LookControl); players turn themselves
            if (target instanceof Mob targetMob) {
                targetMob.getLookControl().setLookAt(
                        messenger.getX(), messenger.getEyeY(), messenger.getZ(), 30.0f, 30.0f);
            }

            if (onArrival != null) {
                onArrival.run();
            }
            // Callers are responsible for adding a StayGoal if they want the messenger
            // to remain in place — see onReturn callback in EntityChatData SPEAK_TO handler.
        } else {
            // Re-issue moveTo every tick so Brain-based mobs keep tracking the target
            messenger.getNavigation().moveTo(
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    speed);
        }
    }
}
