#!/usr/bin/env bash
set -euo pipefail

# Migrate from old package names to com.example.litertlmchat

NEW_DIR="app/src/main/java/com/example/litertlmchat"
NEW_FILE="$NEW_DIR/MainActivity.kt"

# Remove old cactusgemma package if present
OLD_CACTUS="app/src/main/java/com/example/cactusgemma"
OLD_FUN="app/src/main/java/fun/example/cactusgemma"

mkdir -p "$NEW_DIR"

# Copy from old locations if new file doesn't exist
if [ ! -f "$NEW_FILE" ]; then
  if [ -f "$OLD_CACTUS/MainActivity.kt" ]; then
    cp "$OLD_CACTUS/MainActivity.kt" "$NEW_FILE"
  elif [ -f "$OLD_FUN/MainActivity.kt" ]; then
    cp "$OLD_FUN/MainActivity.kt" "$NEW_FILE"
  fi
fi

# Fix package declaration
if [ -f "$NEW_FILE" ]; then
  sed -i 's/^package fun\.example\.cactusgemma/package com.example.litertlmchat/' "$NEW_FILE"
  sed -i 's/^package com\.example\.cactusgemma/package com.example.litertlmchat/' "$NEW_FILE"
fi

# Clean up old directories
rm -rf app/src/main/java/fun
rm -rf "$OLD_CACTUS"

# Fix build.gradle.kts namespace and applicationId
sed -i 's/namespace = "fun\.example\.cactusgemma"/namespace = "com.example.litertlmchat"/' app/build.gradle.kts
sed -i 's/namespace = "com\.example\.cactusgemma"/namespace = "com.example.litertlmchat"/' app/build.gradle.kts
sed -i 's/applicationId = "fun\.example\.cactusgemma"/applicationId = "com.example.litertlmchat"/' app/build.gradle.kts
sed -i 's/applicationId = "com\.example\.cactusgemma"/applicationId = "com.example.litertlmchat"/' app/build.gradle.kts

# Verify no bad packages remain
if grep -R "^package fun\.example\." -n app/src/main/java >/tmp/litert_bad_package.txt 2>/dev/null; then
  cat /tmp/litert_bad_package.txt
  echo "Found invalid Kotlin package still present" >&2
  exit 1
fi

test -f "$NEW_FILE"
head -n 1 "$NEW_FILE"
