# ChatQuest: Every Conversation, A New Journey!

### ✨ Welcome to **Chat Quest 🗨️**, where every creature in your Minecraft world comes to life through conversation! ✨ 

## Features:
- **Dynamic Dialogues**: Engage with Minecraft creatures like never before, each with its unique character sheet.
- **AI-Driven Chats**: Powered by ChatGPT, ensuring each conversation is fresh and engaging.
- **Custom Behaviors**: Creatures can make decisions on their own and Follow, Flee, Attack, and more!
- **Friendship Status**: Track your relationships on a 7-point scale, from foes to friends.
- **Multi-Player Interaction**: Share the experience; conversations sync across server & players.
- **Personalized Memory**: Creatures remember past interactions, making each chat more personal.
- **Open AI Model Support**: Flexible backend, compatible with various GPT model versions.

Ready to transform your Minecraft world into a bustling community of chatting entities? 
**Dive into ChatQuest 🗨 and start the conversation today!**

## Installation

1. **Install Fabric Loader**: Follow the instructions [here](https://fabricmc.net/use/).
2. **Download Fabric API**: Get the version which we support (refer to `gradle.properties` for supported Minecraft and Fabric versions)
   from [Modrinth](https://modrinth.com/mod/fabric-api)
   or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api).
3. **Install Mob GPT Mod**: Place `mobgpt-*.jar` and `fabric-api-*.jar` into your `.minecraft/mods`
   folder.

## Screenshots
![Pig Teaching Player](src/main/resources/assets/mobgpt/screenshots/pig-teaching.png "Pig Teaching Player")
![Villager Greeting Player](src/main/resources/assets/mobgpt/screenshots/villager_greeting.png "Villager Greeting Player")
![Chat User-Interface](src/main/resources/assets/mobgpt/screenshots/chat-ui.png "Chat User-Interface")

## Upgrade Dependencies

When Fabric or Minecraft is updated, the build dependencies need to also
be updated. Below are the general steps for this upgrade process.

1. Visit https://fabricmc.net/develop for updated version #s
2. Copy/paste the recommended versions into `gradle.properties`
3. **Optional:** Update the Loom version in `build.gradle` 
4. Re-build: `./gradlew build` and watch for any errors
5. Re-run: `./gradlew runClient`
6. **Optional:** Re-start **IntelliJ IDEA** to clear cached gradle

## Authors

- Jonathan Thomas <jonathan@openshot.org>
- Owlmaddie <owlmaddie@gmail.com>

## Contact & Resources

- [Source Code](http://gitlab.openshot.org/minecraft/mobgpt)

## License

    ChattCraft is a Minecraft mod which allows chat conversations with entities.
    Copyright (C) 2024 OpenShot Studios, LLC

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