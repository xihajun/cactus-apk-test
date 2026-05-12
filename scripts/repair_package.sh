#!/usr/bin/env bash
set -euo pipefail

OLD_DIR="app/src/main/java/fun/example/cactusgemma"
NEW_DIR="app/src/main/java/com/example/cactusgemma"
OLD_FILE="$OLD_DIR/MainActivity.kt"
NEW_FILE="$NEW_DIR/MainActivity.kt"

mkdir -p "$NEW_DIR"

if [ -f "$OLD_FILE" ]; then
  if [ ! -f "$NEW_FILE" ]; then
    cp "$OLD_FILE" "$NEW_FILE"
  fi
  sed -i 's/^package fun\.example\.cactusgemma/package com.example.cactusgemma/' "$NEW_FILE"
  rm -rf app/src/main/java/fun
fi

if [ -f "$NEW_FILE" ]; then
  sed -i 's/^package fun\.example\.cactusgemma/package com.example.cactusgemma/' "$NEW_FILE"
fi

sed -i 's/namespace = "fun\.example\.cactusgemma"/namespace = "com.example.cactusgemma"/' app/build.gradle.kts
sed -i 's/applicationId = "fun\.example\.cactusgemma"/applicationId = "com.example.cactusgemma"/' app/build.gradle.kts

if grep -R "^package fun\.example\.cactusgemma" -n app/src/main/java >/tmp/cactus_bad_package.txt 2>/dev/null; then
  cat /tmp/cactus_bad_package.txt
  echo "Found invalid Kotlin package still present" >&2
  exit 1
fi

test -f "$NEW_FILE"
head -n 1 "$NEW_FILE"
