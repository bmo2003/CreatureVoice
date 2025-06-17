#!/bin/bash
set -e

declare -a versions=("1.20" "1.20.1" "1.20.2" "1.20.3" "1.20.4")
declare -a mappings=("1.20+build.1" "1.20.1+build.10" "1.20.2+build.4" "1.20.3+build.1" "1.20.4+build.3")
declare -a fabric_versions=("0.83.0+1.20" "0.92.1+1.20.1" "0.91.6+1.20.2" "0.91.1+1.20.3" "0.97.0+1.20.4")

for i in "${!versions[@]}"; do
    minecraft_version="${versions[$i]}"
    yarn_mappings="${mappings[$i]}"
    fabric_version="${fabric_versions[$i]}"

    if [[ -n "$ONLY_VERSION" && "$minecraft_version" != "$ONLY_VERSION" ]]; then
        continue
    fi

    echo "****"
    echo "Preparing build for Minecraft $minecraft_version with Fabric $fabric_version"
    echo "****"

    if [[ "$DRY_RUN" == "1" ]]; then
        echo "[DRY RUN] Would update gradle.properties:"
        echo "  minecraft_version=$minecraft_version"
        echo "  yarn_mappings=$yarn_mappings"
        echo "  loader_version=0.15.11"
        echo "  fabric_version=$fabric_version"
        echo "[DRY RUN] Would edit fabric.mod.json: \"minecraft\": \"~$minecraft_version\""
        echo "[DRY RUN] Would run: ./gradlew build -x test"
        echo "[DRY RUN] Would fetch: https://github.com/FabricMC/fabric/releases/download/${fabric_version//+/%2B}/fabric-api-${fabric_version}.jar"
        echo ""
        continue
    fi

    # Modify configs
    sed -i "s/^minecraft_version=.*/minecraft_version=$minecraft_version/" gradle.properties
    sed -i "s/^yarn_mappings=.*/yarn_mappings=$yarn_mappings/" gradle.properties
    sed -i "s/^loader_version=.*/loader_version=0.15.11/" gradle.properties
    sed -i "s/^fabric_version=.*/fabric_version=$fabric_version/" gradle.properties

    sed -i "s/\"minecraft\": \".*\"/\"minecraft\": \"~$minecraft_version\"/" src/main/resources/fabric.mod.json

    ./gradlew build -x test
    find build/libs -type f -name '*sources*.jar' -exec rm {} \;
    mv build/libs/creaturechat-*.jar .

    if [ "$minecraft_version" == "1.20.1" ]; then
        jar_name=$(ls creaturechat-*+1.20.1.jar)
        cp "$jar_name" "${jar_name%.jar}-forge.jar"
        touch FORGE
        zip -r "${jar_name%.jar}-forge.jar" FORGE
    fi

    FABRIC_API_JAR="fabric-api-${fabric_version}.jar"
    DOWNLOAD_URL="https://github.com/FabricMC/fabric/releases/download/${fabric_version//+/%2B}/${FABRIC_API_JAR}"
    wget -q -O "${FABRIC_API_JAR}" $DOWNLOAD_URL

    echo ""
done
