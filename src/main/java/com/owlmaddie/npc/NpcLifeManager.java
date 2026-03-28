// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.npc;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
import com.owlmaddie.goals.GoToPositionGoal;
import com.owlmaddie.goals.StayGoal;
import com.owlmaddie.network.ServerPackets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * NpcLifeManager adds two systems that make mobs feel more alive:
 *
 * 1. Random mob-initiated interactions — when a mob with an existing character
 *    sheet is within 10 blocks of a player AND roughly facing them, there is a 5%
 *    chance per check interval that the mob starts a conversation unprompted.
 *    Checks run every 200 ticks (~10 seconds). Each mob has a 3-minute cooldown
 *    so it does not spam the player.
 *
 * 2. Conversation overhearing — when a player speaks to a mob, any other mob
 *    within 16 blocks with a character sheet has a chance to react. Base chance
 *    is 15%; if the message sounds angry (threatening words) that jumps to 60%
 *    so arguments attract attention naturally. Only one bystander reacts per
 *    exchange to keep API costs under control.
 *
 * 3. Mob-to-mob casual chat — pairs of mobs with character sheets within 6 blocks
 *    of each other have a small chance (~4%) every 30 seconds to exchange a brief
 *    comment. Each mob in the pair gets an independent trigger so both speak.
 *    Each mob has an 8-minute cooldown to keep usage low.
 */
