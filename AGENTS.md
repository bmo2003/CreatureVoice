# CreatureChat Agent Instructions

See **build.sh** for commands and supported Minecraft/Fabric versions.

## To Build
Run this to build **all versions**:

```bash
./build.sh
````

Build a **specific version** only:

```bash
ONLY_VERSION=1.20.1 ./build.sh
```

Preview the build steps without making changes:

```bash
DRY_RUN=1 ./build.sh
```

Or for a specific version:

```bash
ONLY_VERSION=1.20.1 DRY_RUN=1 ./build.sh
```

## Notes

* This script modifies `gradle.properties` and `fabric.mod.json`
* Run only when you're ready to test builds
