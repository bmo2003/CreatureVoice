# CreatureChat

## Chat with any mob in Minecraft! All creatures can talk using AI!

### Features
- **AI-Driven Chats:** Using ChatGPT or open-source AI models, each conversation is unique and engaging!
- **Behaviors:** Creatures can make decisions on their own and **Follow, Flee, Attack, Protect**, and more!
- **Reactions:** Creatures automatically react to being damaged or receiving items from players.
- **Friendship:** Track your relationships from friends to foes.
- **Multi-Player:** Share the experience; conversations sync across server & players.
- **Memory:** Creatures remember your past interactions, making each chat more personal.

### Create meaningful conversations and enduring friendships? A betrayal perhaps?

[![CreatureChat Trailer Video](src/main/resources/assets/creaturechat/screenshots/video-thumbnail.png)](https://youtu.be/huyMz4FpN3U)

### Installation Instructions
<details>
  <summary>Fabric (Recommended)</summary>

## Fabric Instructions
1. **Install Fabric Loader & API**: Follow the instructions [here](https://fabricmc.net/use/).
1. **Install CreatureChat Mod**: Download and copy `creaturechat-*.jar` and `fabric-api-*.jar` into your `.minecraft/mods` folder.
1. **Create an OpenAI API key**: Visit https://platform.openai.com/api-keys, and use the **+ Create new secret key** button.
   Copy/Paste your key into the `/creaturechat key set <YOUR-SECRET-KEY-HERE>` command.
1. **Launch Minecraft** with the Fabric profile
</details>
<details>
  <summary>Forge (with Sinytra Connector)</summary>

## Forge Instructions
### NOTE: Sintra Connector only supports Minecraft 1.20.1

1. **Install Forge:** Download [Forge Installer](https://files.minecraftforge.net/), run it, select "Install client".
1. **Install Forgified Fabric API:** Download [Forgified Fabric API](https://curseforge.com/minecraft/mc-mods/forgified-fabric-api) and copy the `*.jar` into your `.minecraft/mods` folder.
1. **Install Sinytra Connector:** Download [Sinytra Connector](https://www.curseforge.com/minecraft/mc-mods/sinytra-connector) and copy the `*.jar` into your `.minecraft/mods` folder.
1. **Install CreatureChat Mod**: Download and copy `creaturechat-*.jar` into your `.minecraft/mods` folder.
1. **Create an OpenAI API key**: Visit https://platform.openai.com/api-keys, and use the **+ Create new secret key** button.
   Copy/Paste your key into the `/creaturechat key set <YOUR-SECRET-KEY-HERE>` command.
1. **Launch Minecraft** with the Forge profile
</details>

### In-game Commands
<details>
  <summary>Configure CreatureChat</summary>

- **REQUIRED:** `/creaturechat key set <key>`
  - Sets the _OpenAI API key_. This is required for making requests to the LLM.
- **OPTIONAL:** `/creaturechat url set "<url>"`
  - Sets the URL of the API used to make LLM requests. Defaults to `"<https://api.openai.com/v1/chat/completions>"`
- **OPTIONAL:** `/creaturechat model set <model>`
  - Sets the model used for generating responses in chats. Defaults to `gpt-3.5-turbo`.
- **OPTIONAL:** `/creaturechat timeout set <seconds>`
    - Sets the timeout (in seconds) for API HTTP requests. Defaults to `10` seconds.

  ### Configuration Scope:
  **OPTIONAL:** You can specify the configuration scope at the end of each command to determine where settings should be applied:

  - **Default** Configuration (`--config default`):
    Applies the configuration universally, unless overridden by a server-specific configuration.
  - **Server**-Specific Configuration (`--config server`):
    Applies the configuration only to the server where the command is executed.
  - If the `--config` option is not specified, the `default` configuration scope is assumed.
</details>

### Does OpenAI offer a **FREE** model?
While ChatGPT is a popular product and does offer a free version to their users on their website,
the OpenAI developer API does not extend any free models or free usage. You will be charged for each token
consumed and generated. We use the `gpt-3.5-turbo` model by default, due to its low cost
and fast performance... however it is not free.

### Free Local LLM
CreatureChat fully supports **free & open-source** LLMs. An HTTP endpoint which supports the OpenAI Chat Completion
JSON syntax is required. We highly recommend using [Ollama](https://ollama.com/) or [LiteLLM](https://litellm.vercel.app/) as your HTTP proxy.
LiteLLM supports **100+ LLMs** (including Anthropic, VertexAI, HuggingFace, Google Gemini, and Ollama), and proxies them through a
local HTTP endpoint in a compatible format with CreatureChat. *NOTE: You must have a very expensive GPU to run a local
LLM on your computer at a speed which is fast enough to be playable in Minecraft.*

### Costs & Security
Using third-party Large Language Model (LLM) APIs, such as OpenAI, will incur usage-based **fees**.
These fees are based on the amount of data processed. Before integrating your API key, please
[review the pricing](https://openai.com/pricing#language-models) details provided by the API provider.
Be aware of the **potential costs** and plan your usage accordingly to avoid unexpected charges.

### Screenshots
![Interact with Minecraft Creatures](src/main/resources/assets/creaturechat/screenshots/salmon-follow.png)
![Panda Following the Player](src/main/resources/assets/creaturechat/screenshots/panda-follow.png)
![Piglins Reacting to Player](src/main/resources/assets/creaturechat/screenshots/piglin-reactions.png)
![Enderman Following the Player](src/main/resources/assets/creaturechat/screenshots/enderman-follow.png)
![Chat UI](src/main/resources/assets/creaturechat/screenshots/chat-ui.png)

### Authors

- Jonathan Thomas <jonathan@openshot.org>
- owlmaddie <owlmaddie@gmail.com>

### Contact & Resources

- [Join us on Discord](https://discord.gg/m9dvPFmN3e)
- [Build Instructions](INSTALL.md) ([Source Code](http://gitlab.openshot.org/minecraft/creature-chat))
- Download from [Modrinth](https://modrinth.com/project/creaturechat) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/creaturechat)
- Follow Us: [YouTube](https://www.youtube.com/@CreatureChat/featured) | 
[Twitter](https://twitter.com/TheCreatureChat) |
[TikTok](https://www.tiktok.com/@creaturechat)

### Legal Information

Please review our [Terms of Service](TERMS.md) and [Privacy Policy](PRIVACY.md) before using CreatureChat. 
By using our services, you agree to comply with these documents.

### License

    CreatureChat is a Minecraft mod which allows chat conversations with entities.
    Copyright (C) 2024 owlmaddie LLC

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
