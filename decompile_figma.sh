#!/usr/bin/env bash
set -e

FIG_FILE="${1:-/home/artemmkr/Завантажене/Untitled (2).fig}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUN_BIN="/home/artemmkr/.bun/bin/bun"

if [ ! -x "$BUN_BIN" ]; then
    BUN_BIN="$(which bun 2>/dev/null || true)"
fi

if [ -z "$BUN_BIN" ] || [ ! -x "$BUN_BIN" ]; then
    echo "Error: bun runtime not found. Please install bun."
    exit 1
fi

exec "$BUN_BIN" "$SCRIPT_DIR/tools/figma_decompiler.mjs" "$FIG_FILE" "${@:2}"
