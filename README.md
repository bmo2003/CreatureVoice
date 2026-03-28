# CreatureVoice

## Give every Minecraft mob a real voice. Speak to them, and hear them speak back.

CreatureVoice is a Fabric mod for **Minecraft 1.21.7** (Java 21) that replaces all text-based mob interaction from [CreatureChat](https://github.com/CreatureChat/creature-chat) with full two-way voice. Hold a key, talk to any mob, and it listens, thinks, and replies out loud in its own unique voice with 3D positional audio.

---

### Core Features

**Voice Interaction**
- **Hold-to-Talk** (V key) with your crosshair on any mob to speak. Release to send.
- **Voice Replies** via Chatterbox TTS running locally on your machine. No API costs, no usage limits. Each mob gets a unique, consistent voice derived from its UUID.
- **3D Positional Audio** so the sound comes from wherever the mob is standing, with natural distance fade-out up to 32 blocks.
- **Speech-to-Text** via Deepgram for accurate voice transcription with fuzzy name matching to handle mishearings.

**AI Personalities**
- Every mob gets a procedurally generated RPG character sheet: name, personality, backstory, speaking style, alignment, skills, likes, dislikes, and more.
- Dark and evil characters exist. They hold grudges, manipulate the player, and pursue their own agendas.
- Character sheets persist across sessions and server restarts.

**Behavior System**
Mobs can physically act in the world based on conversation. All behaviors are driven by the AI:
- **Follow / Unfollow** -- mob follows the player
- **Flee** -- mob runs away from the player
- **Attack** -- mob attacks the player with simulated or native combat
- **Attack NPC** -- mob attacks a specific named NPC (e.g. "go kill Pip")
- **Protect / Unprotect** -- mob defends the player from threats
- **Lead** -- mob walks to real locations: villages, buildings, water, caves, portals, chests, or specific NPCs
- **Set Fire** -- mob walks to a nearby building and ignites flammable blocks
- **Stay / Resume** -- mob locks in place or returns to normal AI
- **Give Item / Receive Item** -- mob drops items for the player or accepts payment from the player's inventory
- **Speak To** -- mob walks to another NPC, delivers a message, and returns with the response
- **Rename** -- mob changes its own name when asked
- **Explode** -- creepers can self-detonate on command

**World Awareness**
- Mobs know about nearby structures (villages, strongholds, mineshafts, temples), buildings (detected by door scanning), containers (chests, furnaces, barrels), water bodies, lava pools, and nether portals.
- Mobs know which other NPCs are nearby and can reference them by name.
- Mobs overhear nearby NPC conversations and can react to what they heard.
- Mobs witness deaths and attacks, and remember them permanently.
- Mobs will never invent directions to places that don't exist.

**Friendship System**
- Relationships range from -3 (hostile) to +3 (best friend) and persist across sessions.
- Friendship affects how mobs respond to requests, threats, and gifts.
- At max friendship, the player can ride the mob and access its full inventory.

**Inventory**
- Every mob has a 15-slot inventory populated with biome-specific random loot.
- Players can trade, give gifts, or open the inventory UI (shift + right-click).
- Items received via the RECEIVE_ITEM behavior go into the mob's actual inventory.
- Inventory persists across sessions via NBT.

**Multiplayer**
- Conversations, voice replies, and behavior changes sync across the server so all players see and hear what's happening.

---

### Requirements

| Component | Purpose | Cost |
|-----------|---------|------|
| [Fabric Loader](https://fabricmc.net/use/) + Fabric API | Mod framework | Free |
| [Deepgram](https://deepgram.com) API key | Speech-to-text (voice input) | Free tier available |
| [Chatterbox TTS](https://github.com/resemble-ai/chatterbox) server | Text-to-speech (voice output) | Free, runs locally |
| LLM API key (e.g. [Gemini](https://ai.google.dev/)) | Mob AI, character generation, chat | Pay-per-use |

---

### Setup

**1. Install the mod**

Drop `creaturechat-*.jar` and `fabric-api-*.jar` into your `.minecraft/mods` folder.

**2. Start a Chatterbox TTS server**

Install and run a Chatterbox-compatible server locally. Options include:
- [chatterbox-tts-api](https://github.com/travisvn/chatterbox-tts-api)
- [Chatterbox-TTS-Server](https://github.com/devnen/Chatterbox-TTS-Server)

The server provides 28 unique NPC voices out of the box at `http://localhost:4123`.

**3. Configure the mod**

Create `creaturechat.json` in your Minecraft run directory:

```json
{
  "apiKey": "YOUR_LLM_API_KEY",
  "url": "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
  "model": "gemini-2.5-flash",
  "deepgramApiKey": "YOUR_DEEPGRAM_KEY",
  "chatterboxUrl": "http://localhost:4123"
}
```

Any OpenAI-compatible LLM endpoint works. Adjust `url` and `model` for your provider.

**4. Launch Minecraft** with the Fabric profile.

---

### Controls

| Input | Action |
|-------|--------|
| **V** (hold) | Record voice and send to the mob under your crosshair |
| **Right-click** chat bubble | Minimize / restore the speech bubble |
| **Shift + Right-click** mob | Open mob inventory (requires prior interaction) |
| **E** near mob | Open mob inventory (at max friendship) |

---

### Configuration Options

All fields in `creaturechat.json`:

| Field | Default | Description |
|-------|---------|-------------|
| `apiKey` | *required* | LLM API key |
| `url` | *required* | OpenAI-compatible chat completions endpoint |
| `model` | *required* | LLM model name |
| `deepgramApiKey` | *required* | Deepgram API key for speech-to-text |
| `chatterboxUrl` | `http://localhost:4123` | Chatterbox TTS server URL |
| `maxContextTokens` | `16385` | Max context window size for the LLM |
| `maxOutputTokens` | `200` | Max tokens per LLM response |
| `timeout` | `10` | HTTP timeout in seconds |
| `chatBubbles` | `false` | Show text chat bubbles above mobs |
| `whitelist` | `[]` | Only these mob types can be spoken to (empty = all) |
| `blacklist` | `[]` | These mob types cannot be spoken to |
| `story` | `""` | Custom world story injected into every NPC's system prompt |
| `realismMode` | `true` | Enforces realistic NPC behavior and world knowledge |

---

### Building from Source

Requires **Java 21** and **Gradle**.

```bash
git clone https://github.com/YOUR_REPO/CreatureVoice.git
cd CreatureVoice
./gradlew build -x test
```

The built jar will be in `build/libs/`.

For multi-version builds (Minecraft 1.20.2 through 1.21.7):

```bash
./build.sh
```

---

### Tech Stack

| Layer | Technology |
|-------|------------|
| Mod framework | Fabric 0.17.2 on Minecraft 1.21.7 |
| Mappings | Mojang (1.21.7+build.6) |
| LLM | Gemini 2.5 Flash via OpenAI-compatible endpoint |
| Speech-to-text | Deepgram Nova-3 REST API |
| Text-to-speech | Chatterbox TTS (local, 28 voice references) |
| Persistence | Per-entity JSON via GSON, saved in world folder |
| Build tool | Gradle with Fabric Loom 1.11 |

---

### Status

The mod is fully functional with optimised costs. All core features (voice interaction, behavior system, world awareness, inventory, multiplayer) are working and tested.

**Known issue:** NPCs assigned a non-English native language (Japanese, French, etc.) produce garbled speech through Chatterbox TTS, which only supports English. This has been flagged for fix.

**Coming soon:** Token Shop monetisation and additional features.

---

### Authors

- **bmo2003** -- CreatureVoice voice layer, behavior system extensions
- **Jonathan Thomas & owlmaddie LLC** -- CreatureChat foundation

### License

- **Source code:** [GNU GPL v3](LICENSE.md)
- **Assets:** CC-BY-NC-SA 4.0

CreatureVoice is a fork of [CreatureChat](https://github.com/CreatureChat/creature-chat) by owlmaddie LLC.
CreatureChat is a trademark of owlmaddie LLC. This project is not affiliated with or endorsed by Mojang AB, Microsoft Corp., or Deepgram.
*Minecraft* is a trademark of Mojang AB.
