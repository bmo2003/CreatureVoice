// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.chat;

import com.owlmaddie.commands.ConfigurationHandler;
import com.owlmaddie.controls.SpeedControls;
import com.owlmaddie.goals.*;
import com.owlmaddie.message.Behavior;
import com.owlmaddie.message.MessageParser;
import com.owlmaddie.message.ParsedMessage;
import com.owlmaddie.network.ServerPackets;
import com.owlmaddie.network.ClickEventHelper;
import com.owlmaddie.i18n.TR;
import com.owlmaddie.particle.ParticleEmitter;
import com.owlmaddie.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

import static com.owlmaddie.network.ServerPackets.*;

/**
 * The {@code EntityChatData} class represents a conversation between an
 * entity and one or more players, including friendship, character sheets,
 * and the status of the current displayed message.
 */
public class EntityChatData {
    public static final Logger LOGGER = LoggerFactory.getLogger("creaturechat");
    public static final TR INFO_HELP_LINK = new TR("info.help_link", "Help is available at %s");
    public static final TR ERROR_PREFIX = new TR("error.prefix", "Error: ");
    public static final List<TR> ERROR_MISC = List.of(
            INFO_HELP_LINK,
            ERROR_PREFIX
    );

    public static final TR SOLUTION_CONNECTION = new TR("solution.connection", "Solution: Check internet connection or firewall");
    public static final TR SOLUTION_VERIFY_URL = new TR("solution.verify_url", "Solution: Verify the API URL");
    public static final TR SOLUTION_ADD_KEY = new TR("solution.add_key", "Solution: Add a valid API key");
    public static final TR SOLUTION_CHECK_REGION = new TR("solution.check_region", "Solution: Check region or VPN");
    public static final TR SOLUTION_ADD_FUNDS = new TR("solution.add_funds", "Solution: Add funds to your account");
    public static final TR SOLUTION_SERVER_ERROR = new TR("solution.server_error", "Solution: Server error, try again later");
    public static final TR SOLUTION_TRY_AGAIN = new TR("solution.try_again", "Solution: Try again later");
    public static final List<TR> ERROR_SOLUTIONS = List.of(
            SOLUTION_CONNECTION,
            SOLUTION_VERIFY_URL,
            SOLUTION_ADD_KEY,
            SOLUTION_CHECK_REGION,
            SOLUTION_ADD_FUNDS,
            SOLUTION_SERVER_ERROR,
            SOLUTION_TRY_AGAIN
    );
    public String entityId;
    public String currentMessage;
    public int currentLineNumber;
    public ChatDataManager.ChatStatus status;
    public String characterSheet;
    public ChatDataManager.ChatSender sender;
    public int auto_generated;
    public List<ChatMessage> previousMessages;
    // Permanent death log — entries never expire so mobs remember forever who died nearby.
    // Stored in chatdata.json via GSON so it survives server restarts.
    public List<String> witnessedDeaths;
    public Long born;
    public Long death;
    public transient AutoMessageBucket autoBucket;

    // Holds the player's voice message that arrived while character generation was in progress.
    // After generation completes the message is replayed so the player doesn't have to speak twice.
    public transient String pendingVoiceMessage = null;

    // Short-lived log of what nearby NPCs said during this session (7.14 — NPC hears NPCs speak).
    // Transient: rebuilt each session, not saved to chatdata.json.
    // Entries format: "[Name] said: \"...\""  Max 5, newest appended at the end.
    public transient List<String> recentlyHeard = new ArrayList<>();

    // Set by generate_character/generate_chat when the entity type has a foreign-language personality.
    // When true, generateMessage injects a subtitle rule into the chat prompt so the entity
    // includes [EN: English translation] tags in every response for the bubble to display.
    public transient boolean subtitleMode = false;
    public transient String nativeLanguage = null;

    // Hard speaking-style constraint for this entity type (e.g. "Max 3 words. Moan and grunt.").
    // Injected into entity_speaking_style on every chat call so the LLM always follows it
    // regardless of whatever random speaking style the character generator rolled.
    public transient String chatStyleNote = null;

    // Timestamp of the last time this entity's own attack-response LLM call fired.
    // Used by MixinLivingEntity to enforce a 5-second cooldown so rapid sword swings
    // don't generate one API call per hit, drowning out each response with the next.
    public transient long lastAttackResponseTime = 0L;

    // Timestamp of the last player-initiated message to this entity (not auto-messages).
    // Used by NpcLifeManager to suppress initiative/overhear interruptions while the
    // player is actively conversing with a nearby mob.
    public transient long lastPlayerChatTime = 0L;

    // True while this mob is on a SPEAK_TO delivery mission. Suppresses overhear reactions
    // so the messenger doesn't react to the target's reply as an eavesdropper — it reports
    // back to the player via the return-trip callback instead.
    public transient boolean onDeliveryMission = false;

    // Monotonically-increasing LLM call counter. Each call to generateMessage increments this
    // and captures the value before the async HTTP request fires. When the response returns, it
    // checks whether the counter still matches — if another call fired in the meantime the counter
    // will have advanced, so the stale response is silently discarded instead of overwriting the
    // newer one. This prevents the "what's all this commotion" race where an auto-witness trigger
    // and a player message both fly at the same time and the older result lands last.
    public transient java.util.concurrent.atomic.AtomicInteger llmGenId =
            new java.util.concurrent.atomic.AtomicInteger(0);

    // The map to store data for each player interacting with this entity
    public Map<String, PlayerData> players;

    public EntityChatData(String entityId) {
        this.entityId = entityId;
        this.players = new HashMap<>();
        this.currentMessage = "";
        this.currentLineNumber = 0;
        this.characterSheet = "";
        this.status = ChatDataManager.ChatStatus.NONE;
        this.sender = ChatDataManager.ChatSender.USER;
        this.auto_generated = 0;
        this.previousMessages = new ArrayList<>();
        this.witnessedDeaths = new ArrayList<>();
        this.born = System.currentTimeMillis();
        this.autoBucket = null;
    }

    // Post-deserialization initialization
    public void postDeserializeInitialization() {
        if (this.players == null) {
            this.players = new HashMap<>(); // Ensure players map is initialized
        }
        if (this.witnessedDeaths == null) {
            this.witnessedDeaths = new ArrayList<>(); // Migrate old saves that predate this field
        }

        // Initialize the LLM generation counter — Gson uses Unsafe.allocateInstance() which
        // skips field initializers, so transient fields are always null after deserialization.
        this.llmGenId = new java.util.concurrent.atomic.AtomicInteger(0);

        // Any entity saved mid-request (status=PENDING) has no active LLM call after
        // a reload — recover to the last ASSISTANT message so the spinner clears.
        // If no ASSISTANT message exists, reset to NONE so the entity regenerates cleanly.
        if (this.status == ChatDataManager.ChatStatus.PENDING) {
            String lastAssistant = null;
            if (this.previousMessages != null) {
                for (int i = this.previousMessages.size() - 1; i >= 0; i--) {
                    ChatMessage cm = this.previousMessages.get(i);
                    if (cm.sender == ChatDataManager.ChatSender.ASSISTANT) {
                        lastAssistant = cm.message;
                        break;
                    }
                }
            }
            if (lastAssistant != null) {
                this.currentMessage = lastAssistant;
                this.sender = ChatDataManager.ChatSender.ASSISTANT;
                this.status = ChatDataManager.ChatStatus.DISPLAY;
            } else {
                // No prior response — entity was mid-generation; reset to NONE for a fresh start
                this.status = ChatDataManager.ChatStatus.NONE;
            }
        }
    }

    // Get the player data (or fallback to the blank player)
    public PlayerData getPlayerData(String playerName) {
        if (this.players == null) {
            this.players = new HashMap<>();
        }
        return this.players.computeIfAbsent(playerName, k -> new PlayerData());
    }

    // Generate light version of chat data (no previous messages)
    public EntityChatDataLight toLightVersion(String playerName) {
        return new EntityChatDataLight(this, playerName);
    }

