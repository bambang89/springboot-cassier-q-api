#!/usr/bin/env bash
# Mirrors a Jenkins multibranch build into the GitHub Actions log.
#   MODE=follow  : wait for the build Jenkins started from the GitHub webhook for $SHA
#                  (falls back to triggering one if none shows up)
#   MODE=trigger : start a new Jenkins build for $REF (Re-run / manual dispatch)
set -euo pipefail

: "${JENKINS_URL:?}" "${JENKINS_USER:?}" "${JENKINS_API_TOKEN:?}" "${JOB:?}" "${REF:?}" "${MODE:?}"
SHA="${SHA:-}"
FOLLOW_TIMEOUT="${FOLLOW_TIMEOUT:-300}"
LOG_FILE="${LOG_FILE:-jenkins.log}"

JENKINS_URL="${JENKINS_URL%/}"
api() { curl -fsS --retry 3 -u "$JENKINS_USER:$JENKINS_API_TOKEN" "$@"; }
enc() { jq -rn --arg v "$1" '$v|@uri'; }

ROOT_URL="$JENKINS_URL/job/$(enc "$JOB")"
# A branch job's name is the URL-encoded ref, and that name is encoded again in the URL
JOB_URL="$ROOT_URL/job/$(enc "$(enc "$REF")")"

job_exists() { api -o /dev/null "$JOB_URL/api/json" 2>/dev/null; }

find_build_for_sha() {
  api "$JOB_URL/api/json?tree=builds[number,actions[lastBuiltRevision[SHA1]]]" 2>/dev/null \
    | jq -r --arg sha "$SHA" \
        '[.builds[] | select(any(.actions[]?; .lastBuiltRevision.SHA1? == $sha))][0].number // empty'
}

trigger_build() {
  if ! job_exists; then
    echo "Job '$REF' belum ada di Jenkins, memicu scan repository..."
    api -X POST -o /dev/null "$ROOT_URL/build?delay=0"
    for _ in $(seq 1 60); do job_exists && break; sleep 5; done
    job_exists || { echo "::error::Job '$REF' tidak muncul di Jenkins setelah scan"; exit 1; }
  fi

  local queue q
  queue=$(api -X POST -D - -o /dev/null "$JOB_URL/build?delay=0" \
    | tr -d '\r' | awk 'tolower($1)=="location:"{print $2}')
  [ -n "$queue" ] || { echo "::error::Gagal memicu build Jenkins"; exit 1; }
  echo "Build masuk antrean: $queue"

  for _ in $(seq 1 120); do
    q=$(api "${queue%/}/api/json")
    BUILD=$(jq -r '.executable.number // empty' <<<"$q")
    [ -n "$BUILD" ] && return 0
    if [ "$(jq -r '.cancelled // false' <<<"$q")" = true ]; then
      echo "::error::Build dibatalkan di antrean Jenkins"; exit 1
    fi
    sleep 5
  done
  echo "::error::Build tidak keluar dari antrean Jenkins"; exit 1
}

stream_log() {
  local start=0 hdr more
  : > "$LOG_FILE"
  hdr=$(mktemp)
  while :; do
    api -D "$hdr" "$BUILD_URL/logText/progressiveText?start=$start" | tee -a "$LOG_FILE"
    start=$(tr -d '\r' < "$hdr" | awk -F': ' 'tolower($1)=="x-text-size"{print $2}')
    more=$(tr -d '\r' < "$hdr" | awk -F': ' 'tolower($1)=="x-more-data"{print $2}')
    [ "$more" = "true" ] || break
    sleep 3
  done
  rm -f "$hdr"
}

BUILD=""
if [ "$MODE" = "follow" ]; then
  echo "Menunggu build Jenkins untuk '$REF' (commit ${SHA:0:7})..."
  deadline=$((SECONDS + FOLLOW_TIMEOUT))
  while [ $SECONDS -lt $deadline ]; do
    BUILD=$(find_build_for_sha || true)
    [ -n "$BUILD" ] && break
    sleep 5
  done
  if [ -z "$BUILD" ]; then
    echo "Jenkins tidak memulai build otomatis, memicu build baru."
    trigger_build
  fi
else
  echo "Memicu build baru di Jenkins untuk '$REF'..."
  trigger_build
fi

BUILD_URL="$JOB_URL/$BUILD"
echo "::notice title=Jenkins build::$BUILD_URL"
echo "=================== Jenkins $JOB » $REF #$BUILD ==================="
stream_log
echo "=================================================================="

RESULT=""
for _ in $(seq 1 40); do
  RESULT=$(api "$BUILD_URL/api/json?tree=result" | jq -r '.result // empty')
  [ -n "$RESULT" ] && break
  sleep 3
done
RESULT="${RESULT:-UNKNOWN}"

case "$RESULT" in
  SUCCESS) ICON="✅" ;;
  ABORTED) ICON="⏹️" ;;
  *)       ICON="❌" ;;
esac

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "## $ICON Jenkins \`$REF\` #$BUILD — $RESULT"
    echo
    echo "[Buka build di Jenkins]($BUILD_URL) · log lengkap ada di artifact *jenkins-log*"
    echo
    echo "<details><summary>80 baris terakhir log</summary>"
    echo
    echo '```'
    tail -n 80 "$LOG_FILE"
    echo '```'
    echo "</details>"
  } >> "$GITHUB_STEP_SUMMARY"
fi

if [ "$RESULT" != "SUCCESS" ]; then
  echo "::error title=Jenkins $RESULT::Build $REF #$BUILD $RESULT — $BUILD_URL"
  exit 1
fi
echo "Jenkins build $REF #$BUILD SUCCESS"