public class NpcLifeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("creaturevoice");
    private static final Random RANDOM = new Random();

    // ── Initiative (mob-starts-conversation) ─────────────────────────────────
    private static final int   TICK_INTERVAL_TICKS     = 200;   // check every ~10 seconds
    private static final float INITIATIVE_RANGE_BLOCKS = 12.0f; // within ~12 blocks
    private static final float INITIATIVE_CHANCE        = 0.07f; // 7% per check
    private static final long  INITIATIVE_COOLDOWN_MS   = 2 * 60 * 1000L; // 2 min per mob
    private static final double FRONTAL_ARC_DOT         = 0.0;  // ~90° forward arc (anything in front)

    // ── Overhearing ───────────────────────────────────────────────────────────
    private static final float OVERHEAR_RANGE_BLOCKS    = 32.0f; // 32 blocks — realistic earshot
    private static final float OVERHEAR_BASE_CHANCE     = 0.06f; // 6% normally — occasional, not constant
    private static final float OVERHEAR_ANGRY_CHANCE    = 0.18f; // 18% if message is angry — arguments attract attention

    // Words that indicate an aggressive or threatening message
    private static final String[] ANGRY_WORDS = {
        "kill", "attack", "die", "dead", "hate", "fight", "destroy",
        "hurt", "enemy", "threat", "war", "murder", "burn"
    };

    // ── Mob-to-mob casual chat ────────────────────────────────────────────────
    private static final int   MOB_CHAT_TICK_INTERVAL  = 600;   // check every ~30 seconds
    private static final float MOB_CHAT_RANGE_BLOCKS    = 8.0f;  // 8 blocks — close enough to chat
    private static final float MOB_CHAT_CHANCE          = 0.08f; // 8% per check
    private static final long  MOB_CHAT_COOLDOWN_MS     = 6 * 60 * 1000L; // 6 min per mob

    // Per-mob cooldown maps: UUID → last event timestamp (ms)
    private static final Map<UUID, Long> lastInitiativeTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastMobChatTime    = new ConcurrentHashMap<>();

    // Per-victim cooldown: prevents consecutive swings on the SAME mob from each triggering
    // a witness reaction (the break() only stops duplicates within one checkWitnesses call).
    private static final Map<UUID, Long> lastWitnessReactionTime = new ConcurrentHashMap<>();
    private static final long WITNESS_REACTION_COOLDOWN_MS = 5_000L;

    // Per-witness cooldown: prevents the SAME NPC from reacting to multiple different victims
    // in quick succession (e.g. slaying 5 zombies in a row near the same friendly NPC).
    // Without this, each zombie triggers a separate witness reaction on the NPC, interrupting
    // it before it can finish responding.
    private static final Map<UUID, Long> lastWitnessSpokeTime = new ConcurrentHashMap<>();
    private static final long WITNESS_SPOKE_COOLDOWN_MS = 5_000L;

    // ── Delayed overhear queue ────────────────────────────────────────────────
    // Overhear reactions fire AFTER the target mob finishes responding, so the
    // bystander has full context (both sides of the exchange) before reacting.
    // Keyed by the target mob's UUID. Cleared once fired (or when the mob is gone).
    private static final Map<UUID, PendingOverhear> pendingOverhears = new ConcurrentHashMap<>();

    /** Holds the context needed to fire an overhear reaction after the LLM response arrives. */
    private static class PendingOverhear {
        final ServerLevel level;
        final ServerPlayer speaker;
        final Mob targetMob;
        final String message;

        PendingOverhear(ServerLevel level, ServerPlayer speaker, Mob targetMob, String message) {
            this.level     = level;
            this.speaker   = speaker;
            this.targetMob = targetMob;
            this.message   = message;
        }
    }

    /**
     * Wraps a PendingOverhear with a wall-clock time at which it should fire.
     * This gives the primary mob's TTS a few seconds to play before a bystander
     * chips in, so conversations don't sound like everyone talking at once.
     */
    private static class DelayedOverhear {
        final long fireAtMs;
        final PendingOverhear data;

        DelayedOverhear(long fireAtMs, PendingOverhear data) {
            this.fireAtMs = fireAtMs;
            this.data     = data;
        }
    }

    // Overhears that are waiting for their delay to expire before actually firing.
    // Processed in onServerTick so the game loop drives them naturally.
    private static final ConcurrentLinkedQueue<DelayedOverhear> delayedOverhears
            = new ConcurrentLinkedQueue<>();

    // How long (in ms) to wait after the target mob's reply arrives before a
    // bystander reacts — lets the primary TTS finish playing first.
    private static final long OVERHEAR_DELAY_MS = 4000L;

    // ── NPC-to-NPC speech reaction (7.15) ────────────────────────────────────
    // When mob A speaks aloud, broadcastNpcSpeech adds the speech to nearby mob B's
    // recentlyHeard list AND queues a DelayedNpcReaction so mob B can actually reply.
    // Without this trigger, recentlyHeard is passive context — mob B would never react.
    //
    // Delay: 3 seconds — lets the speaker's TTS finish before the listener chips in.
    // Cooldown: 60 seconds per listener — prevents rapid reaction chains.
    private static final long NPC_REACTION_DELAY_MS    = 5_000L;
    private static final long NPC_REACTION_COOLDOWN_MS = 120_000L; // 2 min — prevents rapid chatter
    private static final Map<UUID, Long> lastNpcReactionTime = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<DelayedNpcReaction> delayedNpcReactions
            = new ConcurrentLinkedQueue<>();

    /** Context needed to fire an NPC's delayed reaction to hearing another NPC speak. */
    private static class DelayedNpcReaction {
        final long fireAtMs;
        final Mob listener;
        final String speakerName;
        // What was actually said — embedded directly into the trigger so the LLM doesn't
        // need to look it up via recently_heard_nearby (which was unreliable in practice).
        final String spokenText;
        final ServerLevel level;

        DelayedNpcReaction(long fireAtMs, Mob listener, String speakerName,
                           String spokenText, ServerLevel level) {
            this.fireAtMs    = fireAtMs;
            this.listener    = listener;
            this.speakerName = speakerName;
            this.spokenText  = spokenText;
            this.level       = level;
        }
    }

    // ── Recent nearby events (attacks, deaths) ────────────────────────────────
    // Stored globally and filtered by proximity when building each mob's context.
    // Events expire after 60 seconds so they don't linger forever.
    private static final long EVENT_EXPIRY_MS    = 60_000L;
    private static final double EVENT_RANGE_BLOCKS = 32.0;

    /** A thing that happened in the world that nearby mobs may know about. */
    private static class NearbyEvent {
        final long   timestampMs;
        final double x, y, z;
        final String description;

        NearbyEvent(double x, double y, double z, String description) {
            this.timestampMs = System.currentTimeMillis();
            this.x           = x;
            this.y           = y;
            this.z           = z;
            this.description = description;
        }
    }

    // Thread-safe queue of recent world events that mobs can reference in conversation.
    private static final ConcurrentLinkedQueue<NearbyEvent> recentEventLog
            = new ConcurrentLinkedQueue<>();

    /**
     * Record something that just happened (an attack, a death, etc.) so nearby mobs
     * can reference it in their next conversation. Called from checkWitnesses and
     * checkDeathWitnesses so the event is captured regardless of whether a mob reacts.
     */
    public static void recordEvent(double x, double y, double z, String description) {
        recentEventLog.add(new NearbyEvent(x, y, z, description));
    }

    /**
     * Returns a semicolon-separated summary of recent events within EVENT_RANGE_BLOCKS
     * of the given mob — e.g. "Player809 attacked Jackson; Delilah was killed nearby".
     * Called from EntityChatData.getPlayerContext so every conversation has this context.
     */
    public static String getRecentEventsContext(Mob entity) {
        long now = System.currentTimeMillis();
        // Prune expired events while we scan
        recentEventLog.removeIf(e -> now - e.timestampMs > EVENT_EXPIRY_MS);

        List<String> relevant = new ArrayList<>();
        for (NearbyEvent event : recentEventLog) {
            double dx = event.x - entity.getX();
            double dy = event.y - entity.getY();
            double dz = event.z - entity.getZ();
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= EVENT_RANGE_BLOCKS) {
                relevant.add(event.description);
            }
        }
        return String.join("; ", relevant);
    }

    private static int tickAccumulator    = 0;
    private static int mobChatAccumulator = 0;

    // ── Server tick entry point ───────────────────────────────────────────────

    /**
     * Called every server tick from ModInit. Runs the initiative checks every
     * TICK_INTERVAL_TICKS ticks to avoid checking too often.
     */
    public static void onServerTick(MinecraftServer server) {
        // Apply any goal add/remove ops that were queued during this tick's entity AI.
        // Must run first — entity ticking is done, so it's safe to touch GoalSelector.
        com.owlmaddie.goals.EntityBehaviorManager.drainPendingOps();

        // Process delayed overhear reactions — fire at most ONE per tick.
        // Processing all expired entries in the same tick could cause several NPCs to
        // react simultaneously, which is exactly the pile-on effect we want to avoid.
        long now = System.currentTimeMillis();
        java.util.Iterator<DelayedOverhear> ohIter = delayedOverhears.iterator();
        while (ohIter.hasNext()) {
            DelayedOverhear d = ohIter.next();
            if (now >= d.fireAtMs) {
                ohIter.remove();
                checkOverhearing(d.data.level, d.data.speaker, d.data.targetMob, d.data.message);
                break; // one overhear reaction per tick maximum — prevents simultaneous NPC pile-ons
            }
        }

        // Process delayed NPC-to-NPC speech reactions — fire at most ONE per tick.
        // Prevents multiple mobs from all chiming in on the same speech simultaneously.
        java.util.Iterator<DelayedNpcReaction> nrIter = delayedNpcReactions.iterator();
        while (nrIter.hasNext()) {
            DelayedNpcReaction r = nrIter.next();
            if (now >= r.fireAtMs) {
                nrIter.remove();
                fireNpcSpeechReaction(r);
                break; // one NPC reaction per tick maximum
            }
        }

        // Force-stop navigation for any mob under a stay command.
        // This runs at END_SERVER_TICK — AFTER Brain.tick() has executed for villagers
        // and other Brain-based mobs. StayGoal.tick() alone isn't enough because
        // customServerAiStep() (where Brain.tick() runs) is called AFTER goalSelector.tick(),
        // meaning the Brain re-issues its WalkTarget in the same tick that StayGoal cancelled it.
        // Running here ensures the stop wins every tick, no matter the AI type.
        if (!StayGoal.STAYING_MOBS.isEmpty()) {
            StayGoal.STAYING_MOBS.entrySet().removeIf(entry -> {
                Mob mob = entry.getValue();
                if (!mob.isAlive()) return true; // prune dead mobs from the map
                mob.getNavigation().stop();
                // Erase the Brain's walk target so villagers don't immediately re-path
                mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                return false;
            });
        }

        // Re-issue moveTo for any mob currently under GoToPositionGoal.
        // Same problem as StayGoal: Brain.tick() runs AFTER goalSelector.tick() and
        // overwrites our navigation path. By re-issuing moveTo here (END_SERVER_TICK),
        // we win the last word every tick, so villagers and other Brain mobs actually
        // walk toward the target building instead of standing still.
        if (!GoToPositionGoal.GOTO_MOBS.isEmpty()) {
            GoToPositionGoal.GOTO_MOBS.entrySet().removeIf(entry -> {
                GoToPositionGoal goal = entry.getValue();
                Mob mob = goal.getEntity();
                if (!mob.isAlive()) return true; // prune dead mobs from map
                // Erase Brain's walk target AND panic memories so villagers walk to
                // their destination instead of fleeing from nearby zombies.
                mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
                mob.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
                mob.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
                mob.getNavigation().moveTo(
                        goal.getTargetPos().getX() + 0.5,
                        goal.getTargetPos().getY(),
                        goal.getTargetPos().getZ() + 0.5,
                        goal.getSpeed());
                return false;
            });
        }

        // Re-issue flee navigation for any mob under FleePlayerGoal.
        // Same Brain-override problem as GoToPositionGoal: Brain.tick() runs after
        // goalSelector.tick() and can clear the navigation path, causing the goal
        // to pick a brand-new random flee direction every tick (spinning in circles).
        // Re-issuing the CACHED flee target here gives us the last word each tick.
        if (!com.owlmaddie.goals.FleePlayerGoal.FLEE_MOBS.isEmpty()) {
            com.owlmaddie.goals.FleePlayerGoal.FLEE_MOBS.entrySet().removeIf(entry -> {
                com.owlmaddie.goals.FleePlayerGoal goal = entry.getValue();
                Mob mob = goal.getEntity();
                if (!mob.isAlive()) return true; // prune dead mobs from map
                mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                net.minecraft.world.phys.Vec3 fleeTarget = goal.getFleeTarget();
                if (fleeTarget != null) {
                    mob.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, goal.getSpeed());
                }
                return false;
            });
        }

        // Re-issue pursuit navigation for any mob under AttackPlayerGoal (ATTACK_NPC).
        // Brain.tick() runs after goalSelector.tick() and clears the navigation path for
        // villagers, so the attacker looks at the target but never walks toward them.
        // Re-issuing moveTo here (END_SERVER_TICK) gives us the last word every tick.
        if (!com.owlmaddie.goals.AttackPlayerGoal.ATTACK_MOBS.isEmpty()) {
            com.owlmaddie.goals.AttackPlayerGoal.ATTACK_MOBS.entrySet().removeIf(entry -> {
                com.owlmaddie.goals.AttackPlayerGoal goal = entry.getValue();
                Mob mob = goal.getAttacker();
                net.minecraft.world.entity.LivingEntity target = goal.getTarget();
                if (!mob.isAlive() || target == null || !target.isAlive()) return true; // prune finished combats
                mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
                mob.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
                mob.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
                mob.getNavigation().moveTo(target, goal.getSpeed());
                return false;
            });
        }

        // Re-issue navigation for any mob under SetFireGoal (arson behavior).
        // Same Brain-override pattern: Brain.tick() clears navigation, causing
        // the mob to spin in circles instead of walking toward the target building.
        if (!com.owlmaddie.goals.SetFireGoal.FIRE_MOBS.isEmpty()) {
            com.owlmaddie.goals.SetFireGoal.FIRE_MOBS.entrySet().removeIf(entry -> {
                com.owlmaddie.goals.SetFireGoal goal = entry.getValue();
                Mob mob = goal.getEntity();
                if (!mob.isAlive()) return true; // prune dead mobs from map
                // Erase panic-related memories so villagers don't flee from nearby zombies
                // instead of walking to their arson target. Without this, Brain.tick() sees
                // NEAREST_HOSTILE → triggers panic → sets flee WALK_TARGET → mob runs in
                // circles instead of heading to the building.
                mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                mob.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
                mob.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
                mob.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
                mob.getNavigation().moveTo(
                        goal.getTargetPos().getX() + 0.5,
                        goal.getTargetPos().getY(),
                        goal.getTargetPos().getZ() + 0.5,
                        goal.getSpeed());
                return false;
            });
        }

        // Initiative checks: every ~10 seconds
        tickAccumulator++;
        if (tickAccumulator >= TICK_INTERVAL_TICKS) {
            tickAccumulator = 0;
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    checkInitiatives(level, player);
                }
            }
        }

        // Mob-to-mob chat checks: every ~30 seconds
        mobChatAccumulator++;
        if (mobChatAccumulator >= MOB_CHAT_TICK_INTERVAL) {
            mobChatAccumulator = 0;
            for (ServerLevel level : server.getAllLevels()) {
                checkMobToMobChats(level);
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

        // Don't start a new initiative while any nearby mob is already generating
        // a reply — prevents pile-ons that produce overlapping speech.
        // Also don't fire if the player is actively conversing with a nearby mob
        // (player spoke within the last 15 seconds) — that interrupts the conversation.
        long initNow = System.currentTimeMillis();
        boolean anyBusy = nearbyMobs.stream().anyMatch(m -> {
            EntityChatData cd = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(m.getStringUUID());
            if (cd.status == ChatDataManager.ChatStatus.PENDING) return true;
            if (initNow - cd.lastPlayerChatTime < 15_000L) return true;
            return false;
        });
        if (anyBusy) return;

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
     * Nearby NPC names are appended so the mob can reference them naturally.
     */
    private static String buildInitiativeTrigger(Mob mob, ServerPlayer player) {
        String nearbyContext = buildNearbyMobContext(
                (ServerLevel) mob.level(), mob);

        // If the player is holding something interesting, reference it
        var heldItem = player.getMainHandItem();
        if (!heldItem.isEmpty()) {
            return "<notices you are holding " + heldItem.getHoverName().getString()
                    + nearbyContext + ">";
        }
        // Otherwise pick a generic approach trigger
        String[] genericTriggers = {
            "<notices you nearby",
            "<glances over at you",
            "<spots you and perks up",
            "<turns to look at you"
        };
        return genericTriggers[RANDOM.nextInt(genericTriggers.length)] + nearbyContext + ">";
    }

    /**
     * Returns a short context string listing nearby mobs that have character sheets,
     * e.g. " [Nearby NPCs: Fibonacci, Hemlock Whisper]". Returns empty string if none.
     * This is appended to trigger messages so the LLM knows who else is around and
     * can have mobs reference each other by name.
     */
    private static String buildNearbyMobContext(ServerLevel level, Mob mob) {
        AABB searchBox = mob.getBoundingBox().inflate(16.0);
        List<Mob> nearby = level.getEntitiesOfClass(Mob.class, searchBox);
        List<String> names = new ArrayList<>();
        for (Mob other : nearby) {
            if (other.getUUID().equals(mob.getUUID())) continue;
            if (other.distanceTo(mob) > 16.0f) continue;
            EntityChatData data = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(other.getStringUUID());
            if (!data.characterSheet.isEmpty()) {
                names.add(extractMobName(data, other));
            }
        }
        if (names.isEmpty()) return "";
        return " [Nearby NPCs: " + String.join(", ", names) + "]";
    }

    // ── Mob-to-mob casual chat ────────────────────────────────────────────────

    /**
     * Scans around each player for pairs of mobs with character sheets that are
     * close together. If a pair is found and the chance rolls, both mobs get an
     * independent trigger so they each say something — creating a brief exchange.
     * Each mob has an 8-minute personal cooldown to keep API usage low.
     */
    private static void checkMobToMobChats(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        long now = System.currentTimeMillis();

        // Search around each player so we only look at loaded, nearby areas
        for (ServerPlayer player : players) {
            AABB searchBox = player.getBoundingBox().inflate(32.0);
            List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, searchBox);

            for (Mob mobA : nearbyMobs) {
                // Must have a character sheet
                EntityChatData dataA = ChatDataManager.getServerInstance()
                        .getOrCreateChatData(mobA.getStringUUID());
                if (dataA.characterSheet.isEmpty()) continue;
                if (dataA.status == ChatDataManager.ChatStatus.PENDING) continue;

                // Per-mob cooldown
                Long lastA = lastMobChatTime.get(mobA.getUUID());
                if (lastA != null && now - lastA < MOB_CHAT_COOLDOWN_MS) continue;

                // Roll chance — most checks produce nothing
                if (RANDOM.nextFloat() > MOB_CHAT_CHANCE) continue;

                // Find a nearby mob to pair with
                AABB chatBox = mobA.getBoundingBox().inflate(MOB_CHAT_RANGE_BLOCKS);
                List<Mob> candidates = level.getEntitiesOfClass(Mob.class, chatBox);

                for (Mob mobB : candidates) {
                    if (mobB.getUUID().equals(mobA.getUUID())) continue;
                    if (mobB.distanceTo(mobA) > MOB_CHAT_RANGE_BLOCKS) continue;

                    EntityChatData dataB = ChatDataManager.getServerInstance()
                            .getOrCreateChatData(mobB.getStringUUID());
                    if (dataB.characterSheet.isEmpty()) continue;
                    if (dataB.status == ChatDataManager.ChatStatus.PENDING) continue;

                    Long lastB = lastMobChatTime.get(mobB.getUUID());
                    if (lastB != null && now - lastB < MOB_CHAT_COOLDOWN_MS) continue;

                    // Stamp mobA's cooldown — it is the one initiating right now.
                    // Do NOT stamp mobB's cooldown: mobB hasn't spoken yet and will
                    // get its own turn via the DelayedNpcReaction system (7.15).
                    lastMobChatTime.put(mobA.getUUID(), now);

                    // Use display names (from character sheet if available)
                    String nameA = extractMobName(dataA, mobA);
                    String nameB = extractMobName(dataB, mobB);

                    // Use the nearest player as context reference for the LLM
                    ServerPlayer contextPlayer = players.stream()
                            .min(Comparator.comparingDouble(p -> (double) p.distanceTo(mobA)))
                            .orElse(players.get(0));

                    // Include nearby NPC names so mobs can reference each other correctly
                    String nearbyA = buildNearbyMobContext((ServerLevel) mobA.level(), mobA);

                    // Only fire the initiator (mobA). MobB will hear mobA's reply via
                    // broadcastNpcSpeech (7.14) and can react on its next initiative check.
                    // Firing both at once caused simultaneous NPC speech which felt spammy.
                    String triggerA = "<notices " + nameB + " nearby and turns to speak to them" + nearbyA + ">";

                    LOGGER.info("Mob-to-mob chat: {} notices {} (one-way initiation)", nameA, nameB);
                    ServerPackets.generate_chat("English", dataA, contextPlayer, mobA, triggerA, true);
                    return; // one pair per check per level
                }
            }
        }
    }

    // ── Overhearing ───────────────────────────────────────────────────────────

    /**
     * Called from ServerPackets.generate_chat instead of directly running the
     * overhear check. Stores the overhear context so it can fire AFTER the target
     * mob has finished responding — giving the bystander full context (both sides
     * of the exchange) rather than reacting before the target even speaks.
     */
    public static void queueOverhear(ServerLevel level, ServerPlayer speaker,
                                     Mob targetMob, String message) {
        pendingOverhears.put(targetMob.getUUID(),
                new PendingOverhear(level, speaker, targetMob, message));
    }

    /**
     * Called from ServerPackets.BroadcastEntityMessage when the target mob's reply
     * arrives (status=DISPLAY, sender=ASSISTANT). Moves the queued overhear into the
     * delayed queue so the bystander waits for the primary TTS to finish playing
     * before reacting — no more simultaneous speech.
     */
    public static void firePendingOverhear(UUID targetMobId) {
        PendingOverhear pending = pendingOverhears.remove(targetMobId);
        if (pending != null) {
            long fireAt = System.currentTimeMillis() + OVERHEAR_DELAY_MS;
            delayedOverhears.add(new DelayedOverhear(fireAt, pending));
        }
    }

    /**
     * Internal overhear logic. Scans mobs within OVERHEAR_RANGE_BLOCKS of the
     * conversation mob. Each nearby mob with a character sheet rolls to react.
     * Only the first mob that succeeds reacts to keep API costs low.
     */
    private static void checkOverhearing(ServerLevel level, ServerPlayer speaker,
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

            // Skip messengers on a SPEAK_TO delivery — they report back via the
            // return-trip callback, not by eavesdropping on the target's reply
            if (bystanderData.onDeliveryMission) continue;

            if (RANDOM.nextFloat() > chance) continue;

            // Build rich context: include what the target mob last said so the bystander
            // can give a relevant reaction rather than a generic interruption.
            EntityChatData targetData = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(targetMob.getStringUUID());
            String targetName = extractMobName(targetData, targetMob);

            StringBuilder overheardMsg = new StringBuilder("<overhears ");
            overheardMsg.append(speaker.getDisplayName().getString())
                    .append(" saying to ").append(targetName).append(": \"")
                    .append(truncate(message, 60)).append("\"");

            // If the target mob has a recent reply, include it for context
            if (targetData.currentMessage != null && !targetData.currentMessage.isEmpty()
                    && targetData.status == ChatDataManager.ChatStatus.DISPLAY
                    && targetData.sender == ChatDataManager.ChatSender.ASSISTANT) {
                // Strip behavior tags and subtitle markers for a clean readable excerpt
                String lastReply = targetData.currentMessage
                        .replaceAll("<[^>]+>", "")            // remove <ATTACK> etc.
                        .replaceAll("\\[EN:[^\\]]*\\]?", "") // remove [EN: subtitle]
                        .replaceAll("\\*[^*]+\\*", "")       // remove *emotes*
                        .trim();
                if (!lastReply.isEmpty()) {
                    overheardMsg.append(". ").append(targetName).append(" had just replied: \"")
                            .append(truncate(lastReply, 60)).append("\"");
                }
            }
            overheardMsg.append(">");

            LOGGER.info("NPC overhear: {} reacts to conversation (angry={})", bystander.getType().toShortString(), isAngry);
            ServerPackets.generate_chat("English", bystanderData, speaker, bystander, overheardMsg.toString(), true);
            break; // Only one bystander reacts per exchange
        }
    }

    /**
     * Fires an NPC's delayed reaction to hearing another NPC speak nearby.
     * Finds the nearest player within 64 blocks to serve as the LLM context reference.
     * The mob's recentlyHeard list already has the content — the trigger just cues the reaction.
     * Skipped if the mob is dead, has no character sheet, is PENDING, or no player is nearby.
     */
    private static void fireNpcSpeechReaction(DelayedNpcReaction r) {
        if (!r.listener.isAlive()) return;

        EntityChatData data = ChatDataManager.getServerInstance()
                .getOrCreateChatData(r.listener.getStringUUID());
        if (data.characterSheet.isEmpty()) return;
        if (data.status == ChatDataManager.ChatStatus.PENDING) return;

        // Need a nearby player to serve as the LLM context reference
        ServerPlayer contextPlayer = r.level.players().stream()
                .filter(p -> p.distanceTo(r.listener) < 64.0f)
                .min(Comparator.comparingDouble(p -> (double) p.distanceTo(r.listener)))
                .orElse(null);
        if (contextPlayer == null) return;

        // Embed the spoken text directly into the trigger so the LLM has full context
        // without relying on the recently_heard_nearby field (which the HEARD RULE can
        // misinterpret as a "did you hear?" player question, causing "I didn't catch that").
        String trigger = "<overheard " + r.speakerName + " say nearby: \""
                + truncate(r.spokenText, 80) + "\">";
        LOGGER.info("NPC speech reaction: {} reacts to hearing {}",
                r.listener.getType().toShortString(), r.speakerName);
        ServerPackets.generate_chat("English", data, contextPlayer, r.listener, trigger, true);
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

    // ── Witness / defend system ───────────────────────────────────────────────

    /**
     * Called from MixinLivingEntity when a player attacks a mob. Scans for nearby mobs
     * with character sheets who can witness the attack. The witness gets a trigger
     * message describing what they saw; the LLM then decides — based on their character
     * sheet and any existing relationship with the victim — whether to react verbally,
     * flee, or attack the player. Only one witness reacts per attack event.
     */
    public static void checkWitnesses(ServerLevel level, ServerPlayer attacker, Mob victim) {
        String victimName = extractMobName(
                ChatDataManager.getServerInstance().getOrCreateChatData(victim.getStringUUID()),
                victim);
        String weapon = attacker.getMainHandItem().isEmpty()
                ? "their fists"
                : attacker.getMainHandItem().getHoverName().getString();

        // Record the event so ALL nearby mobs have it in their conversation context,
        // even if only one triggers an active witness reaction.
        recordEvent(victim.getX(), victim.getY(), victim.getZ(),
                attacker.getDisplayName().getString() + " attacked " + victimName + " with " + weapon);

        // Rate-limit witness reactions per victim: each sword swing calls checkWitnesses
        // independently, so without a cooldown every swing can trigger a different witness
        // even though break() prevents two witnesses in a single call.
        long now = System.currentTimeMillis();
        Long lastWitness = lastWitnessReactionTime.get(victim.getUUID());
        if (lastWitness != null && now - lastWitness < WITNESS_REACTION_COOLDOWN_MS) {
            return; // Too soon — still within the witness cooldown for this victim
        }

        AABB searchBox = victim.getBoundingBox().inflate(16.0); // increased from 10
        List<Mob> witnesses = level.getEntitiesOfClass(Mob.class, searchBox);

        for (Mob witness : witnesses) {
            if (witness.getUUID().equals(victim.getUUID())) continue;
            if (witness.distanceTo(victim) > 16.0f) continue;

            EntityChatData witnessData = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(witness.getStringUUID());
            if (witnessData.characterSheet.isEmpty()) continue;
            if (witnessData.status == ChatDataManager.ChatStatus.PENDING) continue;

            // Skip this witness if it reacted to something else very recently.
            // Prevents a friendly NPC from being interrupted repeatedly while the player
            // fights multiple enemies nearby (each kill would otherwise retrigger it).
            Long lastSpoke = lastWitnessSpokeTime.get(witness.getUUID());
            if (lastSpoke != null && now - lastSpoke < WITNESS_SPOKE_COOLDOWN_MS) continue;

            String trigger = "<witnesses " + attacker.getDisplayName().getString()
                    + " attacking " + victimName + " with " + weapon + ">";

            LOGGER.info("Witness reaction: {} sees attack on {}", witness.getType().toShortString(), victimName);
            lastWitnessReactionTime.put(victim.getUUID(), System.currentTimeMillis());
            lastWitnessSpokeTime.put(witness.getUUID(), now);
            ServerPackets.generate_chat("English", witnessData, attacker, witness, trigger, true);
            break; // one witness per attack keeps costs low
        }
    }

    // ── Death awareness ───────────────────────────────────────────────────────

    /**
     * Called from MixinLivingEntity.onDeath when a mob with a character sheet dies.
     * Nearby mobs with character sheets get a trigger describing what they witnessed,
     * so they can react naturally in character (mourn, cheer, flee, etc.).
     * Only one witness reacts per death to keep API costs low.
     */
    /**
     * @param killerName      Display name of whoever caused the death, or null if unknown.
     * @param killerUUID      UUID of the killer entity, or null. Used to detect self-defense:
     *                        if the witness IS the killer, they saved themselves — not "rescued".
     * @param deceasedTarget  Who the deceased was attacking at time of death, or null.
     *                        Used so a witness who WAS the target can react with gratitude
     *                        rather than fear when someone ELSE saved their life.
     */
    public static void checkDeathWitnesses(ServerLevel level, Mob deceased, String killerName,
                                           java.util.UUID killerUUID,
                                           net.minecraft.world.entity.LivingEntity deceasedTarget) {
        EntityChatData deceasedData = ChatDataManager.getServerInstance()
                .getOrCreateChatData(deceased.getStringUUID());
        String deceasedName = extractMobName(deceasedData, deceased);

        // Resolve who the deceased was attacking — prefer the sheet name for named NPCs
        String targetName = null;
        java.util.UUID targetUUID = null;
        if (deceasedTarget != null) {
            if (deceasedTarget instanceof Mob targetMob) {
                EntityChatData targetData = ChatDataManager.getServerInstance()
                        .getOrCreateChatData(targetMob.getStringUUID());
                targetName = extractMobName(targetData, targetMob);
            } else {
                targetName = deceasedTarget.getDisplayName().getString();
            }
            targetUUID = deceasedTarget.getUUID();
        }

        // Build a descriptive death string — include attacker context when available
        String deathDescription;
        if (killerName != null && !killerName.isBlank()) {
            if (targetName != null && !targetName.isBlank()) {
                deathDescription = deceasedName + " was killed by " + killerName
                        + " while attacking " + targetName;
            } else {
                deathDescription = deceasedName + " was killed by " + killerName;
            }
        } else {
            deathDescription = deceasedName + " was killed nearby";
        }

        // Record so ALL nearby mobs have this event in their next conversation context
        // for the next 60 seconds, even if they're too far away to be active witnesses.
        recordEvent(deceased.getX(), deceased.getY(), deceased.getZ(), deathDescription);

        AABB searchBox = deceased.getBoundingBox().inflate(32.0);
        List<Mob> witnesses = level.getEntitiesOfClass(Mob.class, searchBox);

        // Need at least one player nearby to use as context for the LLM call
        List<net.minecraft.server.level.ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        boolean activeReactionFired = false;
        for (Mob witness : witnesses) {
            if (witness.getUUID().equals(deceased.getUUID())) continue;
            if (witness.distanceTo(deceased) > 32.0f) continue;

            EntityChatData witnessData = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(witness.getStringUUID());

            // Personalise the stored death memory for the mob that was being attacked:
            // they remember "X was killed by Y while attacking me" rather than "while attacking Z".
            // EXCEPT when the witness IS the killer — that's self-defense, not a rescue.
            // Dom killing a spider that was attacking Dom is NOT "someone saved Dom".
            boolean witnessWasTarget = targetUUID != null
                    && witness.getUUID().equals(targetUUID)
                    && (killerUUID == null || !witness.getUUID().equals(killerUUID));
            String witnessMemory = witnessWasTarget
                    ? deceasedName + " was killed by " + killerName + " while attacking me"
                    : deathDescription;

            // Always store the permanent death memory — even for mobs that have not been
            // spoken to yet. When the player first talks to them the memory will already
            // be there, so they can reference the death in that first conversation.
            if (!witnessData.witnessedDeaths.contains(witnessMemory)) {
                witnessData.witnessedDeaths.add(witnessMemory);
            }

            // Active in-character reactions need a character sheet. Skip mobs that
            // haven't been generated yet — they'll still have the permanent memory above.
            if (witnessData.characterSheet.isEmpty()) continue;

            // Skip witnesses who are in an active player conversation — don't interrupt
            // ongoing interactions (e.g. SET_FIRE navigation) with death reactions.
            // The death memory is already stored above, so they'll know about it later.
            long timeSinceChat = System.currentTimeMillis() - witnessData.lastPlayerChatTime;
            if (witnessData.lastPlayerChatTime > 0 && timeSinceChat < 15_000L) continue;

            // Fire one active voice/text reaction (first eligible witness only).
            if (!activeReactionFired && witnessData.status != ChatDataManager.ChatStatus.PENDING) {
                net.minecraft.server.level.ServerPlayer contextPlayer = players.stream()
                        .min(Comparator.comparingDouble(p -> (double) p.distanceTo(witness)))
                        .orElse(players.get(0));

                // The mob that was being attacked gets a personalised trigger so it can
                // react with gratitude ("you saved my life") rather than fear.
                String trigger;
                if (killerName != null && !killerName.isBlank()) {
                    if (witnessWasTarget) {
                        // The witness was the victim's attack target — they were just saved.
                        trigger = "<witnesses " + deceasedName + " being killed by "
                                + killerName + " while attacking you — you were just saved>";
                    } else {
                        // Plain death — do NOT include "while attacking X" when X is the player,
                        // because that phrasing makes the LLM read the kill as justified self-defence
                        // and react positively even when the witness was actively telling the player
                        // to stop (causing the "Oh my goodness, you actually did it!" bug).
                        trigger = "<witnesses " + deceasedName + " being killed by " + killerName + ">";
                    }
                } else {
                    trigger = "<witnesses " + deceasedName + " die nearby>";
                }

                boolean selfDefense = killerUUID != null && witness.getUUID().equals(killerUUID);
                LOGGER.info("Death witness: {} sees {} die (killer: {}, target: {}, savedWitness: {}, selfDefense: {})",
                        witness.getType().toShortString(), deceasedName,
                        killerName != null ? killerName : "unknown",
                        targetName != null ? targetName : "none",
                        witnessWasTarget, selfDefense);
                ServerPackets.generate_chat("English", witnessData, contextPlayer, witness, trigger, true);
                activeReactionFired = true; // one active reaction per death keeps costs low
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // ── NPC hears NPCs speak (7.14) ───────────────────────────────────────────

    /**
     * Called from ServerPackets.BroadcastEntityMessage when a mob's reply is committed
     * for display. Adds "[SpeakerName] said: \"...\"" to the recentlyHeard list of every
     * nearby NPC with a character sheet so they can truthfully answer "did you hear what
     * X just said?" in their next conversation.
     *
     * @param speaker      the mob that just finished speaking
     * @param speakerName  the character-sheet name of the speaker
     * @param message      the clean display text (behavior tags already stripped)
     * @param level        the server level to search for bystanders
     */
    /**
     * @param allowReactions  when false, nearby NPCs still hear the speech (recentlyHeard is
     *                        updated) but no DelayedNpcReaction is queued. This prevents infinite
     *                        chain reactions: NPC A speaks → B reacts → A reacts → B reacts → ...
     *                        Only player-initiated speech and mob-to-mob initiative should set
     *                        this to true. NPC speech reactions and overhear responses set it false.
     */
    public static void broadcastNpcSpeech(Mob speaker, String speakerName,
                                          String message, ServerLevel level,
                                          boolean allowReactions) {
        AABB searchBox = speaker.getBoundingBox().inflate(OVERHEAR_RANGE_BLOCKS);
        // No explicit quote marks — using them caused LLMs to read the entry back verbatim.
        // A plain description lets the NPC react naturally rather than reciting a transcript.
        String entry = speakerName + " said nearby: " + truncate(message, 60);

        for (Mob other : level.getEntitiesOfClass(Mob.class, searchBox)) {
            if (other.getUUID().equals(speaker.getUUID())) continue;
            if (other.distanceTo(speaker) > OVERHEAR_RANGE_BLOCKS) continue;

            EntityChatData data = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(other.getStringUUID());
            if (data.characterSheet.isEmpty()) continue; // only NPCs with sheets can "listen"

            if (data.recentlyHeard == null) data.recentlyHeard = new ArrayList<>();
            data.recentlyHeard.add(entry);
            // Keep list bounded to the 5 most recent entries
            while (data.recentlyHeard.size() > 5) data.recentlyHeard.remove(0);

            // Only queue active reactions for player-initiated speech and mob-to-mob initiative.
            // NPC reactions to other NPC speech should NOT trigger further reactions — that causes
            // infinite chain reactions where every NPC keeps responding to every other NPC.
            if (!allowReactions) continue;

            // Skip messengers on a SPEAK_TO delivery — they report back via callback, not eavesdrop
            if (data.onDeliveryMission) continue;

            long broadcastNow = System.currentTimeMillis();
            Long lastReact = lastNpcReactionTime.get(other.getUUID());
            if (lastReact == null || broadcastNow - lastReact >= NPC_REACTION_COOLDOWN_MS) {
                lastNpcReactionTime.put(other.getUUID(), broadcastNow);
                delayedNpcReactions.add(new DelayedNpcReaction(
                        broadcastNow + NPC_REACTION_DELAY_MS, other, speakerName, message, level));
            }
        }
    }

    /**
     * Public wrapper so other classes (e.g. EntityChatData) can look up a mob's
     * character-sheet name without duplicating the parsing logic.
     */
    public static String extractMobNamePublic(EntityChatData chatData, Mob mob) {
        return extractMobName(chatData, mob);
    }

    /**
     * Tries to extract the mob's name from the first "Name:" line of their character
     * sheet. Falls back to custom name tag, then to type description.
     */
    private static String extractMobName(EntityChatData chatData, Mob mob) {
        if (!chatData.characterSheet.isEmpty()) {
            for (String line : chatData.characterSheet.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Name:")) {
                    String name = trimmed.substring("Name:".length()).trim();
                    // Strip trailing comma or period if the sheet has "Name: Kermit,"
                    name = name.replaceAll("[,.]$", "").trim();
                    if (!name.isEmpty()) return name;
                }
            }
        }
        if (mob.getCustomName() != null) return mob.getCustomName().getString();
        return mob.getType().getDescription().getString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
