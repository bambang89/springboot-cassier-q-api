#!/usr/bin/env bash
# SSH tunnel ke SQL Server di server cassier-Q (43.134.16.30).
# localhost:14333 (Mac) -> 127.0.0.1:1433 (container sqlserver di server).
# Biarkan jalan selama backend lokal dipakai; Ctrl+C untuk menutup.
set -euo pipefail

LOCAL_PORT="${LOCAL_PORT:-14333}"
SERVER="${SERVER:-root@43.134.16.30}"

echo "Tunnel: localhost:${LOCAL_PORT} -> ${SERVER} 127.0.0.1:1433 (Ctrl+C untuk berhenti)"
exec ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 \
  -L "${LOCAL_PORT}:127.0.0.1:1433" "${SERVER}"
