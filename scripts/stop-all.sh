#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"

if [[ ! -d "$RUN_DIR" ]]; then
  exit 0
fi

for pid_file in "$RUN_DIR"/*.pid; do
  [[ -e "$pid_file" ]] || continue
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null; then
    echo "stopping $(basename "$pid_file" .pid)"
    kill "$pid"
  fi
  rm -f "$pid_file"
done

