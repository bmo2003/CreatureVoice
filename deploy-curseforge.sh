#!/bin/bash

set -e

CURSEFORGE_API_KEY=${CURSEFORGE_API_KEY}
CHANGELOG_FILE="./CHANGELOG.md"
API_URL="https://minecraft.curseforge.com/api"
PROJECT_ID=1012118
DEPENDENCY_SLUG="fabric-api"
USER_AGENT="CreatureChat-Minecraft-Mod:curseforge@owlmaddie.com"
SLEEP_DURATION=10

# Function to fetch game version IDs
fetch_game_version_ids() {
  local minecraft_version="$1"
  local response=$(curl --retry 3 --retry-delay 5 -s -H "X-Api-Token: $CURSEFORGE_API_KEY" "$API_URL/game/versions")

  local client_id=$(echo "$response" | jq -r '.[] | select(.name == "Client") | .id')
  local server_id=$(echo "$response" | jq -r '.[] | select(.name == "Server") | .id')
  local fabric_id=$(echo "$response" | jq -r '.[] | select(.name == "Fabric") | .id')
  local minecraft_id=$(echo "$response" | jq -r --arg mv "$minecraft_version" '.[] | select(.name == $mv) | .id' | head -n 1)

  if [ -z "$client_id" ] || [ -z "$server_id" ] || [ -z "$fabric_id" ] || [ -z "$minecraft_id" ]; then
    echo "ERROR: One or more game version IDs not found."
    exit 1
  fi

  echo "$client_id $server_id $fabric_id $minecraft_id"
}

# Read the first changelog block
CHANGELOG=$(awk '/^## \[/{ if (p) exit; p=1 } p' "$CHANGELOG_FILE")
echo "CHANGELOG:"
echo "$CHANGELOG"
echo ""

# Check if the changelog contains "UNRELEASED"
if echo "$CHANGELOG" | grep -qi "UNRELEASED"; then
  echo "ERROR: Changelog contains UNRELEASED. Please finalize the changelog before deploying."
  exit 1
fi

# Extract the version
VERSION=$(echo "$CHANGELOG" | head -n 1 | sed -n 's/^## \[\(.*\)\] - .*/\1/p')
echo "VERSION:"
echo "$VERSION"
echo ""

# Iterate over each jar file in the artifacts
for FILE in creaturechat*.jar; do
  if [ -f "$FILE" ]; then
    FILE_BASENAME=$(basename "$FILE")
    OUR_VERSION=$(echo "$FILE_BASENAME" | sed -n 's/creaturechat-\(.*\)+.*\.jar/\1/p')
    MINECRAFT_VERSION=$(echo "$FILE_BASENAME" | sed -n 's/.*+\(.*\)\.jar/\1/p')
    VERSION_NUMBER="$OUR_VERSION-$MINECRAFT_VERSION"

    # Verify that OUR_VERSION and MINECRAFT_VERSION are not empty and OUR_VERSION matches VERSION
    if [ -z "$OUR_VERSION" ] || [ -z "$MINECRAFT_VERSION" ] || [ "$OUR_VERSION" != "$VERSION" ]; then
      echo "ERROR: Version mismatch or missing version information in $FILE_BASENAME. OUR_VERSION: $OUR_VERSION, MINECRAFT_VERSION: $MINECRAFT_VERSION, EXPECTED VERSION: $VERSION"
      exit 1
    fi

    echo "Preparing to upload $FILE_BASENAME as version $VERSION_NUMBER..."

    # Fetch game version IDs
    GAME_VERSION_IDS=($(fetch_game_version_ids "$MINECRAFT_VERSION"))

    # Create a new version payload
    PAYLOAD=$(jq -n --arg changelog "$CHANGELOG" \
      --arg changelogType "markdown" \
      --arg displayName "$FILE_BASENAME" \
      --argjson gameVersions "$(printf '%s\n' "${GAME_VERSION_IDS[@]}" | jq -R . | jq -s .)" \
      --argjson gameVersionTypeIds '[75125]' \
      --arg releaseType "release" \
      --argjson relations '[{"slug": "'"$DEPENDENCY_SLUG"'", "type": "requiredDependency"}]' \
      '{
        "changelog": $changelog,
        "changelogType": $changelogType,
        "displayName": $displayName,
        "gameVersions": $gameVersions | map(tonumber),
        "gameVersionTypeIds": $gameVersionTypeIds,
        "releaseType": $releaseType,
        "relations": {
          "projects": $relations
        }
      }')

    # Write the payload to a temporary file to avoid issues with large payloads
    echo "$PAYLOAD" > metadata.json

    # Sleep for the specified duration
    sleep $SLEEP_DURATION

    # Upload the version with the file
    echo "Uploading $FILE_BASENAME as version $VERSION_NUMBER..."
    HTTP_RESPONSE=$(curl --retry 3 --retry-delay 5 --fail -o response.txt -w "\nHTTP Code: %{http_code}\n" -X POST "$API_URL/projects/$PROJECT_ID/upload-file" \
      -H "X-Api-Token: $CURSEFORGE_API_KEY" \
      -H "User-Agent: $USER_AGENT" \
      -F "metadata=<metadata.json;type=application/json" \
      -F "file=@$FILE;type=application/java-archive")

    # Output the response and HTTP code
    echo "Response:"
    cat response.txt
    echo "$HTTP_RESPONSE"

    echo "Uploaded $FILE_BASENAME as version $VERSION_NUMBER."
  fi
done
