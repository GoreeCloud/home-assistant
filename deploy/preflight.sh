#!/usr/bin/env bash
# Read-only configuration preflight. No containers are started or modified.
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f .env || -L .env ]]; then
  echo "BLOCKED: create a regular deploy/.env on the authorized VPS after review." >&2
  exit 2
fi
if [[ $(grep -c '^HOME_ASSISTANT_IMAGE=' .env || true) != 1 ]]; then
  echo "BLOCKED: exactly one HOME_ASSISTANT_IMAGE entry is required." >&2
  exit 2
fi
image="$(sed -n 's/^HOME_ASSISTANT_IMAGE=//p' .env)"
if [[ ! "$image" =~ ^ghcr\.io/home-assistant/home-assistant@sha256:[a-f0-9]{64}$ ]]; then
  echo "BLOCKED: HOME_ASSISTANT_IMAGE must be a reviewed upstream image digest." >&2
  exit 2
fi
if ! command -v docker >/dev/null 2>&1; then
  echo "BLOCKED: Docker with the Compose plugin is required." >&2
  exit 2
fi
if ! docker compose config --quiet; then
  echo "BLOCKED: Compose configuration is not valid." >&2
  exit 2
fi
if ! grep -Fq '"127.0.0.1:8123:8123"' compose.yaml; then
  echo "BLOCKED: port 8123 must remain bound to VPS loopback." >&2
  exit 2
fi
echo "Configuration-only preflight passed. No image was verified and no service was started."
echo "VPS resources, backups, reachability and port conflicts remain separate gates."
