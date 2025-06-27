#!/usr/bin/env bash
set -euo pipefail

ONLY_VERSION=${ONLY_VERSION:-}
DRY_RUN=${DRY_RUN:-0}

# Format: minecraft_version  yarn_mappings       loader_version  loom_version      fabric_version
VERSIONS=$(cat <<'EOF'
1.20    1.20+build.1       0.16.14    1.10-SNAPSHOT   0.83.0+1.20
1.20.1  1.20.1+build.10    0.15.11    1.10-SNAPSHOT   0.92.1+1.20.1
1.20.2  1.20.2+build.4     0.15.11    1.10-SNAPSHOT   0.91.6+1.20.2
1.20.3  1.20.3+build.1     0.15.11    1.10-SNAPSHOT   0.91.1+1.20.3
1.20.4  1.20.4+build.3     0.15.11    1.10-SNAPSHOT   0.97.0+1.20.4
1.20.5  1.20.5+build.1     0.16.14    1.10-SNAPSHOT   0.97.8+1.20.5
1.20.6  1.20.6+build.3     0.16.14    1.10-SNAPSHOT   0.100.8+1.20.6
1.21    1.21+build.9       0.16.14    1.10-SNAPSHOT   0.102.0+1.21
1.21.1  1.21.1+build.3     0.16.14    1.10-SNAPSHOT   0.116.3+1.21.1
EOF
)

while read -r mc_version yarn_mapping loader_version loom_version fabric_version; do
  [[ -z "$mc_version" || "$mc_version" =~ ^# ]] && continue
  [[ -n "$ONLY_VERSION" && "$ONLY_VERSION" != "$mc_version" ]] && continue

  echo "****"
  echo "Building for MC $mc_version  Fabric API $fabric_version"
  echo "****"

  if [[ "$DRY_RUN" == "1" ]]; then
    cat <<EOD
[DRY RUN] gradle.properties -> \
minecraft_version=$mc_version, \
yarn_mappings=$yarn_mapping, \
loader_version=$loader_version, \
loom_version=$loom_version, \
fabric_version=$fabric_version
[DRY RUN] fabric.mod.json -> "minecraft": "~$mc_version"
[DRY RUN] run './gradlew build -x test'
[DRY RUN] download fabric-api-$fabric_version.jar from FabricMC
EOD
    echo
    continue
  fi

  sed -i \
    -e "s/^minecraft_version=.*/minecraft_version=$mc_version/" \
    -e "s/^yarn_mappings=.*/yarn_mappings=$yarn_mapping/" \
    -e "s/^loader_version=.*/loader_version=$loader_version/" \
    -e "s/^loom_version=.*/loom_version=$loom_version/" \
    -e "s/^fabric_version=.*/fabric_version=$fabric_version/" \
    gradle.properties

  sed -i "s/\"minecraft\": \".*\"/\"minecraft\": \"~$mc_version\"/" \
    src/main/resources/fabric.mod.json

  ./gradlew build -x test
  find build/libs -name '*sources*.jar' -delete
  mv build/libs/creaturechat-*.jar .

  if [[ "$mc_version" == "1.20.1" ]]; then
    jar=creaturechat-*+1.20.1.jar
    cp "$jar" "${jar%.jar}-forge.jar"
    touch FORGE
    zip -r "${jar%.jar}-forge.jar" FORGE
  fi

  api_jar="fabric-api-${fabric_version}.jar"
  url="https://github.com/FabricMC/fabric/releases/download/${fabric_version//+/%2B}/${api_jar}"
  wget -q -O "$api_jar" "$url"

  echo
done <<< "$VERSIONS"
