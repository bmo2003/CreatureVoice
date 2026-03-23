// SPDX-FileCopyrightText: 2025 bmo2003
// SPDX-License-Identifier: GPL-3.0-or-later
package com.owlmaddie.npc;

import com.owlmaddie.chat.ChatDataManager;
import com.owlmaddie.chat.EntityChatData;
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
    private static final float INITIATIVE_RANGE_BLOCKS = 10.0f; // within ~10 blocks
    private static final float INITIATIVE_CHANCE        = 0.05f; // 5% per check
    private static final long  INITIATIVE_COOLDOWN_MS   = 3 * 60 * 1000L; // 3 min per mob
    private static final double FRONTAL_ARC_DOT         = 0.0;  // ~90° forward arc (anything in front)

    // ── Overhearing ───────────────────────────────────────────────────────────
    private static final float OVERHEAR_RANGE_BLOCKS    = 32.0f; // increased from 16
    private static final float OVERHEAR_BASE_CHANCE     = 0.05f; // 5% normally (was 15% — too frequent)
    private static final float OVERHEAR_ANGRY_CHANCE    = 0.25f; // 25% if message is angry (was 60%)

    // Words that indicate an aggressive or threatening message
    private static final String[] ANGRY_WORDS = {
        "kill", "attack", "die", "dead", "hate", "fight", "destroy",
        "hurt", "enemy", "threat", "war", "murder", "burn"
    };

    // ── Mob-to-mob casual chat ────────────────────────────────────────────────
    private static final int   MOB_CHAT_TICK_INTERVAL  = 600;   // check every ~30 seconds
    private static final float MOB_CHAT_RANGE_BLOCKS    = 6.0f;
    private static final float MOB_CHAT_CHANCE          = 0.10f; // 10% per check
    private static final long  MOB_CHAT_COOLDOWN_MS     = 8 * 60 * 1000L; // 8 min per mob

    // Per-mob cooldown maps: UUID → last event timestamp (ms)
    private static final Map<UUID, Long> lastInitiativeTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastMobChatTime    = new ConcurrentHashMap<>();

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
        // Process delayed overhear reactions — fire any that have waited long enough.
        // Running this every tick is cheap because the queue is almost always empty.
        long now = System.currentTimeMillis();
        delayedOverhears.removeIf(d -> {
            if (now >= d.fireAtMs) {
                checkOverhearing(d.data.level, d.data.speaker, d.data.targetMob, d.data.message);
                return true; // remove from queue after firing
            }
            return false;
        });

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
        boolean anyPending = nearbyMobs.stream().anyMatch(m ->
                ChatDataManager.getServerInstance()
                        .getOrCreateChatData(m.getStringUUID()).status
                        == ChatDataManager.ChatStatus.PENDING);
        if (anyPending) return;

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

                    // Found a valid pair — stamp both cooldowns and fire triggers
                    lastMobChatTime.put(mobA.getUUID(), now);
                    lastMobChatTime.put(mobB.getUUID(), now);

                    // Use display names (from character sheet if available)
                    String nameA = extractMobName(dataA, mobA);
                    String nameB = extractMobName(dataB, mobB);

                    // Use the nearest player as context reference for the LLM
                    ServerPlayer contextPlayer = players.stream()
                            .min(Comparator.comparingDouble(p -> (double) p.distanceTo(mobA)))
                            .orElse(players.get(0));

                    // Include nearby NPC names so mobs can reference each other correctly
                    String nearbyA = buildNearbyMobContext((ServerLevel) mobA.level(), mobA);
                    String nearbyB = buildNearbyMobContext((ServerLevel) mobB.level(), mobB);

                    String triggerA = "<notices " + nameB + " nearby and turns to speak to them" + nearbyA + ">";
                    String triggerB = "<" + nameA + " nearby seems to notice you and turns your way" + nearbyB + ">";

                    LOGGER.info("Mob-to-mob chat: {} speaks to {}", nameA, nameB);
                    ServerPackets.generate_chat("English", dataA, contextPlayer, mobA, triggerA, true);
                    ServerPackets.generate_chat("English", dataB, contextPlayer, mobB, triggerB, true);
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

        AABB searchBox = victim.getBoundingBox().inflate(16.0); // increased from 10
        List<Mob> witnesses = level.getEntitiesOfClass(Mob.class, searchBox);

        for (Mob witness : witnesses) {
            if (witness.getUUID().equals(victim.getUUID())) continue;
            if (witness.distanceTo(victim) > 16.0f) continue;

            EntityChatData witnessData = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(witness.getStringUUID());
            if (witnessData.characterSheet.isEmpty()) continue;
            if (witnessData.status == ChatDataManager.ChatStatus.PENDING) continue;

            String trigger = "<witnesses " + attacker.getDisplayName().getString()
                    + " attacking " + victimName + " with " + weapon + ">";

            LOGGER.info("Witness reaction: {} sees attack on {}", witness.getType().toShortString(), victimName);
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
     * @param killerName  Display name of whoever caused the death, or null if unknown.
     *                    Threaded in from MixinLivingEntity so the memory string is
     *                    richer ("killed by Player972") rather than just "killed nearby".
     */
    public static void checkDeathWitnesses(ServerLevel level, Mob deceased, String killerName) {
        EntityChatData deceasedData = ChatDataManager.getServerInstance()
                .getOrCreateChatData(deceased.getStringUUID());
        String deceasedName = extractMobName(deceasedData, deceased);

        // Build a descriptive death string — include the killer if we know who it was.
        String deathDescription = (killerName != null && !killerName.isBlank())
                ? deceasedName + " was killed by " + killerName
                : deceasedName + " was killed nearby";

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

            // Always store the permanent death memory — even for mobs that have not been
            // spoken to yet. When the player first talks to them the memory will already
            // be there, so they can reference the death in that first conversation.
            if (!witnessData.witnessedDeaths.contains(deathDescription)) {
                witnessData.witnessedDeaths.add(deathDescription);
            }

            // Active in-character reactions need a character sheet. Skip mobs that
            // haven't been generated yet — they'll still have the permanent memory above.
            if (witnessData.characterSheet.isEmpty()) continue;

            // Fire one active voice/text reaction (first eligible witness only).
            if (!activeReactionFired && witnessData.status != ChatDataManager.ChatStatus.PENDING) {
                net.minecraft.server.level.ServerPlayer contextPlayer = players.stream()
                        .min(Comparator.comparingDouble(p -> (double) p.distanceTo(witness)))
                        .orElse(players.get(0));
                String trigger = killerName != null && !killerName.isBlank()
                        ? "<witnesses " + deceasedName + " being killed by " + killerName + ">"
                        : "<witnesses " + deceasedName + " die nearby>";
                LOGGER.info("Death witness: {} sees {} die (killer: {})",
                        witness.getType().toShortString(), deceasedName,
                        killerName != null ? killerName : "unknown");
                ServerPackets.generate_chat("English", witnessData, contextPlayer, witness, trigger, true);
                activeReactionFired = true; // one active reaction per death keeps costs low
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