    public String getCharacterProp(String propertyName) {
        // Create a case-insensitive regex pattern to match the property name and capture its value
        Pattern pattern = Pattern.compile("-?\\s*" + Pattern.quote(propertyName) + ":\\s*(.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(characterSheet);

        if (matcher.find()) {
            // Return the captured value, trimmed of any excess whitespace
            return matcher.group(1).trim().replace("\"", "");
        }

        return "N/A";
    }

    // Get list of status effects for player (handle different Minecraft versions)
    private static MobEffect effectOf(MobEffectInstance inst) {
        Object raw = inst.getEffect();        // 1.20.4: StatusEffect
        if (raw instanceof MobEffect se) {     // J 17-compatible pattern match
            return se;
        }
        return ((Holder<MobEffect>) raw).value();
    }

    // Generate context object
    public Map<String, String> getPlayerContext(ServerPlayer player, String userLanguage, ConfigurationHandler.Config config) {
        // Add PLAYER context information
        Map<String, String> contextData = new HashMap<>();
        contextData.put("player_name", player.getDisplayName().getString());
        // Show player health with a qualitative label so the LLM immediately grasps severity
        // without having to calculate percentages.
        float playerHp = player.getHealth();
        float playerMaxHp = player.getMaxHealth();
        float playerHpPct = playerHp / playerMaxHp;
        String playerHealthLabel = playerHpPct <= 0.25f ? "Critical"
                                 : playerHpPct <= 0.50f ? "Low"
                                 : playerHpPct <= 0.75f ? "Moderate"
                                 : "Healthy";
        contextData.put("player_health", playerHealthLabel + " (" + Math.round(playerHp) + "/" + Math.round(playerMaxHp) + ")");
        contextData.put("player_hunger", String.valueOf(player.getFoodData().getFoodLevel()));
        contextData.put("player_held_item", String.valueOf(player.getMainHandItem().getItem().toString()));
        contextData.put("player_biome", player.level().getBiome(player.blockPosition()).unwrapKey().get().location().getPath());
        contextData.put("player_is_creative", player.isCreative() ? "yes" : "no");
        contextData.put("player_is_swimming", player.isSwimming() ? "yes" : "no");
        contextData.put("player_is_on_ground", player.onGround() ? "yes" : "no");
        contextData.put("player_language", userLanguage);

        ItemStack headArmor = ArmorHelper.getArmor(player, EquipmentSlot.HEAD);
        ItemStack chestArmor = ArmorHelper.getArmor(player, EquipmentSlot.CHEST);
        ItemStack legsArmor = ArmorHelper.getArmor(player, EquipmentSlot.LEGS);
        ItemStack feetArmor = ArmorHelper.getArmor(player, EquipmentSlot.FEET);
        contextData.put("player_armor_head", headArmor.getItem().toString());
        contextData.put("player_armor_chest", chestArmor.getItem().toString());
        contextData.put("player_armor_legs", legsArmor.getItem().toString());
        contextData.put("player_armor_feet", feetArmor.getItem().toString());

        // Get active player effects
        String effectsString = player.getActiveEffectsMap().values().stream()
            .map(inst -> effectOf(inst).getDescriptionId() + " x" + (inst.getAmplifier() + 1))
            .collect(Collectors.joining(", "));
        contextData.put("player_active_effects", effectsString);

        // Add custom story section (if any)
        if (!config.getStory().isEmpty()) {
            contextData.put("story", "Story: " + config.getStory());
        } else {
            contextData.put("story", "");
        }


        // Get World time (as 24 hour value)
        int hours = (int) ((player.level().getDayTime() / 1000 + 6) % 24); // Minecraft day starts at 6 AM
        int minutes = (int) (((player.level().getDayTime() % 1000) / 1000.0) * 60);
        contextData.put("world_time", String.format("%02d:%02d", hours, minutes));
        contextData.put("world_is_raining", player.level().isRaining() ? "yes" : "no");
        contextData.put("world_is_thundering", player.level().isThundering() ? "yes" : "no");
        contextData.put("world_difficulty", player.level().getDifficulty().getKey());
        contextData.put("world_is_hardcore", player.level().getLevelData().isHardcore() ? "yes" : "no");

        // Get moon phase
        String moonPhaseDescription = switch (player.level().getMoonPhase()) {
            case 0 -> "Full Moon";
            case 1 -> "Waning Gibbous";
            case 2 -> "Last Quarter";
            case 3 -> "Waning Crescent";
            case 4 -> "New Moon";
            case 5 -> "Waxing Crescent";
            case 6 -> "First Quarter";
            case 7 -> "Waxing Gibbous";
            default -> "Unknown";
        };
        contextData.put("world_moon_phase", moonPhaseDescription);

        // Dimension — tells the NPC whether they are in the Overworld, Nether, or End
        String dimPath = player.level().dimension().location().getPath();
        String dimension = switch (dimPath) {
            case "the_nether" -> "The Nether";
            case "the_end"    -> "The End";
            default           -> "The Overworld";
        };
        contextData.put("world_dimension", dimension);

        // Get Entity details — the mob may have despawned between character gen and
        // message gen (async race), so guard against null to avoid NPE.
        Mob entity = (Mob) ServerEntityFinder.getEntityByUUID((ServerLevel)player.level(), UUID.fromString(entityId));
        if (entity == null) {
            LOGGER.warn("Entity {} despawned before context could be built — aborting", entityId);
            return null;
        }
        if (entity.getCustomName() == null) {
            contextData.put("entity_name", "");
        } else {
            contextData.put("entity_name", entity.getCustomName().getString());
        }
        contextData.put("entity_type", entity.getType().getDescription().getString());
        // Show entity health with a qualitative label so the LLM immediately grasps severity
        // without having to calculate percentages.
        float entityHp = entity.getHealth();
        float entityMaxHp = entity.getMaxHealth();
        float entityHpPct = entityHp / entityMaxHp;
        String entityHealthLabel = entityHpPct <= 0.25f ? "Critical"
                                 : entityHpPct <= 0.50f ? "Low"
                                 : entityHpPct <= 0.75f ? "Moderate"
                                 : "Healthy";
        contextData.put("entity_health", entityHealthLabel + " (" + Math.round(entityHp) + "/" + Math.round(entityMaxHp) + ")");
        contextData.put("entity_personality", getCharacterProp("Personality"));
        contextData.put("entity_speaking_style", getCharacterProp("Speaking Style / Tone"));
        contextData.put("entity_likes", getCharacterProp("Likes"));
        contextData.put("entity_dislikes", getCharacterProp("Dislikes"));
        contextData.put("entity_age", getCharacterProp("Age"));
        contextData.put("entity_alignment", getCharacterProp("Alignment"));
        contextData.put("entity_class", getCharacterProp("Class"));
        contextData.put("entity_skills", getCharacterProp("Skills"));
        contextData.put("entity_background", getCharacterProp("Background"));
        if (entity instanceof AgeableMob ageableMob && ageableMob.getAge() < 0) {
            contextData.put("entity_maturity", "Baby");
        } else {
            contextData.put("entity_maturity", "Adult");
        }

        PlayerData playerData = this.getPlayerData(player.getDisplayName().getString());
        if (playerData != null) {
            contextData.put("entity_friendship", String.valueOf(playerData.friendship));
        } else {
            contextData.put("entity_friendship", String.valueOf(0));
        }

        // Underground: simple Y-level check (sea level = 63)
        int playerY = player.blockPosition().getY();
        if (playerY < 20) {
            contextData.put("player_underground", "yes (deep underground)");
        } else if (playerY < 63) {
            contextData.put("player_underground", "yes (below surface)");
        } else {
            contextData.put("player_underground", "no");
        }

        // Nearby structures — large named structures (villages, strongholds, etc.)
        contextData.put("nearby_structures",
                findNearbyStructures((ServerLevel) player.level(), player.blockPosition()));

        // Nearby containers — chests, furnaces, brewing stands, etc. the mob can reference
        // or lead the player to. Scanned from the mob's position (not the player's).
        contextData.put("nearby_containers",
                findNearbyContainers((ServerLevel) entity.level(), entity.blockPosition()));

        // Nearby buildings — detected by scanning for door blocks, then clustering.
        // Lets the mob know about individual houses it could hide in, burn, or lead to.
        contextData.put("nearby_buildings",
                findNearbyBuildings((ServerLevel) entity.level(), entity.blockPosition()));

        // Nearby world features — nether portals, large water bodies, lava pools, beacons.
        // Detected by block sampling so the mob can reference them in conversation.
        contextData.put("nearby_world_features",
                findNearbyWorldFeatures((ServerLevel) entity.level(), entity.blockPosition()));

        // Nearby NPCs — other mobs with character sheets that this entity can name-drop.
        // Injected into World Info so the LLM knows who else is around.
        contextData.put("nearby_npcs", buildNearbyNpcsContext(entity, (ServerLevel) player.level()));

        // Entity inventory / profession / trades — prevents the LLM from lying about
        // what the mob is carrying or what it can offer in trade.
        contextData.put("entity_inventory", buildEntityInventoryContext(entity));

        // Build recent_events by combining permanent death memories (never expire) with
        // transient nearby events (attacks, confrontations — expire after 60 sec).
        // Deaths are stored per-mob in witnessedDeaths and survive server restarts.
        StringBuilder events = new StringBuilder();
        if (this.witnessedDeaths != null && !this.witnessedDeaths.isEmpty()) {
            events.append(String.join("; ", this.witnessedDeaths));
        }
        String recentEvents = com.owlmaddie.npc.NpcLifeManager.getRecentEventsContext(entity);
        if (!recentEvents.isEmpty()) {
            if (events.length() > 0) events.append("; ");
            events.append(recentEvents);
        }
        contextData.put("recent_events", events.length() > 0 ? events.toString() : "none");

        // Things this NPC personally heard other nearby NPCs say during this session.
        // Populated by NpcLifeManager.broadcastNpcSpeech whenever any NPC in range responds.
        String heardCtx = (this.recentlyHeard == null || this.recentlyHeard.isEmpty())
                ? "none"
                : String.join("; ", this.recentlyHeard);
        contextData.put("recently_heard_nearby", heardCtx);

        return contextData;
    }

    /**
     * Returns a list of nearby NPCs within 32 blocks that have character sheets, with
     * both name and type — e.g. "Jackson (Villager, ~4 blocks), Delilah (Zombie, ~11 blocks)".
     * Including the entity type prevents the LLM from confusing similarly-spelled names
     * and helps it answer questions like "do you know the zombie over there?"
     */
    /** Public accessor so ServerPackets can inject nearby NPC names during character generation. */
    public static String buildNearbyNpcsContext(Mob entity, ServerLevel level) {
        // Active range: up to 64 blocks — NPC can see, hear, and interact with these
        // Distant range: 64–200 blocks — NPC has general awareness ("I know they live to the north")
        AABB closeBox   = entity.getBoundingBox().inflate(64.0);
        AABB distantBox = entity.getBoundingBox().inflate(200.0);
        List<Mob> allNearby = level.getEntitiesOfClass(Mob.class, distantBox);
        List<String> entries = new ArrayList<>();

        for (Mob other : allNearby) {
            if (other.getUUID().equals(entity.getUUID())) continue;
            float dist = other.distanceTo(entity);
            if (dist > 200.0f) continue;
            EntityChatData data = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(other.getStringUUID());
            if (!data.characterSheet.isEmpty()) {
                String name     = data.getCharacterProp("Name");
                if (name.isEmpty()) name = other.getType().getDescription().getString();
                String typeName = other.getType().getDescription().getString();
                int distBlocks  = Math.round(dist);
                if (dist <= 64.0f) {
                    entries.add(name + " (" + typeName + ", ~" + distBlocks + " blocks)");
                } else {
                    entries.add(name + " (" + typeName + ", ~" + distBlocks + " blocks, distant)");
                }
            }
        }
        return entries.isEmpty() ? "none" : String.join(", ", entries);
    }

    /**
     * Builds a text summary of what this mob is carrying or can offer:
     * profession (for villagers), held items, and any notable equipment.
     * Used to ground the LLM so it doesn't invent items it doesn't have.
     */
    private static String buildEntityInventoryContext(Mob entity) {
        StringBuilder inv = new StringBuilder();

        // Villager profession + trades
        if (entity instanceof net.minecraft.world.entity.npc.Villager villager) {
            try {
                // Parse the profession out of VillagerData.toString() — this works regardless
                // of whether the API exposes it as getProfession() or profession() or a Holder.
                // VillagerData.toString() typically produces something like:
                //   "VillagerData[profession=Holder.Reference[minecraft:farmer], ...]"
                String vdStr = villager.getVillagerData().toString();
                java.util.regex.Matcher pm = java.util.regex.Pattern
                        .compile("profession[\\[=][^a-z]*([a-z_]+)")
                        .matcher(vdStr);
                if (pm.find()) {
                    String profName = pm.group(1).replace("_", " ");
                    if (!profName.equals("none") && !profName.isBlank()) {
                        inv.append("Profession: ").append(profName).append(". ");
                    }
                }
            } catch (Exception ignored) {}

            try {
                // Trades — getResult() always returns ItemStack. getCostA() is ItemCost in
                // 1.21.5+ but its toString() gives readable text like "1 minecraft:wheat".
                net.minecraft.world.item.trading.MerchantOffers offers = villager.getOffers();
                if (offers != null && !offers.isEmpty()) {
                    inv.append("Known trades (pay -> receive): ");
                    int count = 0;
                    for (net.minecraft.world.item.trading.MerchantOffer offer : offers) {
                        if (count++ >= 6) { inv.append("..."); break; }
                        String pays = cleanItemDesc(offer.getCostA().toString());
                        String gets = formatItemStack(offer.getResult());
                        inv.append(pays).append(" -> ").append(gets);
                        if (count < Math.min(offers.size(), 6)) inv.append("; ");
                    }
                    inv.append(". ");
                }
            } catch (Exception ignored) {}
        }

        // Held items — applies to all mob types
        try {
            ItemStack mainHand = entity.getMainHandItem();
            ItemStack offHand  = entity.getOffhandItem();
            if (!mainHand.isEmpty()) {
                inv.append("Holding: ").append(formatItemStack(mainHand)).append(". ");
            }
            if (!offHand.isEmpty()) {
                inv.append("Offhand: ").append(formatItemStack(offHand)).append(". ");
            }
        } catch (Exception ignored) {}

        // Inventory contents from the mob's 15-slot SimpleContainer
        try {
            net.minecraft.world.Container mobInv =
                    ((com.owlmaddie.inventory.ChatInventory) entity).creaturechat$getInventory();
            List<String> invItems = new java.util.ArrayList<>();
            for (int s = 0; s < mobInv.getContainerSize(); s++) {
                ItemStack slot = mobInv.getItem(s);
                if (!slot.isEmpty()) {
                    invItems.add(formatItemStack(slot));
                }
            }
            if (!invItems.isEmpty()) {
                inv.append("Inventory: ").append(String.join(", ", invItems)).append(". ");
            }
        } catch (Exception ignored) {}

        return inv.length() == 0 ? "nothing notable" : inv.toString().trim();
    }

    /** Returns a clean human-readable item name, e.g. "iron sword" from "minecraft:iron_sword". */
    private static String formatItemStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "nothing";
        String raw = stack.getItem().toString().replace("minecraft:", "").replace("_", " ");
        int count = stack.getCount();
        return count > 1 ? count + "x " + raw : raw;
    }

