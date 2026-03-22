// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.npc;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NpcLifeManager adds two systems that make mobs feel more alive:
 *
 * 1. Random mob-initiated interactions — when a mob with an existing character
 *    sheet is within 4 blocks of a player AND looking at them, there is a 5%
 *    chance per check interval that the mob starts a conversation unprompted.
 *    Checks run every 200 ticks (~10 seconds). Each mob has a 3-minute cooldown
 *    so it does not spam the player.
 *
 * 2. Conversation overhearing — when a player speaks to a mob, any other mob
 *    within 8 blocks with a character sheet has a chance to react. Base chance
 *    is 15%; if the message sounds angry (threatening words) that jumps to 60%
 *    so arguments attract attention naturally. Only one bystander reacts per
 *    exchange to keep API costs under control.
 */
public class NpcLifeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("creaturevoice");
    private static final Random RANDOM = new Random();

    // ── Initiative (mob-starts-conversation) ─────────────────────────────────
    private static final int   TICK_INTERVAL_TICKS     = 200;   // check every ~10 seconds
    private static final float INITIATIVE_RANGE_BLOCKS = 4.0f;  // must be very close
    private static final float INITIATIVE_CHANCE        = 0.05f; // 5% per check
    private static final long  INITIATIVE_COOLDOWN_MS   = 3 * 60 * 1000L; // 3 min per mob
    private static final double FRONTAL_ARC_DOT         = 0.5;  // ~60° forward arc

    // ── Overhearing ───────────────────────────────────────────────────────────
    private static final float OVERHEAR_RANGE_BLOCKS    = 8.0f;
    private static final float OVERHEAR_BASE_CHANCE     = 0.15f; // 15% normally
    private static final float OVERHEAR_ANGRY_CHANCE    = 0.60f; // 60% if message is angry

    // Words that indicate an aggressive or threatening message
    private static final String[] ANGRY_WORDS = {
        "kill", "attack", "die", "dead", "hate", "fight", "destroy",
        "hurt", "enemy", "threat", "war", "murder", "burn"
    };

    // Per-mob cooldown: maps mob UUID → timestamp of last initiative (ms)
    private static final Map<UUID, Long> lastInitiativeTime = new ConcurrentHashMap<>();

    private static int tickAccumulator = 0;

    // ── Server tick entry point ───────────────────────────────────────────────

    /**
     * Called every server tick from ModInit. Runs the initiative checks every
     * TICK_INTERVAL_TICKS ticks to avoid checking too often.
     */
    public static void onServerTick(MinecraftServer server) {
        tickAccumulator++;
        if (tickAccumulator < TICK_INTERVAL_TICKS) return;
        tickAccumulator = 0;

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                checkInitiatives(level, player);
            }
        }
    }

    // ── Initiative checks ─────────────────────────────────────────────────────

    /**
     * For each player, find nearby mobs that have met the player before (have a
     * character sheet), are looking at them, and roll a 5% chance to start talking.
     * At most one mob initiates per player per check so multiple mobs don't pile on.
     */
    private static void checkInitiatives(ServerLevel level, ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(INITIATIVE_RANGE_BLOCKS);
        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, searchBox);

        for (Mob mob : nearbyMobs) {
            // Must be within range (inflate uses axis-aligned box, re-check sphere dist)
            if (mob.distanceTo(player) > INITIATIVE_RANGE_BLOCKS) continue;

            // Must have been spoken to before (character sheet exists)
            EntityChatData chatData = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(mob.getStringUUID());
            if (chatData.characterSheet.isEmpty()) continue;

            // Don't interrupt an in-progress response
            if (chatData.status == ChatDataManager.ChatStatus.PENDING) continue;

            // Per-mob cooldown check
            long now = System.currentTimeMillis();
            Long last = lastInitiativeTime.get(mob.getUUID());
            if (last != null && now - last < INITIATIVE_COOLDOWN_MS) continue;

            // Mob must be looking at the player (line of sight + frontal arc)
            if (!isMobLookingAtPlayer(mob, player)) continue;

            // 5% chance
            if (RANDOM.nextFloat() > INITIATIVE_CHANCE) continue;

            // All conditions met — fire the initiative
            lastInitiativeTime.put(mob.getUUID(), now);
            String trigger = buildInitiativeTrigger(mob, player);
            LOGGER.info("NPC initiative: {} starts conversation with {} via \"{}\"",
                    mob.getType().toShortString(), player.getDisplayName().getString(), trigger);

            ServerPackets.generate_chat("English", chatData, player, mob, trigger, true);
            break; // Only one mob per player per check interval
        }
    }

    /**
     * Returns true if the mob has line of sight to the player and the player is
     * within roughly 60 degrees of the mob's forward-facing direction.
     */
    private static boolean isMobLookingAtPlayer(Mob mob, ServerPlayer player) {
        if (!mob.hasLineOfSight(player)) return false;
        Vec3 lookDir  = mob.getLookAngle();
        Vec3 toPlayer = player.position().subtract(mob.position()).normalize();
        return lookDir.dot(toPlayer) > FRONTAL_ARC_DOT;
    }

    /**
     * Builds the auto-message string that kicks off the initiative. If the
     * player is holding something visible, the mob notices that specifically.
     */
    private static String buildInitiativeTrigger(Mob mob, ServerPlayer player) {
        // If the player is holding something interesting, reference it
        var heldItem = player.getMainHandItem();
        if (!heldItem.isEmpty()) {
            return "<notices you are holding " + heldItem.getHoverName().getString() + ">";
        }
        // Otherwise pick a generic approach trigger
        String[] genericTriggers = {
            "<notices you nearby>",
            "<glances over at you>",
            "<spots you and perks up>",
            "<turns to look at you>"
        };
        return genericTriggers[RANDOM.nextInt(genericTriggers.length)];
    }

    // ── Overhearing ───────────────────────────────────────────────────────────

    /**
     * Called from ServerPackets.generate_chat for every player-initiated message.
     * Scans mobs within 8 blocks of the conversation mob. Each nearby mob with a
     * character sheet rolls to react — chance is 15% normally, 60% if the message
     * sounds angry. Only the first mob that succeeds reacts to keep API costs low.
     */
    public static void checkOverhearing(ServerLevel level, ServerPlayer speaker,
                                        Mob targetMob, String message) {
        boolean isAngry = isAngryMessage(message);
        float chance = isAngry ? OVERHEAR_ANGRY_CHANCE : OVERHEAR_BASE_CHANCE;

        AABB searchBox = targetMob.getBoundingBox().inflate(OVERHEAR_RANGE_BLOCKS);
        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, searchBox);

        for (Mob bystander : nearbyMobs) {
            // Skip the mob being spoken to
            if (bystander.getUUID().equals(targetMob.getUUID())) continue;
            if (bystander.distanceTo(targetMob) > OVERHEAR_RANGE_BLOCKS) continue;

            // Must have been spoken to before
            EntityChatData bystanderData = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(bystander.getStringUUID());
            if (bystanderData.characterSheet.isEmpty()) continue;

            // Don't pile on if they're already responding to something
            if (bystanderData.status == ChatDataManager.ChatStatus.PENDING) continue;

            if (RANDOM.nextFloat() > chance) continue;

            // Build the overhearing context so the bystander knows what they heard
            String targetName = targetMob.getCustomName() != null
                    ? targetMob.getCustomName().getString()
                    : targetMob.getType().getDescription().getString();
            String overheardMsg = "<overhears " + speaker.getDisplayName().getString()
                    + " saying to " + targetName + ": \""
                    + truncate(message, 60) + "\">";

            LOGGER.info("NPC overhear: {} reacts to conversation (angry={})", bystander.getType().toShortString(), isAngry);
            ServerPackets.generate_chat("English", bystanderData, speaker, bystander, overheardMsg, true);
            break; // Only one bystander reacts per exchange
        }
    }

    /**
     * Returns true if the message contains words that suggest aggression or anger,
     * which should make nearby mobs more likely to react.
     */
    private static boolean isAngryMessage(String message) {
        String lower = message.toLowerCase(java.util.Locale.ENGLISH);
        for (String word : ANGRY_WORDS) {
            if (lower.contains(word)) return true;
        }
        return false;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
