# Changelog

All notable changes to **CreatureChat** are documented in this file. The format is based on 
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to 
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.4] - 2024-05-15

### Added
- Doubled the number of character personality traits (to balance things out) 
- Added new `/creaturechat timeout set <seconds>` command
- Added support for commands to use different data types (`String`, `Integer`)

### Changed
- All buckets are now ignored for item-giving detection (since the entity is despawned immediately)
- Item giving is now aware if the entity accepts the item. It uses either "shows" or "gives" in the prompt now.
- Updated error messages to `RED` color for maximum attention
- Updated `/creaturechat help` output
- Updated `README.md` with new command documentation

### Fixed
- Bucketing a creature now maintains chat history when respawned
- Chats broken when OS locale is non-English language (i.e. `assistant to ass\u0131stant`)

## [1.0.3] - 2024-05-10

### Changed
- Simplified saving of chat data (no more renaming files)
- If chat data fails to save, send message to all ops (first auto-save happens at 1 minute after launching)
- If /creaturechat commands fail to save, send message to all ops (fail loudly) and display RED error message

## [1.0.2] - 2024-05-07

### Added
- Added support for Minecraft 1.20, 1.20.1, 1.20.2, 1.20.3, and 1.20.4 (new build pipeline)

### Changed
- Replaced calls to getLiteralString() with getString() for wider compatability

## [1.0.1] - 2024-05-06

### Added
- Added support for CreatureChat API (switch URL when a CreatureChat key is detected)
- Upgrade to Fabric Loader: `0.15.10`, Fabric API: `0.97.0+1.20.4`, Fabric Loom: `1.6.5`, Gradle: `8.6`
- New TERMS.md and PRIVACY.md documents (terms of service and privacy policy for CreatureChat)

### Changed
- Improved error messages onscreen (Invalid API Key, No tokens remaining, etc...), for improved troubleshooting
- Improved privacy by not outputting API keys in logs or onscreen
- Updated README.md with improved instructions and info

## [1.0.0] - 2024-05-01

### Added
- First release of CreatureChat
- Dynamic Dialogues: Enables chat interaction with unique Minecraft creatures.
- AI-Generated Chats: Uses LLM for dynamic conversations.
- Custom Behaviors: Creatures can follow, flee, or attack.
- Reactions: Automatic reactions to damage or item reception.
- Friendship Status: Tracks relationships on a scale from friend to foe.
- Auto Name Tag: All characters receive unique name tags (when interacted with).
- Custom UI Artwork: Includes hand-drawn entity icons and chat bubbles.
- Multi-Player Interaction: Syncs conversations across server and players.
- Personalized Memory: Creatures recall past interactions.
- Model Support: Supports various GPT versions and open-source LLM models.
- Commands: Easily set your API key, model, and url to connect your LLM.
- Language Support: Supports all Minecraft-supported languages.
- Auto Save: Chat data is automatically saved periodically.