    /**
     * Returns true if two names are close enough to be a voice transcription match.
     * Handles common Deepgram mishearings like "Pepp" for "Pip", "Quinten" for "Quinton".
     * Uses Levenshtein edit distance — allows 1 edit for names <= 4 chars, 2 for longer names.
     */
    private static boolean fuzzyNameMatch(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.contains(b) || b.contains(a)) return true;
        int dist = levenshteinDistance(a, b);
        // Voice transcription commonly swaps vowels and adds/drops letters
        // (e.g. "Pip"→"Pepp", "Quinton"→"Quinten"). Allow up to 2 edits
        // for all names — false positives are unlikely in a small NPC pool.
        return dist <= 2;
    }

    /**
     * Finds the best matching mob by name in the search area.
     * Exact matches always win (returns immediately). Fuzzy matches only apply
     * to entities with generated character sheets, preventing generic mob type
     * names like "Pig" from matching character names like "Pip".
     */
    private static Mob findBestNamedMob(String targetName, Mob searcher,
                                        net.minecraft.server.level.ServerLevel level,
                                        net.minecraft.world.phys.AABB searchBox) {
        String lowerTarget = targetName.trim().toLowerCase(java.util.Locale.ENGLISH);
        Mob bestFuzzy = null;
        int bestDist = Integer.MAX_VALUE;

        for (Mob nearby : level.getEntitiesOfClass(Mob.class, searchBox)) {
            if (nearby.getUUID().equals(searcher.getUUID())) continue;

            EntityChatData nearbyData = ChatDataManager.getServerInstance()
                    .getOrCreateChatData(nearby.getStringUUID());
            String sheetName = com.owlmaddie.npc.NpcLifeManager
                    .extractMobNamePublic(nearbyData, nearby).toLowerCase(java.util.Locale.ENGLISH);
            String displayName = nearby.getDisplayName().getString()
                    .toLowerCase(java.util.Locale.ENGLISH);
            String firstName = sheetName.split("\\s+")[0];

            // Exact match on any name — immediate win
            if (displayName.equals(lowerTarget) || firstName.equals(lowerTarget)
                    || sheetName.equals(lowerTarget)) {
                return nearby;
            }

            // Fuzzy match only for entities with a generated character sheet.
            // This prevents generic mob type names ("Pig") from matching "Pip".
            if (!nearbyData.characterSheet.isEmpty()) {
                int dist = Math.min(levenshteinDistance(firstName, lowerTarget),
                        levenshteinDistance(sheetName, lowerTarget));
                if (dist <= 2 && dist < bestDist) {
                    bestDist = dist;
                    bestFuzzy = nearby;
                }
            }
        }
        return bestFuzzy;
    }

    /** Standard Levenshtein edit distance between two strings. */
    private static int levenshteinDistance(String s, String t) {
        int m = s.length(), n = t.length();
        int[] prev = new int[n + 1], curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[n];
    }

    /**
     * Strips class names and namespace from an item/cost toString() output so the LLM
     * gets readable text. Works on both ItemStack and ItemCost toString formats.
     * Examples: "1 minecraft:wheat" -> "wheat",  "ItemCost{item=minecraft:emerald, count=1}" -> "emerald"
     */
    private static String cleanItemDesc(String raw) {
        if (raw == null) return "?";
        // Extract last segment after a colon (namespace:id) and remove noise chars
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)?\\s*(?:minecraft:)?([a-z_]+)")
                .matcher(raw.toLowerCase(java.util.Locale.ENGLISH));
        List<String> parts = new ArrayList<>();
        while (m.find()) {
            String count = m.group(1);
            String name  = m.group(2).replace("_", " ");
            // Skip known class-name fragments that appear in toString output
            if (name.equals("item") || name.equals("count") || name.equals("itemcost")
                    || name.equals("holder") || name.equals("reference")) continue;
            parts.add(count != null && !count.equals("1") ? count + "x " + name : name);
            if (parts.size() >= 2) break; // "2x wheat" is enough
        }
        return parts.isEmpty() ? raw.substring(0, Math.min(raw.length(), 20)) : String.join(" + ", parts);
    }

    /**
     * Scans for common structures near the given position using Minecraft's built-in
     * structure locator. Returns a comma-separated list with direction and rough distance,
     * or "none detected" if nothing was found within search range.
     */
    private static String findNearbyStructures(ServerLevel level, BlockPos origin) {
        // [display name, tag path] — tag path must match a structure tag in data/minecraft/tags/structure/
        // Village variants (village_plains, village_desert, etc.) all start with "village".
        String[][] structuresToCheck = {
            // Overworld structures
            {"Village",           "village"},
            {"Pillager Outpost",  "pillager_outpost"},
            {"Mineshaft",         "mineshaft"},
            {"Stronghold",        "stronghold"},
            {"Ancient City",      "ancient_city"},
            {"Trial Chambers",    "trial_chambers"},
            {"Ruined Portal",     "ruined_portal"},
            {"Desert Temple",     "desert_pyramid"},
            {"Jungle Temple",     "jungle_temple"},
            {"Ocean Monument",    "monument"},
            {"Woodland Mansion",  "mansion"},
            {"Shipwreck",         "shipwreck"},
            {"Swamp Hut",         "swamp_hut"},
            {"Igloo",             "igloo"},
            // Nether structures (silently skipped when not in the Nether)
            {"Nether Fortress",   "fortress"},
            {"Bastion Remnant",   "bastion_remnant"},
            // End structures (silently skipped when not in the End)
            {"End City",          "end_city"},
        };

        List<String> found = new ArrayList<>();
        for (String[] entry : structuresToCheck) {
            try {
                TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE,
                        ResourceLocation.parse("minecraft:" + entry[1]));

                // Primary containment check: is the player inside any individual structure piece
                // (a specific building, tunnel section, room, etc.)?
                // Works great for compact structures like mineshafts, strongholds, temples.
                StructureStart pieceCheck = level.structureManager()
                        .getStructureWithPieceAt(origin, tag);
                if (pieceCheck != StructureStart.INVALID_START) {
                    found.add(entry[0] + " (you are here)");
                    continue;
                }

                // Find the nearest structure of this type
                BlockPos structPos = level.findNearestMapStructure(tag, origin, 200, false);
                if (structPos == null) continue;

                int dx = structPos.getX() - origin.getX();
                int dz = structPos.getZ() - origin.getZ();
                int xzDist = (int) Math.sqrt(dx * dx + dz * dz);

                // Proximity fallback: handles open structures like villages where the player
                // walks between buildings and isn't inside any single piece's bounding box.
                // Only triggers if the structure is also at a similar Y level (within 30 blocks)
                // so a mineshaft directly 100 blocks below doesn't falsely say "you are here".
                int yDist = Math.abs(structPos.getY() - origin.getY());
                if (xzDist < 50 && yDist < 30) {
                    found.add(entry[0] + " (you are here)");
                    continue;
                }

                // Report direction + rounded distance
                int dist = (xzDist + 5) / 10 * 10;
                found.add(entry[0] + " ~" + dist + " blocks " + compassDirection(dx, dz));

            } catch (Exception ignored) {
                // Structure type doesn't exist in this dimension — skip it
            }
        }
        return found.isEmpty() ? "none detected nearby" : String.join(", ", found);
    }

    /** Convert a delta-X/Z vector to the nearest compass direction (8 points). */
    private static String compassDirection(int dx, int dz) {
        // In Minecraft: -Z = North, +Z = South, +X = East, -X = West
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = North, clockwise
        if (angle < 0) angle += 360;
        String[] dirs = {"North", "North-East", "East", "South-East",
                         "South", "South-West", "West", "North-West"};
        return dirs[(int) Math.round(angle / 45) % 8];
    }

    /**
     * Scans loaded chunks within 30 blocks for container-type block entities
     * (chests, furnaces, brewing stands, etc.) and returns a readable list with
     * direction and distance relative to the given origin (the mob's position).
     * Using chunk block-entity maps is cheap — no block-by-block iteration needed.
     */
    private static String findNearbyContainers(ServerLevel level, BlockPos origin) {
        int searchRadius = 30;
        int chunkRadius = (searchRadius >> 4) + 1;
        int centerChunkX = origin.getX() >> 4;
        int centerChunkZ = origin.getZ() >> 4;

        List<String> found = new ArrayList<>();

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                // Skip unloaded chunks — getChunk on an unloaded chunk would force-load it
                if (!level.isLoaded(new BlockPos(cx << 4, 0, cz << 4))) continue;
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> entry
                        : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    int dx = pos.getX() - origin.getX();
                    int dz = pos.getZ() - origin.getZ();
                    int dist = (int) Math.sqrt(dx * dx + dz * dz);
                    if (dist > searchRadius) continue;
                    String name = containerTypeName(entry.getValue());
                    if (name != null) {
                        found.add(name + " (~" + dist + " blocks " + compassDirection(dx, dz) + ")");
                    }
                }
            }
        }

        if (found.isEmpty()) return "none detected";
        // Sort by distance so nearest entries appear first
        found.sort(Comparator.comparingInt(s -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("~(\\d+)").matcher(s);
            return m.find() ? Integer.parseInt(m.group(1)) : 999;
        }));
        return String.join(", ", found.subList(0, Math.min(found.size(), 10)));
    }

    /** Maps a block entity to a readable container name, or null if not a relevant container. */
    private static String containerTypeName(net.minecraft.world.level.block.entity.BlockEntity be) {
        if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)      return "Chest";
        if (be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity)     return "Barrel";
        if (be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity) return "Shulker Box";
        if (be instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity)    return "Furnace";
        if (be instanceof net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity) return "Blast Furnace";
        if (be instanceof net.minecraft.world.level.block.entity.SmokerBlockEntity)     return "Smoker";
        if (be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity) return "Brewing Stand";
        if (be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity)     return "Hopper";
        if (be instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity)  return "Dispenser";
        if (be instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) return "Ender Chest";
        return null;
    }

    /**
     * Scans within 50 blocks for door blocks, then clusters nearby doors together
     * so each cluster counts as one building. Returns a readable list of building
     * locations relative to the origin (the mob's position).
     *
     * Doors are the most reliable single indicator of human-made structures —
     * they appear in village houses, player houses, and most generated buildings.
     * Scanning at 3-block steps keeps the cost low (~800 block checks).
     */
    private static String findNearbyBuildings(ServerLevel level, BlockPos origin) {
        int searchRadius = 50;
        List<BlockPos> doorPositions = new ArrayList<>();

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                if (x * x + z * z > searchRadius * searchRadius) continue;
                // Check ground level ± a few blocks to handle uneven terrain
                for (int dy = -2; dy <= 8; dy++) {
                    BlockPos checkPos = origin.offset(x, dy, z);
                    if (!level.isLoaded(checkPos)) continue;
                    if (level.getBlockState(checkPos).is(BlockTags.DOORS)) {
                        doorPositions.add(checkPos);
                        break; // one door per XZ column is sufficient
                    }
                }
            }
        }

        if (doorPositions.isEmpty()) return "none detected";

        // Greedy cluster: doors within 12 blocks of the seed door form one building
        List<BlockPos> buildingCentres = clusterDoorPositions(doorPositions, 12);

        List<String> found = new ArrayList<>();
        for (BlockPos centre : buildingCentres) {
            int dx = centre.getX() - origin.getX();
            int dz = centre.getZ() - origin.getZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            String loc = dist < 5 ? "you are here" : "~" + dist + " blocks " + compassDirection(dx, dz);
            found.add("Building (" + loc + ")");
        }
        // Sort closest first (entries with "you are here" have no number — sort them first)
        found.sort(Comparator.comparingInt(s -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("~(\\d+)").matcher(s);
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        }));
        return String.join(", ", found.subList(0, Math.min(found.size(), 5)));
    }

    /**
     * Scans for notable world features near the mob that are not covered by the structure
     * or building systems: nether portals, large water bodies (lakes/rivers/oceans), lava
     * pools, and beacons.
     *
     * Portal and beacon detection look for specific blocks in a radius.
     * Water and lava detection sample the area at 3-block steps and require a minimum
     * hit count so single cauldrons/puddles/small pockets are ignored.
     * Water and lava scanning are skipped in the Nether where both are too common to be useful.
     */
    private static String findNearbyWorldFeatures(ServerLevel level, BlockPos origin) {
        List<String> found = new ArrayList<>();
        String dimPath = level.dimension().location().getPath();
        boolean inNether = dimPath.equals("the_nether");

        // --- Nether Portal: 80-block XZ radius, entity Y ± 20, step 3 XZ / step 2 Y ---
        // Portals exist in all dimensions; always scan.
        {
            final int RADIUS = 80;
            final int Y_RANGE = 20;
            BlockPos nearestPortal = null;
            int nearestDist = Integer.MAX_VALUE;

            for (int x = -RADIUS; x <= RADIUS; x += 3) {
                for (int z = -RADIUS; z <= RADIUS; z += 3) {
                    if (x * x + z * z > RADIUS * RADIUS) continue;
                    for (int dy = -Y_RANGE; dy <= Y_RANGE; dy += 2) {
                        BlockPos check = origin.offset(x, dy, z);
                        if (!level.isLoaded(check)) continue;
                        if (level.getBlockState(check).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
                            int dist = (int) Math.sqrt(x * x + z * z);
                            if (dist < nearestDist) {
                                nearestDist = dist;
                                nearestPortal = check;
                            }
                            break; // one find per XZ column is enough
                        }
                    }
                }
            }

            if (nearestPortal != null) {
                int dx = nearestPortal.getX() - origin.getX();
                int dz = nearestPortal.getZ() - origin.getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);
                String loc = dist < 5 ? "you are here" : "~" + dist + " blocks " + compassDirection(dx, dz);
                found.add("Nether Portal (" + loc + ")");
            }
        }

        // --- Water: 50-block XZ radius, Y -5 to +1 of entity, step 2 ---
        // Step 2 (finer than doors/structures) is needed to reliably detect small sources
        // like village wells (2x2 pool) that a 3-block step would skip entirely.
        // Tiers:
        //   3–24 hits  → "Small water source" (well, fountain, pond, puddle)
        //   25+ hits   → "Water body"          (lake, river, ocean)
        // Skipped in the Nether where water does not naturally generate.
        if (!inNether) {
            final int RADIUS = 50;
            int waterCount = 0;
            long sumX = 0, sumZ = 0;

            for (int x = -RADIUS; x <= RADIUS; x += 2) {
                for (int z = -RADIUS; z <= RADIUS; z += 2) {
                    if (x * x + z * z > RADIUS * RADIUS) continue;
                    for (int dy = -5; dy <= 1; dy++) {
                        BlockPos check = origin.offset(x, dy, z);
                        if (!level.isLoaded(check)) continue;
                        if (level.getBlockState(check).is(net.minecraft.world.level.block.Blocks.WATER)) {
                            waterCount++;
                            sumX += x;
                            sumZ += z;
                            break; // one water block per XZ column
                        }
                    }
                }
            }

            if (waterCount >= 3) {
                int cx = (int) (sumX / waterCount);
                int cz = (int) (sumZ / waterCount);
                int dist = (int) Math.sqrt(cx * cx + cz * cz);
                String label = waterCount >= 25 ? "Water body" : "Small water source";
                String loc = dist < 5 ? "you are here" : "~" + dist + " blocks " + compassDirection(cx, cz);
                found.add(label + " (" + loc + ")");
            }
        }

        // --- Lava: 40-block XZ radius, Y -5 to +2 of entity, step 3 ---
        // In the Nether, lava is omnipresent terrain — scanning would always return true and
        // the direction would be meaningless. Instead, always report it as a fixed fact so the
        // NPC is never caught saying "no lava here" when standing in the Nether.
        // Outside the Nether, require 20+ hits to rule out cauldrons and small cave pockets.
        if (inNether) {
            found.add("Lava (omnipresent — this is the Nether)");
        } else {
            final int RADIUS = 40;
            final int MIN_HITS = 20;
            int lavaCount = 0;
            long sumX = 0, sumZ = 0;

            for (int x = -RADIUS; x <= RADIUS; x += 3) {
                for (int z = -RADIUS; z <= RADIUS; z += 3) {
                    if (x * x + z * z > RADIUS * RADIUS) continue;
                    for (int dy = -5; dy <= 2; dy++) {
                        BlockPos check = origin.offset(x, dy, z);
                        if (!level.isLoaded(check)) continue;
                        if (level.getBlockState(check).is(net.minecraft.world.level.block.Blocks.LAVA)) {
                            lavaCount++;
                            sumX += x;
                            sumZ += z;
                            break; // one lava block per XZ column
                        }
                    }
                }
            }

            if (lavaCount >= MIN_HITS) {
                int cx = (int) (sumX / lavaCount);
                int cz = (int) (sumZ / lavaCount);
                int dist = (int) Math.sqrt(cx * cx + cz * cz);
                String loc = dist < 5 ? "you are here" : "~" + dist + " blocks " + compassDirection(cx, cz);
                found.add("Lava pool (" + loc + ")");
            }
        }

        // --- Beacon: chunk block-entity scan within 64 blocks ---
        // Beacons are extremely rare player-built structures and always worth reporting.
        // Reuses the same chunk map approach as the container scan for efficiency.
        {
            final int RADIUS = 64;
            int chunkRadius = (RADIUS >> 4) + 1;
            int centerChunkX = origin.getX() >> 4;
            int centerChunkZ = origin.getZ() >> 4;
            BlockPos nearestBeacon = null;
            int nearestDist = Integer.MAX_VALUE;

            for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
                for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                    if (!level.isLoaded(new BlockPos(cx << 4, 0, cz << 4))) continue;
                    net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
                    for (Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> entry
                            : chunk.getBlockEntities().entrySet()) {
                        if (!(entry.getValue() instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity))
                            continue;
                        BlockPos pos = entry.getKey();
                        int dx = pos.getX() - origin.getX();
                        int dz = pos.getZ() - origin.getZ();
                        int dist = (int) Math.sqrt(dx * dx + dz * dz);
                        if (dist <= RADIUS && dist < nearestDist) {
                            nearestDist = dist;
                            nearestBeacon = pos;
                        }
                    }
                }
            }

            if (nearestBeacon != null) {
                int dx = nearestBeacon.getX() - origin.getX();
                int dz = nearestBeacon.getZ() - origin.getZ();
                int dist = (int) Math.sqrt(dx * dx + dz * dz);
                String loc = dist < 5 ? "you are here" : "~" + dist + " blocks " + compassDirection(dx, dz);
                found.add("Beacon (" + loc + ")");
            }
        }

        return found.isEmpty() ? "none detected" : String.join(", ", found);
    }

    /**
     * Returns the BlockPos of the nearest building entrance within 50 blocks of the origin,
     * or null if no buildings were detected. Used by the LEAD and SET_FIRE behavior handlers
     * to give mobs a real destination instead of random waypoints.
     */
    public static BlockPos findNearestBuildingPos(ServerLevel level, BlockPos origin) {
        int searchRadius = 50;
        List<BlockPos> doorPositions = new ArrayList<>();

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                if (x * x + z * z > searchRadius * searchRadius) continue;
                for (int dy = -2; dy <= 8; dy++) {
                    BlockPos checkPos = origin.offset(x, dy, z);
                    if (!level.isLoaded(checkPos)) continue;
                    if (level.getBlockState(checkPos).is(BlockTags.DOORS)) {
                        doorPositions.add(checkPos);
                        break;
                    }
                }
            }
        }

        if (doorPositions.isEmpty()) return null;

        List<BlockPos> centres = clusterDoorPositions(doorPositions, 12);
        // First choice: the nearest building cluster centre that is at least 8 blocks away
        // (distSqr >= 64) so the mob actually walks somewhere visible.
        // Fallback: if every building is within 8 blocks, return the farthest one so the mob
        // still walks somewhere rather than standing still.
        java.util.Optional<BlockPos> distant = centres.stream()
                .filter(p -> p.distSqr(origin) >= 64)
                .min(Comparator.comparingInt(p -> (int) p.distSqr(origin)));
        BlockPos doorPos = distant.isPresent() ? distant.get()
                : centres.stream()
                        .max(Comparator.comparingInt(p -> (int) p.distSqr(origin)))
                        .orElse(null);
        if (doorPos == null) return null;

        // Push the nav target 4 blocks into the building interior so the mob walks fully
        // through the door. GoToPositionGoal's 2.5-block arrival radius means a target only
        // 2 blocks past the door still leaves the mob parked in the doorway — 4 blocks of
        // depth guarantees it stops at least 1.5 blocks inside regardless of approach angle.
        // Using float math avoids the rounding loss that occurs with integer offsets on diagonals.
        double dx = doorPos.getX() - origin.getX();
        double dz = doorPos.getZ() - origin.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 0.5) {
            double iX = doorPos.getX() + 0.5 + (dx / len) * 4.0;
            double iZ = doorPos.getZ() + 0.5 + (dz / len) * 4.0;
            return new BlockPos((int) Math.floor(iX), doorPos.getY(), (int) Math.floor(iZ));
        }
        return doorPos;
    }

    /**
     * Returns a walkable BlockPos at the edge of the nearest water body within 50 blocks,
     * or null if no water is found. Finds the nearest water surface block to origin, then
     * returns an adjacent land block so the mob stands at the bank, not in the water.
     */
    public static BlockPos findNearestWaterPos(ServerLevel level, BlockPos origin) {
        int searchRadius = 50;
        int minDistSq = Integer.MAX_VALUE;
        BlockPos bestPos = null;

        for (int x = -searchRadius; x <= searchRadius; x += 2) {
            for (int z = -searchRadius; z <= searchRadius; z += 2) {
                if (x * x + z * z > searchRadius * searchRadius) continue;
                for (int dy = -5; dy <= 1; dy++) {
                    BlockPos check = origin.offset(x, dy, z);
                    if (!level.isLoaded(check)) continue;
                    if (!level.getBlockState(check).is(net.minecraft.world.level.block.Blocks.WATER)) continue;
                    // Check all four adjacent horizontal blocks for walkable land
                    for (net.minecraft.core.Direction dir : new net.minecraft.core.Direction[]{
                            net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                            net.minecraft.core.Direction.EAST,  net.minecraft.core.Direction.WEST}) {
                        BlockPos adj   = check.relative(dir);
                        BlockPos below = adj.below();
                        if (!level.getBlockState(adj).isSolid()
                                && level.getBlockState(below).isSolid()
                                && !level.getBlockState(below).is(net.minecraft.world.level.block.Blocks.WATER)) {
                            int dxx = adj.getX() - origin.getX();
                            int dzz = adj.getZ() - origin.getZ();
                            int distSq = dxx * dxx + dzz * dzz;
                            if (distSq < minDistSq && distSq >= 64) { // min 8 blocks travel
                                minDistSq = distSq;
                                bestPos   = adj;
                            }
                        }
                    }
                    break; // one water block per XZ column is enough
                }
            }
        }
        return bestPos;
    }

    /**
     * Returns the BlockPos of the nearest Nether Portal block within 80 blocks,
     * or null if none detected.
     */
    public static BlockPos findNearestPortalPos(ServerLevel level, BlockPos origin) {
        final int RADIUS   = 80;
        final int Y_RANGE  = 20;
        BlockPos nearest   = null;
        int nearestDist    = Integer.MAX_VALUE;

        for (int x = -RADIUS; x <= RADIUS; x += 3) {
            for (int z = -RADIUS; z <= RADIUS; z += 3) {
                if (x * x + z * z > RADIUS * RADIUS) continue;
                for (int dy = -Y_RANGE; dy <= Y_RANGE; dy += 2) {
                    BlockPos check = origin.offset(x, dy, z);
                    if (!level.isLoaded(check)) continue;
                    if (level.getBlockState(check).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
                        int dist = (int) Math.sqrt(x * x + z * z);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest     = check;
                        }
                        break;
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Returns the BlockPos of the nearest container (chest, barrel, furnace, etc.) within
     * 30 blocks, or null if none is found. Uses chunk block-entity maps for efficiency.
     */
    public static BlockPos findNearestContainerPos(ServerLevel level, BlockPos origin) {
        int searchRadius = 30;
        int chunkRadius  = (searchRadius >> 4) + 1;
        int ccx = origin.getX() >> 4;
        int ccz = origin.getZ() >> 4;
        BlockPos nearest  = null;
        int nearestDistSq = Integer.MAX_VALUE;

        for (int cx = ccx - chunkRadius; cx <= ccx + chunkRadius; cx++) {
            for (int cz = ccz - chunkRadius; cz <= ccz + chunkRadius; cz++) {
                if (!level.isLoaded(new BlockPos(cx << 4, 0, cz << 4))) continue;
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
                for (java.util.Map.Entry<BlockPos, net.minecraft.world.level.block.entity.BlockEntity> entry
                        : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    int dx = pos.getX() - origin.getX();
                    int dz = pos.getZ() - origin.getZ();
                    int distSq = dx * dx + dz * dz;
                    if (distSq > searchRadius * searchRadius) continue;
                    net.minecraft.world.level.block.entity.BlockEntity be = entry.getValue();
                    boolean isContainer =
                            be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity ||
                            be instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity ||
                            be instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity ||
                            be instanceof net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity ||
                            be instanceof net.minecraft.world.level.block.entity.HopperBlockEntity ||
                            be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
                    if (isContainer && distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest       = pos;
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Returns the nearest outdoor position (sky-visible) within 30 blocks of the origin,
     * or null if no open-air position was found. Used when a mob is told to "go outside"
     * or "exit the building" — scans outward in expanding rings for the first surface
     * block that can see the sky.
     */
    public static BlockPos findNearestOutsidePos(ServerLevel level, BlockPos origin) {
        int originY = origin.getY();

        // If already outside, move a few blocks away so the mob visibly walks somewhere
        if (level.canSeeSky(origin.above())) {
            java.util.Random rng = new java.util.Random();
            for (int attempt = 0; attempt < 16; attempt++) {
                int dx = rng.nextInt(17) - 8; // -8 to +8
                int dz = rng.nextInt(17) - 8;
                if (dx == 0 && dz == 0) continue;
                BlockPos surface = level.getHeightmapPos(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        origin.offset(dx, 0, dz));
                // Must be at ground level (within 4 blocks of origin Y) — not on a rooftop
                if (level.canSeeSky(surface.above()) && Math.abs(surface.getY() - originY) <= 4) {
                    return surface;
                }
            }
            return null;
        }

        // Inside — scan outward in expanding rings for the nearest sky-visible ground-level spot.
        // The heightmap returns the TOP of whatever is there, so positions within the building
        // footprint return the ROOF (y=70) instead of ground (y=63). Filter by Y proximity
        // to ensure we find an actual walkable outdoor position, not the rooftop.
        for (int r = 1; r <= 30; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // only check perimeter
                    BlockPos surface = level.getHeightmapPos(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            origin.offset(dx, 0, dz));
                    if (level.canSeeSky(surface.above()) && Math.abs(surface.getY() - originY) <= 4) {
                        return surface;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the BlockPos of the nearest named structure matching the keyword within
     * 200 blocks, or null if none found. The keyword is matched against known structure
     * display names (village, mineshaft, stronghold, portal, etc.).
     */
    public static BlockPos findNearestStructurePos(ServerLevel level, BlockPos origin, String keyword) {
        String lower = keyword.toLowerCase(java.util.Locale.ENGLISH);
        // Map keyword fragments to Minecraft structure tag paths
        String[][] structureMap = {
            {"village",    "village"},
            {"outpost",    "pillager_outpost"},
            {"mineshaft",  "mineshaft"},
            {"stronghold", "stronghold"},
            {"ancient",    "ancient_city"},
            {"trial",      "trial_chambers"},
            {"ruined",     "ruined_portal"},
            {"desert",     "desert_pyramid"},
            {"jungle",     "jungle_temple"},
            {"monument",   "monument"},
            {"ocean",      "monument"},
            {"mansion",    "mansion"},
            {"woodland",   "mansion"},
            {"shipwreck",  "shipwreck"},
            {"swamp",      "swamp_hut"},
            {"witch",      "swamp_hut"},
            {"igloo",      "igloo"},
            {"fortress",   "fortress"},
            {"nether fort","fortress"},
            {"bastion",    "bastion_remnant"},
            {"end city",   "end_city"},
        };

        for (String[] entry : structureMap) {
            if (lower.contains(entry[0])) {
                try {
                    net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag =
                            net.minecraft.tags.TagKey.create(
                                    net.minecraft.core.registries.Registries.STRUCTURE,
                                    net.minecraft.resources.ResourceLocation.parse("minecraft:" + entry[1]));
                    BlockPos pos = level.findNearestMapStructure(tag, origin, 200, false);
                    return pos;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /** Greedy spatial clustering: groups door positions within clusterDist of each seed. */
    private static List<BlockPos> clusterDoorPositions(List<BlockPos> doors, int clusterDist) {
        List<BlockPos> centres = new ArrayList<>();
        boolean[] used = new boolean[doors.size()];
        for (int i = 0; i < doors.size(); i++) {
            if (used[i]) continue;
            int ax = 0, ay = 0, az = 0, count = 0;
            for (int j = i; j < doors.size(); j++) {
                if (!used[j] && doors.get(i).distSqr(doors.get(j)) <= clusterDist * clusterDist) {
                    ax += doors.get(j).getX();
                    ay += doors.get(j).getY();
                    az += doors.get(j).getZ();
                    count++;
                    used[j] = true;
                }
            }
            centres.add(new BlockPos(ax / count, ay / count, az / count));
        }
        return centres;
    }

    // Generate a new character
    public void generateCharacter(String userLanguage, ServerPlayer player, String userMessage, boolean is_auto_message, String nearbyNpcs) {
        String systemPrompt = "system-character";
        if (is_auto_message) {
            // Increment an auto-generated message
            this.auto_generated++;
        } else {
            // Reset auto-generated counter
            this.auto_generated = 0;
        }

        // Add USER Message
        this.addMessage(userMessage, ChatDataManager.ChatSender.USER, player, systemPrompt);

        // Get config (api key, url, settings)
        ConfigurationHandler.Config config = new ConfigurationHandler(ServerPackets.serverInstance).loadConfig();
        String promptText = ChatPrompt.loadPromptFromResource(ServerPackets.serverInstance.getResourceManager(), systemPrompt);

        // Add PLAYER context information
        Map<String, String> contextData = getPlayerContext(player, userLanguage, config);
        if (contextData == null) {
            // Entity despawned between character gen trigger and now — abort silently
            this.status = ChatDataManager.ChatStatus.DISPLAY;
            return;
        }

        // Inject nearby NPC names so evil/dark characters can develop grudges against real neighbours
        if (nearbyNpcs != null && !nearbyNpcs.isBlank()) {
            contextData.put("nearby_characters", "Nearby Characters (can be referenced in Likes/Dislikes/Background/Grudge): " + nearbyNpcs);
        } else {
            contextData.put("nearby_characters", "");
        }

        // fetch HTTP response from ChatGPT
        // Character sheets need ~500 tokens — much more than the 60-token chat limit in config.
        ChatGPTRequest.fetchMessageFromChatGPT(config, promptText, contextData, previousMessages, false, 500).thenAccept(output_message -> {
            try {
                if (output_message != null) {
                    // Character Sheet: Remove system-character message from previous messages
                    previousMessages.clear();

                    // Add NEW CHARACTER sheet & greeting (or respond directly to the player's
                    // first words if they spoke before generation finished).
                    this.characterSheet = output_message;
                    String pending = this.pendingVoiceMessage;
                    this.pendingVoiceMessage = null;
                    if (pending != null && !pending.isBlank()) {
                        // Player already spoke — skip the canned greeting and respond in character
                        // directly to their words. This prevents the mob from "speaking twice".
                        this.generateMessage(userLanguage, player, pending, is_auto_message);
                    } else {
                        // Player hasn't said anything yet — show the short intro greeting.
                        String shortGreeting = Optional.ofNullable(getCharacterProp("short greeting")).filter(s -> !s.isEmpty() && !s.equalsIgnoreCase("N/A")).orElse(Randomizer.getRandomNoResponse().comp().getString()).replace("\n", " ");
                        this.addMessage(shortGreeting, ChatDataManager.ChatSender.ASSISTANT, player, systemPrompt);
                    }

                } else {
                    // No valid LLM response
                    throw new RuntimeException(ChatGPTRequest.lastErrorMessage);
                }

            } catch (Exception e) {
                // Log the exception for debugging
                LOGGER.error("Error processing LLM response", e);

                Randomizer.ErrorType type = Randomizer.ErrorType.GENERAL;
                int code = ChatGPTRequest.lastErrorCode;
                if (code == -1) {
                    type = Randomizer.ErrorType.CONNECTION;
                } else if (code == 401 || (config.getApiKey() == null || config.getApiKey().isEmpty())) {
                    type = Randomizer.ErrorType.CODE401;
                } else if (code == 403) {
                    type = Randomizer.ErrorType.CODE403;
                } else if (code == 429) {
                    type = Randomizer.ErrorType.CODE429;
                } else if (code == 500) {
                    type = Randomizer.ErrorType.CODE500;
                } else if (code == 503) {
                    type = Randomizer.ErrorType.CODE503;
                }
                Component link = Component.literal(Randomizer.DISCORD_LINK)
                        .withStyle(ChatFormatting.BLUE)
                        .withStyle(style -> style
                                .withClickEvent(ClickEventHelper.openUrl("https://" + Randomizer.DISCORD_LINK))
                                .withUnderlined(true));

                TR randomError = Randomizer.getRandomError(type);
                MutableComponent randomComp = randomError.comp(link);
                this.addMessage(randomComp.getString(), ChatDataManager.ChatSender.ASSISTANT, player, systemPrompt, false);

                MutableComponent errorComp = ERROR_PREFIX.comp();
                if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                    errorComp.append(Component.literal(truncateString(e.getMessage(), 50)));
                }
                player.displayClientMessage(errorComp.withStyle(ChatFormatting.RED), false);

                // Remove the error message from history to prevent it from affecting future ChatGPT requests
                if (!previousMessages.isEmpty()) {
                    previousMessages.remove(previousMessages.size() - 1);
                }

                TR solution = getSolutionMessage(code);
                if (solution != null) {
                    player.displayClientMessage(solution.comp().withStyle(ChatFormatting.BLUE), false);
                }

                player.displayClientMessage(INFO_HELP_LINK.comp(link), false);
            }
        });
    }

    // Generate greeting
    /** Overload used by the character-sheet generation callback — no slot to release. */
    public void generateMessage(String userLanguage, ServerPlayer player, String userMessage, boolean is_auto_message) {
        generateMessage(userLanguage, player, userMessage, is_auto_message, false);
    }

    /**
     * Start an LLM chat call for this entity.
     *
     * @param releaseOnComplete when {@code true}, calls
     *   {@link com.owlmaddie.network.ServerPackets#releaseAndTryNext()} when the
     *   response (or error) arrives, releasing one slot in the chat concurrency queue.
     *   Pass {@code true} when called from {@code generate_chat}; {@code false} when
     *   called from the character-sheet generation callback (that path has no slot).
     */
    public void generateMessage(String userLanguage, ServerPlayer player, String userMessage, boolean is_auto_message, boolean releaseOnComplete) {
        String systemPrompt = "system-chat";
        if (is_auto_message) {
            // Increment an auto-generated message
            this.auto_generated++;
        } else {
            // Reset auto-generated counter
            this.auto_generated = 0;
        }

        // Add USER Message
        this.addMessage(userMessage, ChatDataManager.ChatSender.USER, player, systemPrompt);

        // Get config (api key, url, settings)
        ConfigurationHandler.Config config = new ConfigurationHandler(ServerPackets.serverInstance).loadConfig();
        String promptText = ChatPrompt.loadPromptFromResource(ServerPackets.serverInstance.getResourceManager(), systemPrompt);

        // Add PLAYER context information
        Map<String, String> contextData = getPlayerContext(player, userLanguage, config);
        if (contextData == null) {
            // Entity despawned between trigger and now — abort silently
            this.status = ChatDataManager.ChatStatus.DISPLAY;
            return;
        }

        // Apply the entity-type speaking style constraint (word limits, speech patterns).
        // Injected into STRICT OUTPUT RULES (chat_style_note) so the LLM sees it as a
        // mandatory rule before anything else, and also appended to entity_speaking_style
        // as belt-and-suspenders. This ensures zombie stays zombie-like, piglin stays
        // terse, etc., regardless of whatever random speaking style the character sheet rolled.
        if (this.chatStyleNote != null) {
            contextData.put("chat_style_note",
                    "- SPEAKING STYLE OVERRIDE (MANDATORY): " + this.chatStyleNote + "\n");
            String existingStyle = contextData.getOrDefault("entity_speaking_style", "");
            contextData.put("entity_speaking_style", existingStyle + " " + this.chatStyleNote);
        } else {
            contextData.put("chat_style_note", "");
        }

        // If this entity speaks a foreign language, inject the subtitle instruction so every
        // chat response includes [EN: English translation] for the bubble to display.
        if (this.subtitleMode && this.nativeLanguage != null) {
            // Reinforce the subtitle rule inside entity_speaking_style so the LLM sees it
            // in the entity persona section as well as the output rules section.
            String existingStyle = contextData.getOrDefault("entity_speaking_style", "");
            contextData.put("entity_speaking_style",
                    existingStyle + " ALWAYS append [EN: English translation] after every response.");

            contextData.put("subtitle_rule",
                    "- SUBTITLE RULE (MANDATORY): Respond ONLY in " + this.nativeLanguage + "." +
                    " EVERY response MUST end with [EN: English translation] before any behavior tags." +
                    " NEVER omit this. Example: こんにちは！ [EN: Hello!] <FRIENDSHIP 1>");

            // Few-shot examples showing the exact [EN: ...] format the LLM must follow.
            // These are the strongest signal — the LLM copies demonstrated patterns reliably.
            contextData.put("subtitle_examples",
                    "\nSubtitle format examples (follow this format for EVERY response):\n" +
                    "PLAYER: Hello!\n" +
                    "ENTITY: こんにちは！ [EN: Hello!] <FRIENDSHIP 1>\n\n" +
                    "PLAYER: What do you want?\n" +
                    "ENTITY: 何が欲しいの？ [EN: What do you want?]\n\n" +
                    "PLAYER: Leave me alone!\n" +
                    "ENTITY: わかった。 [EN: Understood.] <FRIENDSHIP -1>\n\n" +
                    "PLAYER: Follow me!\n" +
                    "ENTITY: ついて行く。 [EN: I will follow.] <FOLLOW>");
        } else {
            contextData.put("subtitle_rule", "");
            contextData.put("subtitle_examples", "");
        }

        // Get messages for player
        PlayerData playerData = this.getPlayerData(player.getDisplayName().getString());
        if (previousMessages.size() == 1) {
            // No messages exist yet for this player (start with normal greeting)
            String shortGreeting = Optional.ofNullable(getCharacterProp("short greeting")).filter(s -> !s.isEmpty() && !s.equalsIgnoreCase("N/A")).orElse(Randomizer.getRandomNoResponse().comp().getString()).replace("\n", " ");
            previousMessages.add(0, new ChatMessage(shortGreeting, ChatDataManager.ChatSender.ASSISTANT, player.getDisplayName().getString()));
        }

        // Capture the current generation ID before we fire the async HTTP request.
        // If another LLM call starts for this same entity before this one returns, the counter
        // advances and this response will be discarded inside the callback below.
        // Gson deserializes via Unsafe.allocateInstance(), bypassing field initializers,
        // so transient fields may be null on entities loaded from chatdata.json.
        if (this.llmGenId == null) this.llmGenId = new java.util.concurrent.atomic.AtomicInteger(0);
        final int myGenId = this.llmGenId.incrementAndGet();

        // fetch HTTP response from ChatGPT
        ChatGPTRequest.fetchMessageFromChatGPT(config, promptText, contextData, previousMessages, false).thenAccept(output_message -> {
            try {
            // Stale-response guard: if a newer LLM call fired while we were waiting (e.g. a
            // player message arrived during a witness-reaction delay), discard this result.
            // We still fall through to the finally block to release the chat slot.
            if (this.llmGenId.get() != myGenId) {
                LOGGER.info("Discarding stale LLM response for entity {} (gen {} superseded by {})",
                        entityId, myGenId, this.llmGenId.get());
                return;
            }
            try {
                if (output_message != null) {
                    // Chat Message: Parse message for behaviors
                    ParsedMessage result = MessageParser.parseMessage(output_message.replace("\n", " "));
                    Mob entity = (Mob) ServerEntityFinder.getEntityByUUID((ServerLevel)player.level(), UUID.fromString(entityId));

                    if (entity != null) {
                        // Determine entity's default speed
                        // Some Entities (i.e. Axolotl) set this incorrectly... so adjusting in the SpeedControls class
                        float entitySpeed = SpeedControls.getMaxSpeed(entity);
                        float entitySpeedMedium = Mth.clamp(entitySpeed * 1.15F, 0.5f, 1.15f);
                        float entitySpeedFast = Mth.clamp(entitySpeed * 1.3F, 0.5f, 1.3f);

                        // Apply behaviors (if any)
                        for (Behavior behavior : result.getBehaviors()) {
                            LOGGER.info("Behavior: " + behavior.getName() + (behavior.getArgument() != null ?
                                    ", Argument: " + behavior.getArgument() : ""));

                            // Apply behaviors to entity
                            if (behavior.getName().equals("FOLLOW")) {
                                FollowPlayerGoal followGoal = new FollowPlayerGoal(player, entity, entitySpeedMedium);
                                EntityBehaviorManager.removeGoal(entity, TalkPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, AttackPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, LeadPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, StayGoal.class); // release stand-still lock
                                EntityBehaviorManager.addGoal(entity, followGoal, GoalPriority.FOLLOW_PLAYER);
                                if (playerData.attacking) {
                                    AdvancementHelper.calmTheStorm(player);
                                    playerData.attacking = false;
                                }
                                playerData.fleeing = false;
                                AdvancementHelper.follow(player);
                                if (playerData.friendship >= 0) {
                                    ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) FOLLOW_FRIEND_PARTICLE, 0.5, 1);
                                } else {
                                    ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) FOLLOW_ENEMY_PARTICLE, 0.5, 1);
                                }

                            } else if (behavior.getName().equals("UNFOLLOW")) {
                                EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);
                                // Lock in place so the mob doesn't resume wandering on its own
                                EntityBehaviorManager.addGoal(entity, new StayGoal(entity), GoalPriority.STAY_PLAYER);

                            } else if (behavior.getName().equals("FLEE")) {
                                float fleeDistance = 40F;
                                FleePlayerGoal fleeGoal = new FleePlayerGoal(player, entity, entitySpeedFast, fleeDistance);
                                EntityBehaviorManager.removeGoal(entity, TalkPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, AttackPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, ProtectPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, LeadPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, StayGoal.class); // release stand-still lock
                                EntityBehaviorManager.addGoal(entity, fleeGoal, GoalPriority.FLEE_PLAYER);
                                ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) FLEE_PARTICLE, 0.5, 1);
                                playerData.fleeing = true;
                                if (playerData.attacking) {
                                    AdvancementHelper.calmTheStorm(player);
                                    playerData.attacking = false;
                                }

                            } else if (behavior.getName().equals("UNFLEE")) {
                                EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                // Lock in place — mob stopped fleeing on command, should stay put
                                EntityBehaviorManager.addGoal(entity, new StayGoal(entity), GoalPriority.STAY_PLAYER);
                                if (playerData.fleeing) {
                                    AdvancementHelper.standYourGround(player);
                                    playerData.fleeing = false;
                                }

                            } else if (behavior.getName().equals("ATTACK")) {
                                AttackPlayerGoal attackGoal = new AttackPlayerGoal(player, entity, entitySpeedFast);
                                EntityBehaviorManager.removeGoal(entity, TalkPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, ProtectPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, LeadPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, StayGoal.class); // release stand-still lock
                                EntityBehaviorManager.addGoal(entity, attackGoal, GoalPriority.ATTACK_PLAYER);
                                ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) FLEE_PARTICLE, 0.5, 1);
                                playerData.attacking = true;

                            } else if (behavior.getName().equals("PROTECT")) {
                                if (playerData.friendship <= 0) {
                                    // force friendship to prevent entity from attacking player when protecting
                                    playerData.friendship = 1;
                                }
                                ProtectPlayerGoal protectGoal = new ProtectPlayerGoal(player, entity, 1.0);
                                EntityBehaviorManager.removeGoal(entity, TalkPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, AttackPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, StayGoal.class); // release stand-still lock
                                EntityBehaviorManager.addGoal(entity, protectGoal, GoalPriority.PROTECT_PLAYER);
                                if (playerData.attacking) {
                                    AdvancementHelper.calmTheStorm(player);
                                    playerData.attacking = false;
                                }
                                playerData.fleeing = false;
                                AdvancementHelper.bodyguard(player);
                                if (entity.getType() == net.minecraft.world.entity.EntityType.PIG && playerData.friendship == 3) {
                                    playerData.pigProtect = true;
                                    ItemStack main = entity.getMainHandItem();
                                    ItemStack off = entity.getOffhandItem();
                                    if (main.getItem() == Items.DIAMOND_SWORD || main.getItem() == Items.NETHERITE_SWORD ||
                                            off.getItem() == Items.DIAMOND_SWORD || off.getItem() == Items.NETHERITE_SWORD) {
                                        AdvancementHelper.aLegend(player);
                                        playerData.pigProtect = false;
                                    }
                                }
                                ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) PROTECT_PARTICLE, 0.5, 1);

                            } else if (behavior.getName().equals("UNPROTECT")) {
                                EntityBehaviorManager.removeGoal(entity, ProtectPlayerGoal.class);

                            } else if (behavior.getName().equals("LEAD")) {
                                // --- Destination routing ---
                                // The LLM outputs <LEAD> or <LEAD [destination]>.
                                // We resolve the destination to a BlockPos (or mob entity) and launch
                                // GoToPositionGoal (or GoToEntityGoal for NPC targets).
                                String dest = behavior.getStringArgument() != null
                                        ? behavior.getStringArgument().trim().toLowerCase(java.util.Locale.ENGLISH)
                                        : "";
                                ServerLevel leadLevel = (ServerLevel) entity.level();
                                BlockPos leadOrigin   = entity.blockPosition();

                                // Lambda captures for the arrival callback
                                final EntityChatData selfData   = this;
                                final ServerPlayer   leadPlayer = player;

                                // Resolve the destination to a NavigationTarget (pos or entity)
                                BlockPos destPos    = null;
                                net.minecraft.world.entity.Mob destMob = null;

                                if (dest.contains("outside") || dest.contains("exit") || dest.contains("outdoors")
                                        || dest.contains("leave") || dest.contains("open air")) {
                                    destPos = findNearestOutsidePos(leadLevel, leadOrigin);
                                    if (destPos == null) LOGGER.warn("LEAD: no outside position found within 30 blocks");
                                } else if (dest.contains("water") || dest.contains("lake") || dest.contains("river")
                                        || dest.contains("ocean") || dest.contains("pond") || dest.contains("well")) {
                                    destPos = findNearestWaterPos(leadLevel, leadOrigin);
                                    if (destPos == null) LOGGER.warn("LEAD: no water found within 50 blocks");
                                } else if (dest.contains("portal") || dest.contains("nether")) {
                                    destPos = findNearestPortalPos(leadLevel, leadOrigin);
                                    if (destPos == null) LOGGER.warn("LEAD: no nether portal found within 80 blocks");
                                } else if (dest.contains("chest") || dest.contains("container") || dest.contains("storage")
                                        || dest.contains("barrel") || dest.contains("furnace")) {
                                    destPos = findNearestContainerPos(leadLevel, leadOrigin);
                                    if (destPos == null) LOGGER.warn("LEAD: no container found within 30 blocks");
                                } else if (dest.contains("village") || dest.contains("outpost") || dest.contains("temple")
                                        || dest.contains("stronghold") || dest.contains("mineshaft") || dest.contains("mansion")
                                        || dest.contains("monument") || dest.contains("shipwreck") || dest.contains("fortress")
                                        || dest.contains("bastion") || dest.contains("igloo") || dest.contains("ancient")
                                        || dest.contains("trial") || dest.contains("ruined")) {
                                    destPos = findNearestStructurePos(leadLevel, leadOrigin, dest);
                                    if (destPos == null) LOGGER.warn("LEAD: no structure matching '{}' found within 200 blocks", dest);
                                } else if (!dest.isEmpty() && !dest.equals("building") && !dest.equals("house")) {
                                    // Check if the destination matches a known nearby NPC name
                                    AABB npcSearch = entity.getBoundingBox().inflate(200.0);
                                    destMob = findBestNamedMob(dest, entity, leadLevel, npcSearch);
                                    if (destMob == null) {
                                        // Unknown destination — fall back to nearest building
                                        LOGGER.warn("LEAD: destination '{}' not matched to NPC or known location — falling back to building", dest);
                                        destPos = findNearestBuildingPos(leadLevel, leadOrigin);
                                    }
                                } else {
                                    // Default: building / house
                                    destPos = findNearestBuildingPos(leadLevel, leadOrigin);
                                }

                                // Clear competing goals — including any in-progress SPEAK_TO or
                                // previous LEAD navigation. Without removing GoToEntityGoal, a
                                // SPEAK_TO delivery goal and a new LEAD goal can both be active at
                                // the same priority, causing them to both fire arrival callbacks at
                                // the same location and produce conflicting "I'm delivering / I'm
                                // arriving" messages simultaneously.
                                EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, AttackPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, StayGoal.class);
                                EntityBehaviorManager.removeGoal(entity, LeadPlayerGoal.class);
                                EntityBehaviorManager.removeGoal(entity, com.owlmaddie.goals.GoToEntityGoal.class);
                                EntityBehaviorManager.removeGoal(entity, com.owlmaddie.goals.GoToPositionGoal.class);

                                if (destMob != null) {
                                    // Navigate to a specific NPC
                                    final net.minecraft.world.entity.Mob finalDestMob = destMob;
                                    Runnable onNpcArrival = () -> {
                                        if (!selfData.characterSheet.isEmpty()
                                                && selfData.status != com.owlmaddie.chat.ChatDataManager.ChatStatus.PENDING) {
                                            com.owlmaddie.network.ServerPackets.generate_chat(
                                                    "English", selfData, leadPlayer, entity,
                                                    "<arrives near " + finalDestMob.getDisplayName().getString()
                                                            + ", turns to face the player>", true);
                                        }
                                        // No StayGoal — mob resumes normal behavior after leading the player
                                    };
                                    EntityBehaviorManager.addGoal(entity,
                                            new com.owlmaddie.goals.GoToEntityGoal(entity, destMob, entitySpeedMedium, onNpcArrival),
                                            GoalPriority.LEAD_PLAYER);
                                    LOGGER.info("LEAD: {} heading toward NPC '{}'", entity.getType().toShortString(),
                                            destMob.getDisplayName().getString());

                                } else if (destPos != null) {
                                    // Navigate to a BlockPos
                                    final BlockPos finalDest = destPos;
                                    Runnable onPosArrival = () -> {
                                        if (!selfData.characterSheet.isEmpty()
                                                && selfData.status != com.owlmaddie.chat.ChatDataManager.ChatStatus.PENDING) {
                                            com.owlmaddie.network.ServerPackets.generate_chat(
                                                    "English", selfData, leadPlayer, entity,
                                                    "<arrives at destination, turns to face the player>", true);
                                        }
                                    };
                                    EntityBehaviorManager.addGoal(entity,
                                            new GoToPositionGoal(entity, finalDest, entitySpeedMedium, onPosArrival),
                                            GoalPriority.LEAD_PLAYER);
                                    LOGGER.info("LEAD: {} heading toward {} at {}", entity.getType().toShortString(), dest.isEmpty() ? "building" : dest, finalDest);
                                } else {
                                    LOGGER.warn("LEAD: no destination found for '{}' — mob stays put", dest);
                                }

                                if (playerData.attacking) {
                                    AdvancementHelper.calmTheStorm(player);
                                    playerData.attacking = false;
                                }
                                playerData.fleeing = false;
                                AdvancementHelper.lead(player);
                                if (playerData.friendship >= 0) {
                                    ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) LEAD_FRIEND_PARTICLE, 0.5, 1);
                                } else {
                                    ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) LEAD_ENEMY_PARTICLE, 0.5, 1);
                                }
                            } else if (behavior.getName().equals("RESUME")) {
                                // Remove StayGoal so the mob returns to its native AI (wandering, etc.).
                                // StayGoal.stop() removes the entity from STAYING_MOBS automatically,
                                // which also kills the Brain/navigation override in NpcLifeManager.
                                EntityBehaviorManager.removeGoal(entity, StayGoal.class);

                            } else if (behavior.getName().equals("UNLEAD")) {
                                EntityBehaviorManager.removeGoal(entity, LeadPlayerGoal.class);
                                // Lock in place after done leading — mob shouldn't wander off
                                EntityBehaviorManager.addGoal(entity, new StayGoal(entity), GoalPriority.STAY_PLAYER);

                            } else if (behavior.getName().equals("EXPLODE")) {
                                // Trigger a creeper explosion — only works on Creeper entities.
                                // We use the vanilla Level.explode() API directly so it plays the
                                // full blast animation, destroys blocks, and deals damage just like
                                // a normal creeper detonation, then remove the entity.
                                if (entity instanceof net.minecraft.world.entity.monster.Creeper creeper) {
                                    boolean powered = creeper.isPowered();
                                    float radius = powered ? 6.0F : 3.0F;
                                    entity.level().explode(
                                            creeper,
                                            creeper.getX(), creeper.getY(), creeper.getZ(),
                                            radius,
                                            net.minecraft.world.level.Level.ExplosionInteraction.MOB
                                    );
                                    creeper.discard();
                                }

                            } else if (behavior.getName().equals("ATTACK_NPC")) {
                                // Make this mob attack a specific named NPC nearby.
                                // We reuse AttackPlayerGoal — it accepts any LivingEntity, not just
                                // players. This means ANY mob (villager, chicken, etc.) can attack
                                // another mob the same way it attacks the player when <ATTACK> fires.
                                String targetName = behavior.getStringArgument();
                                if (targetName != null && !targetName.isBlank()) {
                                    AABB searchBox = entity.getBoundingBox().inflate(200.0);
                                    Mob foundTarget = findBestNamedMob(targetName, entity, (ServerLevel) entity.level(), searchBox);
                                    if (foundTarget != null) {
                                        // forceAttack=true bypasses the native-attack check that
                                        // normally gates the goal for monsters. Without it, a zombie
                                        // or skeleton commanded to attack a cow would have
                                        // isGoalActive() return false because canAttack(cow)=true
                                        // makes hasNativeAttacksButCannotTarget=false.
                                        AttackPlayerGoal attackNpcGoal = new AttackPlayerGoal(foundTarget, entity, entitySpeedFast, true);
                                        EntityBehaviorManager.removeGoal(entity, TalkPlayerGoal.class);
                                        EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);
                                        EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                        EntityBehaviorManager.removeGoal(entity, ProtectPlayerGoal.class);
                                        EntityBehaviorManager.removeGoal(entity, LeadPlayerGoal.class);
                                        EntityBehaviorManager.removeGoal(entity, StayGoal.class);
                                        EntityBehaviorManager.addGoal(entity, attackNpcGoal, GoalPriority.ATTACK_PLAYER);
                                        LOGGER.info("ATTACK_NPC: {} targeting {}", entity.getType().toShortString(), targetName);
                                    } else {
                                        LOGGER.info("ATTACK_NPC: could not find '{}' near {} (searched 200 blocks)", targetName, entity.getType().toShortString());
                                    }
                                }

                            } else if (behavior.getName().equals("GIVE_ITEM")) {
                                // The mob gives the player an item by dropping it at its own feet.
                                // Format: <GIVE_ITEM emerald> or <GIVE_ITEM emerald 2>
                                // Works for any mob type — villagers can also drop items this way,
                                // independently of the trade UI.
                                String giveArg = behavior.getStringArgument();
                                if (giveArg != null && !giveArg.isBlank()) {
                                    String[] parts = giveArg.trim().split("\\s+");
                                    int giveCount = 1;
                                    String itemName;
                                    // If last token is a number, treat it as quantity
                                    if (parts.length > 1) {
                                        try {
                                            giveCount = Math.max(1, Math.min(64, Integer.parseInt(parts[parts.length - 1])));
                                            itemName = java.util.Arrays.stream(parts, 0, parts.length - 1)
                                                    .collect(Collectors.joining("_")).toLowerCase();
                                        } catch (NumberFormatException ex) {
                                            itemName = java.util.Arrays.stream(parts, 0, parts.length)
                                                    .collect(Collectors.joining("_")).toLowerCase();
                                        }
                                    } else {
                                        itemName = parts[0].toLowerCase();
                                    }
                                    // Normalize: spaces/hyphens → underscores, strip minecraft: prefix if present
                                    itemName = itemName.replace("-", "_").replace(" ", "_")
                                            .replaceFirst("^minecraft:", "");
                                    net.minecraft.resources.ResourceLocation rl =
                                            net.minecraft.resources.ResourceLocation.tryParse("minecraft:" + itemName);
                                    if (rl != null && entity.level() instanceof ServerLevel giveLevel) {
                                        java.util.Optional<net.minecraft.world.item.Item> optItem =
                                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(rl);
                                        if (optItem.isPresent() && optItem.get() != net.minecraft.world.item.Items.AIR) {
                                            ItemStack gift = new ItemStack(optItem.get(), giveCount);
                                            // Spawn item entity at the mob's feet — player walks over to pick it up
                                            net.minecraft.world.entity.item.ItemEntity dropped =
                                                    new net.minecraft.world.entity.item.ItemEntity(
                                                            giveLevel,
                                                            entity.getX(), entity.getY() + 0.5, entity.getZ(),
                                                            gift);
                                            dropped.setDefaultPickUpDelay();
                                            // Toss the item toward the player so it rolls their way
                                            double dx = player.getX() - entity.getX();
                                            double dz = player.getZ() - entity.getZ();
                                            double dist = Math.sqrt(dx * dx + dz * dz);
                                            if (dist > 0) {
                                                dropped.setDeltaMovement(dx / dist * 0.3, 0.3, dz / dist * 0.3);
                                            }
                                            giveLevel.addFreshEntity(dropped);
                                            LOGGER.info("GIVE_ITEM: {} gave {}x {}", entity.getType().toShortString(), giveCount, itemName);
                                        } else {
                                            LOGGER.warn("GIVE_ITEM: unknown item '{}' — LLM hallucinated an item name", itemName);
                                        }
                                    }
                                }

                            } else if (behavior.getName().equals("RECEIVE_ITEM")) {
                                // The player gives the mob an item by having it removed from their
                                // inventory. Format: <RECEIVE_ITEM emerald> or <RECEIVE_ITEM emerald 2>
                                // The mob "accepts" the payment; the items disappear from the player's hand.
                                String receiveArg = behavior.getStringArgument();
                                if (receiveArg != null && !receiveArg.isBlank()) {
                                    String[] parts = receiveArg.trim().split("\\s+");
                                    int receiveCount = 1;
                                    String itemName;
                                    if (parts.length > 1) {
                                        try {
                                            receiveCount = Math.max(1, Math.min(64, Integer.parseInt(parts[parts.length - 1])));
                                            itemName = java.util.Arrays.stream(parts, 0, parts.length - 1)
                                                    .collect(Collectors.joining("_")).toLowerCase();
                                        } catch (NumberFormatException ex) {
                                            itemName = java.util.Arrays.stream(parts, 0, parts.length)
                                                    .collect(Collectors.joining("_")).toLowerCase();
                                        }
                                    } else {
                                        itemName = parts[0].toLowerCase();
                                    }
                                    itemName = itemName.replace("-", "_").replace(" ", "_")
                                            .replaceFirst("^minecraft:", "");
                                    net.minecraft.resources.ResourceLocation rl =
                                            net.minecraft.resources.ResourceLocation.tryParse("minecraft:" + itemName);
                                    if (rl != null) {
                                        java.util.Optional<net.minecraft.world.item.Item> optItem =
                                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(rl);
                                        if (optItem.isPresent() && optItem.get() != net.minecraft.world.item.Items.AIR) {
                                            net.minecraft.world.item.Item wantedItem = optItem.get();
                                            net.minecraft.world.entity.player.Inventory inv = player.getInventory();
                                            int remaining = receiveCount;
                                            // Walk every inventory slot and shrink matching stacks
                                            for (int slot = 0; slot < inv.getContainerSize() && remaining > 0; slot++) {
                                                ItemStack stack = inv.getItem(slot);
                                                if (stack.is(wantedItem)) {
                                                    int take = Math.min(stack.getCount(), remaining);
                                                    stack.shrink(take);
                                                    remaining -= take;
                                                }
                                            }
                                            int taken = receiveCount - remaining;
                                            if (taken > 0) {
                                                // Add received items to the mob's actual inventory
                                                net.minecraft.world.Container mobInv =
                                                        ((com.owlmaddie.inventory.ChatInventory) entity).creaturechat$getInventory();
                                                ItemStack received = new ItemStack(wantedItem, taken);
                                                // Try to stack into existing slots first, then empty slots
                                                for (int s = 0; s < mobInv.getContainerSize() && !received.isEmpty(); s++) {
                                                    ItemStack existing = mobInv.getItem(s);
                                                    if (existing.isEmpty()) {
                                                        mobInv.setItem(s, received.copy());
                                                        received.setCount(0);
                                                    } else if (existing.is(wantedItem) && existing.getCount() < existing.getMaxStackSize()) {
                                                        int add = Math.min(received.getCount(), existing.getMaxStackSize() - existing.getCount());
                                                        existing.grow(add);
                                                        received.shrink(add);
                                                    }
                                                }
                                            }
                                            if (remaining == 0) {
                                                LOGGER.info("RECEIVE_ITEM: {} took {}x {} from player", entity.getType().toShortString(), receiveCount, itemName);
                                            } else {
                                                // Player didn't have enough — took what was available
                                                LOGGER.info("RECEIVE_ITEM: player had insufficient {}; took {}/{}", itemName, receiveCount - remaining, receiveCount);
                                            }
                                        } else {
                                            LOGGER.warn("RECEIVE_ITEM: unknown item '{}' — LLM hallucinated an item name", itemName);
                                        }
                                    }
                                }

                            } else if (behavior.getName().equals("RENAME")) {
                                // Rename the mob to whatever the player requested, update the nameplate
                                // above its head, and update the character sheet so it knows its new name.
                                String newName = behavior.getStringArgument();
                                if (newName != null && !newName.isBlank()) {
                                    newName = newName.trim();
                                    // Update the entity nameplate (always visible once set)
                                    entity.setCustomName(net.minecraft.network.chat.Component.literal(newName));
                                    entity.setCustomNameVisible(true);
                                    // Update the character sheet so future LLM calls see the new name
                                    String finalNewName = newName;
                                    characterSheet = characterSheet.replaceFirst(
                                            "(?i)(- ?Name:\\s*).*",
                                            "$1" + java.util.regex.Matcher.quoteReplacement(finalNewName));
                                    LOGGER.info("RENAME: {} renamed to '{}'", entity.getType().toShortString(), finalNewName);
                                }

                            } else if (behavior.getName().equals("SPEAK_TO")) {
                                // The messenger NPC walks to a named target NPC (up to 200 blocks away),
                                // delivers the message on arrival, then walks back to the player and
                                // reports what happened — so the player can hear the result via TTS.
                                String targetName = behavior.getStringArgument();
                                if (targetName != null && !targetName.isBlank()) {
                                    AABB searchBox = entity.getBoundingBox().inflate(200.0);
                                    Mob foundTarget = findBestNamedMob(targetName, entity, (ServerLevel) entity.level(), searchBox);
                                    if (foundTarget != null) {
                                        // Use the player's actual last message as the delivery intent.
                                        // result.getCleanedMessage() is Bly's travel confirmation to the player
                                        // ("I know where Edgar lives. I'll go tell him.") — not message content.
                                        // The player's last USER message IS what they wanted delivered.
                                        // Iterate backward to find it (ASSISTANT response not yet added).
                                        String playerLastMsg = "";
                                        for (int pi = previousMessages.size() - 1; pi >= 0; pi--) {
                                            ChatMessage cm = previousMessages.get(pi);
                                            if (cm.sender == ChatDataManager.ChatSender.USER) {
                                                playerLastMsg = cm.message;
                                                break;
                                            }
                                        }
                                        if (playerLastMsg.isEmpty()) playerLastMsg = result.getCleanedMessage();
                                        String messengerWords = playerLastMsg;

                                        String messengerName = com.owlmaddie.npc.NpcLifeManager
                                                .extractMobNamePublic(this, entity);

                                        // Capture final references for use inside the lambdas
                                        final Mob          deliverTo      = foundTarget;
                                        final String       deliverToName  = foundTarget.getDisplayName().getString();
                                        final String       intent         = messengerWords;
                                        final String       mName          = messengerName;
                                        final ServerPlayer pRef           = player;
                                        final float        speed          = entitySpeedMedium;

                                        // --- onArrival: fires when messenger reaches the target NPC ---
                                        Runnable onArrival = () -> {
                                            EntityChatData targetData = ChatDataManager.getServerInstance()
                                                    .getOrCreateChatData(deliverTo.getStringUUID());

                                            // Determine what happened at the target, so we can report back
                                            String returnTrigger;

                                            boolean targetIsFighting = deliverTo.getTarget() != null;
                                            if (targetIsFighting) {
                                                // Target is in combat — skip delivery entirely so we don't
                                                // interrupt their fight. Messenger will report back to player.
                                                returnTrigger = "<just tried to deliver a message to "
                                                        + deliverToName + " on behalf of "
                                                        + pRef.getDisplayName().getString()
                                                        + " but they were in the middle of a fight and couldn't be reached>";
                                                LOGGER.info("SPEAK_TO: {} skipped delivery to {} — target is fighting",
                                                        mName, deliverToName);

                                            } else if (!targetData.characterSheet.isEmpty()
                                                    && targetData.status != ChatDataManager.ChatStatus.PENDING) {
                                                // Deliver the message — target NPC will react and speak.
                                                // Format: "Bly arrived to pass on a message from Player695,
                                                // who said: [player's actual words]" — Edgar gets the real content.
                                                String deliveryTrigger = "<" + mName
                                                        + " arrived to pass on a message from "
                                                        + pRef.getDisplayName().getString()
                                                        + ", who said: " + intent + ">";
                                                LOGGER.info("SPEAK_TO: {} delivered message to {}",
                                                        mName, deliverToName);
                                                // is_auto_message=false so this bypasses the token-bucket
                                                // rate limiter — delivery is player-directed, not NPC initiative.
                                                ServerPackets.generate_chat("English", targetData,
                                                        pRef, deliverTo, deliveryTrigger, false);
                                                // Prompt the messenger to describe how Edgar reacted —
                                                // this plays as TTS at the player's location when Bly returns,
                                                // so the player hears a summary of the exchange even if they
                                                // were too far from Edgar to hear Edgar's response directly.
                                                returnTrigger = "<just returned from delivering a message to "
                                                        + deliverToName + " for " + pRef.getDisplayName().getString()
                                                        + ". Briefly describe in your own words how "
                                                        + deliverToName + " seemed to react — were they pleased,"
                                                        + " annoyed, confused, or indifferent? One sentence only.>";

                                            } else {
                                                // Target had no character sheet or was busy generating
                                                returnTrigger = "<just tried to deliver a message to "
                                                        + deliverToName + " on behalf of "
                                                        + pRef.getDisplayName().getString()
                                                        + " but they didn't seem able to respond>";
                                                LOGGER.info("SPEAK_TO: {} could not deliver to {} — no sheet or PENDING",
                                                        mName, deliverToName);
                                            }

                                            // --- onReturn: fires when messenger gets back to the player ---
                                            // The player hears the messenger's TTS report at the player's location.
                                            final String finalReturnTrigger = returnTrigger;
                                            Runnable onReturn = () -> {
                                                // Delivery mission complete — re-enable overhear
                                                EntityChatData.this.onDeliveryMission = false;

                                                // Only report if not already mid-conversation.
                                                // is_auto_message=false bypasses the rate limiter —
                                                // this is a player-directed action, not NPC initiative.
                                                // generate_chat adds a TalkPlayerGoal to keep the mob
                                                // in place while reporting — no StayGoal needed.
                                                if (EntityChatData.this.status != ChatDataManager.ChatStatus.PENDING) {
                                                    ServerPackets.generate_chat("English",
                                                            EntityChatData.this, pRef, entity,
                                                            finalReturnTrigger, false);
                                                }
                                            };

                                            // Head back to the player — uses LivingEntity target so ServerPlayer works
                                            EntityBehaviorManager.addGoal(entity,
                                                    new com.owlmaddie.goals.GoToEntityGoal(
                                                            entity, pRef, speed, onReturn),
                                                    GoalPriority.LEAD_PLAYER);
                                            LOGGER.info("SPEAK_TO: {} heading back to player after delivery",
                                                    mName);
                                        };

                                        // Mark messenger as on delivery so overhear skips them
                                        EntityChatData.this.onDeliveryMission = true;

                                        EntityBehaviorManager.removeGoal(entity, StayGoal.class);
                                        EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);
                                        EntityBehaviorManager.addGoal(entity,
                                                new com.owlmaddie.goals.GoToEntityGoal(
                                                        entity, foundTarget, entitySpeedMedium, onArrival),
                                                GoalPriority.LEAD_PLAYER);
                                        LOGGER.info("SPEAK_TO: {} heading toward '{}' to deliver message (searching up to 200 blocks)",
                                                entity.getType().toShortString(), targetName);
                                    } else {
                                        LOGGER.info("SPEAK_TO: could not find '{}' near {} (searched 200 blocks)",
                                                targetName, entity.getType().toShortString());
                                    }
                                }

                            } else if (behavior.getName().equals("SET_FIRE")) {
                                // The mob walks toward the nearest building and starts setting it on fire.
                                // SetFireGoal navigates there and ignites flammable blocks (wood, leaves, etc.)
                                // one at a time every 5 ticks — fire then spreads naturally.
                                if (entity.level() instanceof ServerLevel setFireLevel) {
                                    BlockPos buildingPos = findNearestBuildingPos(setFireLevel, entity.blockPosition());
                                    if (buildingPos != null) {
                                        EntityBehaviorManager.removeGoal(entity, StayGoal.class);
                                        EntityBehaviorManager.removeGoal(entity, FollowPlayerGoal.class);
                                        EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                        EntityBehaviorManager.addGoal(entity,
                                                new com.owlmaddie.goals.SetFireGoal(entity, buildingPos, entitySpeedMedium),
                                                GoalPriority.LEAD_PLAYER);
                                        LOGGER.info("SET_FIRE: {} heading toward building at {}", entity.getType().toShortString(), buildingPos);
                                    } else {
                                        LOGGER.info("SET_FIRE: no building found within 50 blocks of {}", entity.getType().toShortString());
                                    }
                                }

                            } else if (behavior.getName().equals("FRIENDSHIP")) {
                                int new_friendship = Math.max(-3, Math.min(3, behavior.getArgument()));
                                int old_friendship = playerData.friendship;

                                // Does friendship improve?
                                if (new_friendship > playerData.friendship) {
                                    // Stop any attack/flee if friendship improves
                                    EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                    EntityBehaviorManager.removeGoal(entity, AttackPlayerGoal.class);

                                    if (entity instanceof WitherBoss && new_friendship == 3) {
                                        // Best friend a Nether and get a NETHER_STAR
                                        WitherBoss wither = (WitherBoss) entity;
                                        ((WitherEntityAccessor) wither).callDropEquipment(entity.level().damageSources().generic(), 1, true);
                                        entity.level().playSound(entity, entity.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.3F, 1.0F);
                                    }

                                    if (entity instanceof EnderDragon && new_friendship == 3) {
                                        // Trigger end of game (friendship always wins!)
                                        EnderDragon dragon = (EnderDragon) entity;

                                        // Emit particles & sound
                                        ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) HEART_BIG_PARTICLE, 3, 200);
                                        entity.level().playSound(entity, entity.blockPosition(), SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 0.3F, 1.0F);
                                        entity.level().playSound(entity, entity.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.5F, 1.0F);

                                        // Check if the game rule for mob loot is enabled
                                        ServerLevel serverWorld = (ServerLevel) entity.level();
                                        boolean doMobLoot = serverWorld.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT);

                                        // If this is the first time the dragon is 'befriended', adjust the XP
                                        int baseXP = 500;
                                        if (dragon.getDragonFight() != null && !dragon.getDragonFight().hasPreviouslyKilledDragon()) {
                                            baseXP = 12000;
                                        }

                                        // If the world is a server world and mob loot is enabled, spawn XP orbs
                                        if (entity.level() instanceof ServerLevel && doMobLoot) {
                                            // Loop to spawn XP orbs
                                            for (int j = 1; j <= 11; j++) {
                                                float xpFraction = (j == 11) ? 0.2F : 0.08F;
                                                int xpAmount = Mth.floor((float) baseXP * xpFraction);
                                                ExperienceOrb.award((ServerLevel) entity.level(), entity.position(), xpAmount);
                                            }
                                        }

                                        // Mark fight as over
                                        dragon.getDragonFight().setDragonKilled(dragon);
                                    }
                                }

                                // Merchant deals (if friendship changes with a Villager
                                if (entity instanceof Villager && playerData.friendship != new_friendship) {
                                    VillagerEntityAccessor villager = (VillagerEntityAccessor) entity;
                                    switch (new_friendship) {
                                        case 3:
                                            GossipTypeHelper.startGossip(villager, player.getUUID(),
                                                    GossipTypeHelper.MAJOR_POSITIVE, 20);
                                            GossipTypeHelper.startGossip(villager, player.getUUID(),
                                                    GossipTypeHelper.MINOR_POSITIVE, 25);
                                            break;
                                        case 2:
                                            GossipTypeHelper.startGossip(villager, player.getUUID(),
                                                    GossipTypeHelper.MINOR_POSITIVE, 25);
                                            break;
                                        case 1:
                                            GossipTypeHelper.startGossip(villager, player.getUUID(),
                                                    GossipTypeHelper.MINOR_POSITIVE, 10);
                                            break;
                                        case -1:
                                            GossipTypeHelper.startGossip(villager, player.getUUID(),
                                                    GossipTypeHelper.MINOR_NEGATIVE, 10);
                                            break;
                                        case -2:
                                            GossipTypeHelper.startGossip(villager, player.getUUID(),
                                                    GossipTypeHelper.MINOR_NEGATIVE, 25);
                                            break;
                                        case -3:
                                            GossipTypeHelper.startGossip(villager, player.getUUID(),
                                                    GossipTypeHelper.MAJOR_NEGATIVE, 20);
                                            GossipTypeHelper.startGossip(villager, player.getUUID(),
                                                    GossipTypeHelper.MINOR_NEGATIVE, 25);
                                            break;
                                    }
                                }


                                // Tame best friends and un-tame worst enemies
                                if (entity instanceof TamableAnimal && playerData.friendship != new_friendship) {
                                    TamableAnimal tamableEntity = (TamableAnimal) entity;
                                    if (new_friendship == 3 && !tamableEntity.isTame()) {
                                        tamableEntity.tame(player);
                                    } else if (new_friendship == -3 && tamableEntity.isTame()) {
                                        TameableHelper.setTamed((TamableAnimal) entity, false);
                                        TameableHelper.clearOwner(tamableEntity);
                                    }
                                }

                                // Emit friendship particles
                                if (playerData.friendship != new_friendship) {
                                    int friendDiff = new_friendship - playerData.friendship;
                                    if (friendDiff > 0) {
                                        // Heart particles
                                        if (new_friendship == 3) {
                                            ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) HEART_BIG_PARTICLE, 0.5, 10);
                                        } else {
                                            ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) HEART_SMALL_PARTICLE, 0.1, 1);
                                        }

                                    } else if (friendDiff < 0) {
                                        // Fire particles
                                        if (new_friendship == -3) {
                                            ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) FIRE_BIG_PARTICLE, 0.5, 10);
                                        } else {
                                            ParticleEmitter.emitCreatureParticle((ServerLevel) entity.level(), entity, (ParticleOptions) FIRE_SMALL_PARTICLE, 0.1, 1);
                                        }
                                    }
                                }

                                playerData.friendship = new_friendship;

                                // If the NPC has turned genuinely hostile (friendship -2 or worse),
                                // release any StayGoal that was previously issued. Without this, a mob
                                // that overhears a threat or has its friendship drop mid-stay will remain
                                // frozen even though it wants to flee or attack. FLEE/ATTACK behaviors
                                // already remove StayGoal themselves, but the LLM sometimes outputs only
                                // FRIENDSHIP -2 without a movement behavior (e.g. Edgar's overhear case).
                                if (new_friendship <= -2 && new_friendship < old_friendship) {
                                    EntityBehaviorManager.removeGoal(entity, StayGoal.class);
                                    LOGGER.info("FRIENDSHIP dropped to {} — releasing StayGoal so NPC can react", new_friendship);
                                }

                                if (playerData.attacking) {
                                    EntityBehaviorManager.removeGoal(entity, AttackPlayerGoal.class);
                                    AdvancementHelper.calmTheStorm(player);
                                    playerData.attacking = false;
                                }
                                if (playerData.fleeing && new_friendship >= 0) {
                                    EntityBehaviorManager.removeGoal(entity, FleePlayerGoal.class);
                                    playerData.fleeing = false;
                                }
                                AdvancementHelper.friendshipChanged(player, playerData, old_friendship, new_friendship, entity);
                            }
                        }
                    }

                    // Get cleaned message (i.e. no <BEHAVIOR> strings)
                    String cleanedMessage = result.getCleanedMessage();
                    if (cleanedMessage.isEmpty()) {
                        cleanedMessage = Randomizer.getRandomNoResponse().comp().getString();
                    }

                    // Add ASSISTANT message to history
                    this.addMessage(cleanedMessage, ChatDataManager.ChatSender.ASSISTANT, player, systemPrompt);

                    // Update the last entry in previousMessages to use the original message
                    this.previousMessages.set(this.previousMessages.size() - 1,
                            new ChatMessage(result.getOriginalMessage(), ChatDataManager.ChatSender.ASSISTANT, player.getDisplayName().getString()));

                } else {
                    // No valid LLM response
                    throw new RuntimeException(ChatGPTRequest.lastErrorMessage);
                }

            } catch (Exception e) {
                // Log the exception for debugging
                LOGGER.error("Error processing LLM response", e);

                Randomizer.ErrorType type = Randomizer.ErrorType.GENERAL;
                int code = ChatGPTRequest.lastErrorCode;
                if (code == -1) {
                    type = Randomizer.ErrorType.CONNECTION;
                } else if (code == 401 || (config.getApiKey() == null || config.getApiKey().isEmpty())) {
                    type = Randomizer.ErrorType.CODE401;
                } else if (code == 403) {
                    type = Randomizer.ErrorType.CODE403;
                } else if (code == 429) {
                    type = Randomizer.ErrorType.CODE429;
                } else if (code == 500) {
                    type = Randomizer.ErrorType.CODE500;
                } else if (code == 503) {
                    type = Randomizer.ErrorType.CODE503;
                }
                Component link = Component.literal(Randomizer.DISCORD_LINK)
                        .withStyle(ChatFormatting.BLUE)
                        .withStyle(style -> style
                                .withClickEvent(ClickEventHelper.openUrl("https://" + Randomizer.DISCORD_LINK))
                                .withUnderlined(true));

                TR randomError = Randomizer.getRandomError(type);
                MutableComponent randomComp = randomError.comp(link);
                this.addMessage(randomComp.getString(), ChatDataManager.ChatSender.ASSISTANT, player, systemPrompt, false);

                MutableComponent errorComp = ERROR_PREFIX.comp();
                if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                    errorComp.append(Component.literal(truncateString(e.getMessage(), 50)));
                }
                player.displayClientMessage(errorComp.withStyle(ChatFormatting.RED), false);

                // Remove the error message from history to prevent it from affecting future ChatGPT requests
                if (!previousMessages.isEmpty()) {
                    previousMessages.remove(previousMessages.size() - 1);
                }

                TR solution = getSolutionMessage(code);
                if (solution != null) {
                    player.displayClientMessage(solution.comp().withStyle(ChatFormatting.BLUE), false);
                }

                player.displayClientMessage(INFO_HELP_LINK.comp(link), false);
            }
            } finally {
                // Always release the chat concurrency slot so the next queued chat can start,
                // regardless of whether this response was stale, successful, or an error.
                if (releaseOnComplete) {
                    com.owlmaddie.network.ServerPackets.releaseAndTryNext();
                }
            }
        });
    }

    public static String truncateString(String input, int maxLength) {
        return input.length() > maxLength ? input.substring(0, maxLength - 3) + "..." : input;
    }

    public static TR getSolutionMessage(int code) {
        return switch (code) {
            case -1 -> SOLUTION_CONNECTION;
            case 0 -> SOLUTION_VERIFY_URL;
            case 401 -> SOLUTION_ADD_KEY;
            case 403 -> SOLUTION_CHECK_REGION;
            case 429 -> SOLUTION_ADD_FUNDS;
            case 500 -> SOLUTION_SERVER_ERROR;
            case 503 -> SOLUTION_TRY_AGAIN;
            default -> null;
        };
    }

    // Add a message to the history and update the current message
    public void addMessage(String message, ChatDataManager.ChatSender sender, ServerPlayer player, String systemPrompt) {
        addMessage(message, sender, player, systemPrompt, true);
    }

    // Internal helper allowing callers to skip advancement triggers
    public void addMessage(String message, ChatDataManager.ChatSender sender, ServerPlayer player, String systemPrompt, boolean triggerAdvancement) {
        // Truncate message (prevent crazy long messages... just in case)
        String truncatedMessage = message.substring(0, Math.min(message.length(), ChatDataManager.MAX_CHAR_IN_USER_MESSAGE));

        // Strip asterisk emote actions from assistant messages (e.g. *adjusts helmet*, *smiles warmly*)
        // Multi-word asterisk phrases are emote actions — remove them entirely.
        // Single-word asterisk wrapping is emphasis (e.g. *really*, *Skeleton*) — keep the word.
        if (sender == ChatDataManager.ChatSender.ASSISTANT) {
            truncatedMessage = truncatedMessage.replaceAll("\\*[^*]*\\s[^*]*\\*\\s*", "").trim(); // multi-word: remove
            truncatedMessage = truncatedMessage.replaceAll("\\*([^*\\s]+)\\*", "$1").trim();       // single-word: unwrap

            // Keep up to 3 complete sentences. Behavior tags (e.g. <LEAD>) are preserved
            // by splitting them off first, truncating the speech, then re-appending.
            // This prevents the model from monologuing while still allowing natural dialogue.
            java.util.regex.Matcher tagMatcher = java.util.regex.Pattern
                    .compile("(\\s*<[^>]+>)+\\s*$").matcher(truncatedMessage);
            String trailingTags = tagMatcher.find() ? tagMatcher.group().trim() : "";
            String speechOnly = tagMatcher.replaceAll("").trim();

            // Find the end of up to 3 sentences. Short exclamations like "Oh!" don't
            // count as a full sentence unless they have 4+ words.
            int cutPoint = -1;
            int sentenceCount = 0;
            java.util.regex.Matcher sentenceMatcher = java.util.regex.Pattern
                    .compile("(?<!\\.)\\.(?!\\.)|[!?]").matcher(speechOnly);
            while (sentenceMatcher.find()) {
                int candidate = sentenceMatcher.end();
                String fragment = speechOnly.substring(0, candidate).trim();
                // Only count as a sentence if the fragment has at least 4 words total
                if (fragment.split("\\s+").length >= 4) {
                    sentenceCount++;
                    cutPoint = candidate;
                    if (sentenceCount >= 3) break;
                }
            }
            if (cutPoint > 0) {
                speechOnly = speechOnly.substring(0, cutPoint).trim();
            }
            truncatedMessage = trailingTags.isEmpty()
                    ? speechOnly
                    : speechOnly + " " + trailingTags;
        }

        // Add context-switching logic for USER messages only
        String playerName = player.getDisplayName().getString();
        if (sender == ChatDataManager.ChatSender.USER && previousMessages.size() > 1) {
            ChatMessage lastMessage = previousMessages.get(previousMessages.size() - 1);
            if (lastMessage.name == null || !lastMessage.name.equals(playerName)) {  // Null-safe check
                boolean isReturningPlayer = previousMessages.stream().anyMatch(msg -> playerName.equals(msg.name)); // Avoid NPE here too
                String note = isReturningPlayer
                        ? "<returning player: " + playerName + " resumes the conversation>"
                        : "<a new player has joined the conversation: " + playerName + ">";
                previousMessages.add(new ChatMessage(note, sender, playerName));

                // Log context-switching message
                LOGGER.info("Conversation-switching message: status=PENDING, sender={}, message={}, player={}, entity={}",
                        ChatDataManager.ChatStatus.PENDING, note, playerName, entityId);
            }
        }

        // Add message to history
        previousMessages.add(new ChatMessage(truncatedMessage, sender, playerName));

        // Log regular message addition
        LOGGER.info("Message added: status={}, sender={}, message={}, player={}, entity={}",
                status.toString(), sender.toString(), truncatedMessage, playerName, entityId);

        // Update current message and reset line number of displayed text
        this.currentMessage = truncatedMessage;
        this.currentLineNumber = 0;
        this.sender = sender;

        // Determine status for message
        if (sender == ChatDataManager.ChatSender.ASSISTANT) {
            status = ChatDataManager.ChatStatus.DISPLAY;
        } else {
            status = ChatDataManager.ChatStatus.PENDING;
        }

        if (sender == ChatDataManager.ChatSender.USER && systemPrompt.equals("system-chat") && auto_generated == 0) {
            // Broadcast new player message (when not auto-generated)
            ServerPackets.BroadcastPlayerMessage(this, player);
        }

        // Broadcast new entity message status (i.e. pending)
        ServerPackets.BroadcastEntityMessage(this);

        if (sender == ChatDataManager.ChatSender.ASSISTANT && triggerAdvancement) {
            AdvancementHelper.chatExchange(player, this);
            Mob entity = (Mob) ServerEntityFinder.getEntityByUUID((ServerLevel) player.level(), UUID.fromString(entityId));
            if (entity != null) {
                AdvancementHelper.checkInnerCircle(player, entity);
            }
        }
    }

    // Get wrapped lines
    public List<String> getWrappedLines() {
        return LineWrapper.wrapLines(this.currentMessage, ChatDataManager.MAX_CHAR_PER_LINE);
    }

    public boolean isEndOfMessage() {
        int totalLines = this.getWrappedLines().size();
        // Check if the current line number plus DISPLAY_NUM_LINES covers or exceeds the total number of lines
        return currentLineNumber + ChatDataManager.DISPLAY_NUM_LINES >= totalLines;
    }

    public void setLineNumber(Integer lineNumber) {
        int totalLines = this.getWrappedLines().size();
        // Ensure the lineNumber is within the valid range
        currentLineNumber = Math.min(Math.max(lineNumber, 0), totalLines);

        // Broadcast to all players
        ServerPackets.BroadcastEntityMessage(this);
    }

    public void setStatus(ChatDataManager.ChatStatus new_status) {
        status = new_status;

        // Broadcast to all players
        ServerPackets.BroadcastEntityMessage(this);
    }
}