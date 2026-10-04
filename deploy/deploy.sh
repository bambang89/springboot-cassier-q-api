#!/usr/bin/env bash
# Zero-downtime blue-green deploy, run by Jenkins: deploy.sh <image-tag>
#   1. start the idle color next to the live one
#   2. wait until its health check (/actuator/health, includes the DB) passes
#   3. point nginx-upstream.conf at it (a systemd path unit on the host
#      runs `nginx -t && systemctl reload nginx`, then copies the file to
#      .nginx-applied so we know the switch is live)
#   4. stop the old color
set -euo pipefail
cd "$(dirname "$0")"

TAG="${1:?usage: deploy.sh <image-tag>}"
UPSTREAM=nginx-upstream.conf
APPLIED=.nginx-applied

ACTIVE_PORT=$(grep -oE '127\.0\.0\.1:[0-9]+' "$UPSTREAM" 2>/dev/null | head -1 | cut -d: -f2 || true)
if [ "$ACTIVE_PORT" = "8082" ]; then
  NEW=blue;  NEW_PORT=8081; OLD_PORT=8082
else
  NEW=green; NEW_PORT=8082; OLD_PORT=8081
fi
NEW_CONTAINER="cassier-q-api-$NEW"

echo ">> Live: 127.0.0.1:${ACTIVE_PORT:-none}. Deploying $TAG as $NEW on 127.0.0.1:$NEW_PORT"
export IMAGE_TAG="$TAG" COLOR="$NEW" HOST_PORT="$NEW_PORT"
docker compose -p "$NEW_CONTAINER" up -d --force-recreate

echo ">> Waiting for $NEW_CONTAINER to become healthy..."
STATUS=starting
for _ in $(seq 1 60); do
  STATUS=$(docker inspect -f '{{.State.Health.Status}}' "$NEW_CONTAINER" 2>/dev/null || echo missing)
  case "$STATUS" in healthy|unhealthy|missing) break ;; esac
  sleep 5
done
if [ "$STATUS" != "healthy" ]; then
  echo "!! $NEW_CONTAINER is $STATUS. Live version left untouched."
  docker logs --tail 150 "$NEW_CONTAINER" || true
  docker compose -p "$NEW_CONTAINER" down || true
  exit 1
fi

echo ">> Switching nginx to 127.0.0.1:$NEW_PORT"
NEW_UPSTREAM="upstream cassier_q_api { server 127.0.0.1:$NEW_PORT; keepalive 16; }"
echo "$NEW_UPSTREAM" > "$UPSTREAM"
for _ in $(seq 1 30); do
  [ "$(cat "$APPLIED" 2>/dev/null)" = "$NEW_UPSTREAM" ] && break
  sleep 1
done
if [ "$(cat "$APPLIED" 2>/dev/null)" != "$NEW_UPSTREAM" ]; then
  echo "!! nginx did not pick up the new upstream. Rolling back."
  [ -n "$ACTIVE_PORT" ] && echo "upstream cassier_q_api { server 127.0.0.1:$ACTIVE_PORT; keepalive 16; }" > "$UPSTREAM"
  docker compose -p "$NEW_CONTAINER" down || true
  exit 1
fi

# Old nginx workers keep serving their in-flight requests for a moment
sleep 10
OLD=$(docker ps -q --filter "publish=$OLD_PORT")
if [ -n "$OLD" ]; then
  echo ">> Stopping old version on 127.0.0.1:$OLD_PORT"
  docker stop -t 40 $OLD >/dev/null
  docker rm $OLD >/dev/null
fi

echo ">> Done: $TAG live as $NEW on 127.0.0.1:$NEW_PORT"
