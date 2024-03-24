# Mob GPT

Elevate your Minecraft experiences with **Mob GPT**, a cutting-edge mod that brings intelligent, dynamic conversation to
Minecraft mobs! Harnessing the power of GPT to make your world more interactive.

## Authors

- Jonathan Thomas <jonathan@openshot.org>
- Owlmaddie <owlmaddie@gmail.com>

## Installation

1. **Install Fabric Loader**: Follow the instructions [here](https://fabricmc.net/use/).
2. **Download Fabric API**: Get the version which we support (refer to `gradle.properties` for supported Minecraft and Fabric versions)
   from [Modrinth](https://modrinth.com/mod/fabric-api)
   or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fabric-api).
3. **Install Mob GPT Mod**: Place `mobgpt-*.jar` and `fabric-api-*.jar` into your `.minecraft/mods`
   folder.

## Upgrade Dependencies

When Fabric or Minecraft is updated, the build dependencies need to also
be updated. Below are the general steps for this upgrade process.

1. Visit https://fabricmc.net/develop for updated version #s
2. Copy/paste the recommended versions into `gradle.properties`
3. **Optional:** Update the Loom version in `build.gradle` 
4. Re-build: `./gradlew build` and watch for any errors
5. Re-run: `./gradlew runClient`
6. **Optional:** Re-start **IntelliJ IDEA** to clear cached gradle

## Contact & Resources

- [Source Code](http://gitlab.openshot.org/minecraft/mobgpt)

## License

GPLv3
