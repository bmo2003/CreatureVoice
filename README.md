# CreatureVoice

## Give every Minecraft mob a real voice — speak to them, and hear them speak back!

CreatureVoice is a Fabric mod for Minecraft 1.21.7 that replaces all text-based mob interaction from [CreatureChat](https://github.com/CreatureChat/creature-chat) with full voice. Talk to any mob by holding **V** and speaking; the mob listens, thinks, and replies out loud in its own persistent voice — with 3D positional audio so the sound comes from wherever the mob is standing.

### Features

- **Hold-to-Talk:** Hold **V** with your crosshair on any mob to speak to it. Release to send.
- **Voice Replies:** Mobs reply out loud using ElevenLabs TTS. Each mob has a unique, consistent voice derived from its UUID.
- **3D Positional Audio:** Sound comes from the mob's location in the world with natural distance fade-out up to 32 blocks.
- **AI Personalities:** Every mob has a generated personality, backstory, speaking style, and alignment.
- **Behaviors:** Mobs can Follow, Flee, Attack, Protect, Lead you to locations, and more — all driven by conversation.
- **Real Location Knowledge:** Mobs know about nearby structures (villages, mineshafts, strongholds, etc.) and can guide you there. They will never make up directions to places they don't know about.
- **Friendship System:** Relationships range from -3 (hostile) to +3 (best friends) and persist across sessions.
- **Memory:** Mobs remember your past conversations, making each interaction more personal.
- **Inventory:** Every mob has a random loot inventory. Trade, give gifts, or rob them.
- **Multi-Player:** Conversations sync across the server so other players see and hear what's happening.

### Voice Setup

Two API keys are required and must be placed in a `creaturechat.json` file in your Minecraft run directory:

```json
{
  "deepgramApiKey": "YOUR_DEEPGRAM_KEY",
  "elevenLabsApiKey": "YOUR_ELEVENLABS_KEY"
}
```

- **Deepgram** — converts your microphone audio to text (speech-to-text). Get a free key at [deepgram.com](https://deepgram.com).
- **ElevenLabs** — converts mob responses to speech (text-to-speech). Get a key at [elevenlabs.io](https://elevenlabs.io).

### LLM Setup (required for mob AI)

An LLM endpoint is required to generate mob personalities and chat responses. Configure it in-game:

```
/creaturechat key set <YOUR-OPENAI-OR-COMPATIBLE-KEY>
/creaturechat url set "https://api.openai.com/v1/chat/completions"
/creaturechat model set gpt-4o-mini
```

For free/local models via [Ollama](https://ollama.com/) + [LiteLLM](https://litellm.vercel.app/):
```
/creaturechat url set "http://localhost:8000/v1/chat/completions"
/creaturechat model set ollama/llama3
/creaturechat timeout set 360
```

### Controls

| Key | Action |
|-----|--------|
| **V** (hold) | Record voice and send to the mob your crosshair is on |
| **Right-click** on chat bubble | Minimize / restore the bubble |
| **E** near mob | Open mob inventory |

### In-game Commands

| Command | Description |
|---------|-------------|
| `/creaturechat key set <key>` | Set the LLM API key |
| `/creaturechat url set "<url>"` | Set the LLM endpoint URL |
| `/creaturechat model set <model>` | Set the LLM model name |
| `/creaturechat timeout set <seconds>` | Set HTTP request timeout |
| `/creaturechat whitelist <type \| all \| clear>` | Show bubbles for entity type |
| `/creaturechat blacklist <type \| all \| clear>` | Hide bubbles for entity type |
| `/story set "<text>"` | Set a world story included in all prompts |
| `/story display \| clear` | Show or clear the current story |

### Installation

1. Install [Fabric Loader & Fabric API](https://fabricmc.net/use/)
2. Drop `creaturevoice-*.jar` and `fabric-api-*.jar` into `.minecraft/mods`
3. Create `creaturechat.json` with your API keys (see Voice Setup above)
4. Launch Minecraft with the Fabric profile
5. Configure your LLM key in-game

### Building from Source

Requires Java 21 and Gradle.

```bash
git clone https://github.com/YOUR_REPO/creaturevoice
cd creaturevoice
./gradlew build
```

The built jar will be in `build/libs/`.

### Authors

- bmo2003 (CreatureVoice voice layer)
- Jonathan Thomas & owlmaddie LLC (CreatureChat foundation)

### License

- **Source code:** [GNU GPL v3](LICENSE.md)
- **Non-code assets:** [CC-BY-NC-SA-4.0](LICENSE-ASSETS.md)

CreatureVoice is a fork of [CreatureChat](https://github.com/CreatureChat/creature-chat) by owlmaddie LLC.
CreatureChat™ is a trademark of owlmaddie LLC. This project is not affiliated with or endorsed by Mojang AB, Microsoft Corp., ElevenLabs, or Deepgram.
*Minecraft®* is a trademark of Mojang AB.
