#!/bin/bash
# временный хелпер тайпчека (не коммитить): javac по уже собранным классам, пока gradle заблокирован helium-Kotlin
cd "$(dirname "$0")" || exit 1
ARCJARS=$(find /home/mihail/.gradle/caches/modules-2/files-2.1 -name "*.jar" -path "*mindustry-antigrief*" | grep -v sources | tr '\n' ':')
CP="core/build/classes/java/main:core/build/classes/kotlin/main:$ARCJARS"
mkdir -p /tmp/javac-check
/home/mihail/.local/jdk/jdk-17.0.20+8/bin/javac -nowarn -proc:none -d /tmp/javac-check -cp "$CP" "$@" 2>&1 | grep -v "unchecked or unsafe\|Recompile with" | head -25
exit "${PIPESTATUS[0]}"
