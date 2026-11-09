#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/out"
SRC="$ROOT/src/main/java"
rm -rf "$OUT"
mkdir -p "$OUT"
mapfile -t FILES < <(find "$SRC" -name '*.java' | sort)
javac -encoding UTF-8 -d "$OUT" "${FILES[@]}"
java -cp "$OUT" com.jianwen.springailab.SmokeMain
