#!/usr/bin/env bash
# Démarre Gantt Simulator sous Linux ou macOS.
# Installe si nécessaire, sans droits administrateur :
#   - Java 17 (Eclipse Temurin) dans .runtime/
#   - JavaFX SDK 17.0.17 dans lib/
# puis compile les sources et lance l'interface.

set -euo pipefail

JAVAFX_VERSION="17.0.17"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT/.runtime"

step() { printf '\033[36m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[31mERREUR : %s\033[0m\n' "$*" >&2; exit 1; }

case "$(uname -s)" in
    Linux)  os="linux"; fx_os="linux" ;;
    Darwin) os="mac";   fx_os="osx" ;;
    *) fail "Système non pris en charge : $(uname -s) (utilisez start/start.bat sous Windows)" ;;
esac
case "$(uname -m)" in
    x86_64|amd64)  arch="x64" ;;
    arm64|aarch64) arch="aarch64" ;;
    *) fail "Architecture non prise en charge : $(uname -m)" ;;
esac

JAVAFX_DIR="$ROOT/lib/openjfx-${JAVAFX_VERSION}_${fx_os}-${arch}_bin-sdk"
JAVAFX_LIB="$JAVAFX_DIR/javafx-sdk-$JAVAFX_VERSION/lib"
JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/$os/$arch/jdk/hotspot/normal/eclipse"
JAVAFX_URL="https://download2.gluonhq.com/openjfx/$JAVAFX_VERSION/openjfx-${JAVAFX_VERSION}_${fx_os}-${arch}_bin-sdk.zip"

download() {
    if command -v curl >/dev/null 2>&1; then
        curl -fL --progress-bar -o "$2" "$1"
    elif command -v wget >/dev/null 2>&1; then
        wget -q --show-progress -O "$2" "$1"
    else
        fail "curl ou wget est nécessaire pour télécharger les dépendances"
    fi
}

extract_zip() {
    if command -v unzip >/dev/null 2>&1; then
        unzip -q -o "$1" -d "$2"
    elif command -v python3 >/dev/null 2>&1; then
        python3 -m zipfile -e "$1" "$2"
    else
        tar -xf "$1" -C "$2"
    fi
}

java_major() {
    "$1" -version 2>&1 | sed -n 's/^javac \([0-9][0-9]*\).*/\1/p' | head -n 1
}

find_jdk() {
    local candidate
    for candidate in "$RUNTIME_DIR"/jdk-17*/Contents/Home "$RUNTIME_DIR"/jdk-17*; do
        if [ -x "$candidate/bin/javac" ]; then
            echo "$candidate"
            return
        fi
    done

    local candidates=()
    [ -n "${JAVA_HOME:-}" ] && candidates+=("$JAVA_HOME")
    if command -v javac >/dev/null 2>&1; then
        candidates+=("$(cd "$(dirname "$(readlink -f "$(command -v javac)" 2>/dev/null || command -v javac)")/.." && pwd)")
    fi
    for candidate in ${candidates[@]+"${candidates[@]}"}; do
        if [ -x "$candidate/bin/javac" ]; then
            local major
            major="$(java_major "$candidate/bin/javac")"
            if [ -n "$major" ] && [ "$major" -ge 17 ]; then
                echo "$candidate"
                return
            fi
        fi
    done
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# 1. Java 17+
jdk="$(find_jdk)"
if [ -z "$jdk" ]; then
    step "Java 17 introuvable : installation locale (une seule fois, ~190 Mo)"
    download "$JDK_URL" "$tmp/jdk.tar.gz"
    mkdir -p "$RUNTIME_DIR"
    tar -xzf "$tmp/jdk.tar.gz" -C "$RUNTIME_DIR"
    jdk="$(find_jdk)"
    [ -n "$jdk" ] || fail "L'installation de Java a échoué"
fi
step "Java : $jdk"

# 2. JavaFX
if [ ! -f "$JAVAFX_LIB/javafx.controls.jar" ]; then
    step "JavaFX $JAVAFX_VERSION introuvable : installation locale (une seule fois, ~40 Mo)"
    download "$JAVAFX_URL" "$tmp/javafx.zip" || fail "JavaFX $JAVAFX_VERSION n'est pas disponible pour $fx_os-$arch"
    mkdir -p "$JAVAFX_DIR"
    extract_zip "$tmp/javafx.zip" "$JAVAFX_DIR"
    [ -f "$JAVAFX_LIB/javafx.controls.jar" ] || fail "L'installation de JavaFX a échoué"
fi
step "JavaFX : $JAVAFX_LIB"

# 3. Compilation
step "Compilation des sources"
"$jdk/bin/javac" -encoding UTF-8 --module-path "$JAVAFX_LIB" --add-modules javafx.controls \
    -d "$ROOT" "$ROOT"/*.java || fail "La compilation a échoué"

# 4. Lancement
step "Lancement de Gantt Simulator"
rm -rf "$tmp"
trap - EXIT
cd "$ROOT"
exec "$jdk/bin/java" -Dfile.encoding=UTF-8 --module-path "$JAVAFX_LIB" --add-modules javafx.controls \
    -cp "$ROOT" GanttSimulator "$@"
