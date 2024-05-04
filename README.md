![CreatureChat Logo](src/main/resources/assets/creaturechat/icon.png "CreatureChat Logo")

# CreatureChat

### Chat, befriend, and interact with a rich world of creatures like never before! All creatures can talk and respond naturally using AI.

## Features:
- **Dynamic Dialogues**: Engage with Minecraft creatures like never before, each with a unique character sheet.
- **AI-Driven Chats**: Powered by ChatGPT, ensuring each conversation is fresh and engaging.
- **Custom Behaviors**: Creatures can make decisions on their own and **Follow, Flee, Attack**, and more!
- **Reactive Interactions**: Creatures automatically react to being damaged or receiving items from players.
- **Friendship Status**: Track your relationships on a 7-point scale, from foes to friends.
- **Custom UI Artwork**: Features beautiful hand-drawn icons for entities, expressive chat bubbles.
- **Multi-Player Interaction**: Share the experience; conversations sync across server & players.
- **Personalized Memory**: Creatures remember past interactions, making each chat more personal.
- **Model Support**: Flexible backend, compatible with various GPT and open-sources LLM models.

Ready to deepen your Minecraft journey with meaningful conversations and enduring friendships?
**Step into the world of CreatureChat 🗨 and spark your first conversation today!**

## Installation
1. **Install Fabric Loader & API**: Follow the instructions [here](https://fabricmc.net/use/).
1. **Install CreatureChat Mod**: Download and copy `creaturechat-*.jar` and `fabric-api-*.jar` into your `.minecraft/mods`
   folder.
1. **Create an OpenAI API key**: Visit https://platform.openai.com/api-keys, and use the **+ Create new secret key** button.
   Copy/Paste your key into the `/creaturechat key set <YOUR-SECRET-KEY-HERE>` command.

## Commands
The CreatureChat mod allows users to configure settings via in-game commands. Here's how to use them:

### Command Usage
- **REQUIRED:** `/creaturechat key set <key>`
  - Sets the *OpenAI API key*. This is required for making requests to the LLM.
- **OPTIONAL:** `/creaturechat url set <url>`
  - Sets the URL of the API used to make LLM requests. Defaults to `"https://api.openai.com/v1/chat/completions"`
- **OPTIONAL:** `/creaturechat model set <model>`
  - Sets the model used for generating responses in chats. Defaults to `gpt-3.5-turbo`.

### Configuration Scope:
**OPTIONAL:** You can specify the configuration scope at the end of each command to determine where settings should be applied:

- **Default** Configuration (`--config default`):
  Applies the configuration universally, unless overridden by a server-specific configuration.
- **Server**-Specific Configuration (`--config server`):
  Applies the configuration only to the server where the command is executed.
- If the `--config` option is not specified, the `default` configuration scope is assumed.

## Costs & Security
Using third-party Large Language Model (LLM) APIs, such as OpenAI, will incur usage-based **fees**.
These fees are based on the amount of data processed. Before integrating your API key, please
[review the pricing](https://openai.com/pricing#language-models) details provided by the API provider.
Be aware of the **potential costs** and plan your usage accordingly to avoid unexpected charges.

## Does OpenAI offer a **FREE** model?
While ChatGPT is a popular product and does offer a free version to their users on their website,
the OpenAI developer API does not extend any free models or free usage. You will be charged for each token
consumed and generated. We use the `gpt-3.5-turbo` model by default, due to its extremely low cost
and fast performance... however it is not free.

## Screenshots
![Interact with Minecraft Creatures](src/main/resources/assets/creaturechat/screenshots/salmon-follow.png)
![Panda Following the Player](src/main/resources/assets/creaturechat/screenshots/panda-follow.png)
![Piglins Reacting to Player](src/main/resources/assets/creaturechat/screenshots/piglin-reactions.png)
![Enderman Following the Player](src/main/resources/assets/creaturechat/screenshots/enderman-follow.png)
![Chat UI](src/main/resources/assets/creaturechat/screenshots/chat-ui.png)

## Authors

- Jonathan Thomas <jonathan@openshot.org>
- owlmaddie <owlmaddie@gmail.com>

## Contact & Resources

- [Join us on Discord](https://discord.gg/m9dvPFmN3e)
- [Build Instructions](INSTALL.md) ([Source Code](http://gitlab.openshot.org/minecraft/creature-chat))
- Download from [Modrinth](https://modrinth.com/project/creaturechat) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/creaturechat)
- Follow Us: [YouTube](https://www.youtube.com/@CreatureChat/featured) | 
[Twitter](https://twitter.com/TheCreatureChat) |
[TikTok](https://www.tiktok.com/@creaturechat)

## Legal Information

Please review our [Terms of Service](TERMS.md) and [Privacy Policy](PRIVACY.md) before using CreatureChat. By using our services, you agree to comply with these documents.

## License

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
