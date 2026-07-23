#!/usr/bin/env bash
set -euo pipefail

PROXY_CONFIG="src/proxy.conf.local.json"
[[ -f "$PROXY_CONFIG" ]] || PROXY_CONFIG="src/proxy.conf.json"

npm run start -- --proxy-config "$PROXY_CONFIG"
