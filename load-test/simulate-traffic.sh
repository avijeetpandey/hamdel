#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Hamdel — continuous traffic simulation
#
# Simulates a pool of concurrent streaming sessions sending heartbeat events
# at realistic intervals.  Every 30 s a summary line is printed.
#
# Usage:
#   chmod +x load-test/simulate-traffic.sh
#   ./load-test/simulate-traffic.sh               # defaults
#   SESSIONS=200 RPS=50 ./load-test/simulate-traffic.sh
#
# Environment variables:
#   BASE_URL  – target (default: http://localhost:8080)
#   SESSIONS  – number of concurrent session IDs in the pool (default: 50)
#   RPS       – approximate requests per second (default: 20)
#   FAIL_RATE – fraction of events that have playbackFailed=true (default: 0.08)
# ─────────────────────────────────────────────────────────────────────────────

BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSIONS="${SESSIONS:-50}"
RPS="${RPS:-20}"
FAIL_RATE="${FAIL_RATE:-0.08}"
ENDPOINT="${BASE_URL}/api/v1/heartbeat"

# Delay between requests in milliseconds (1000 / RPS)
DELAY_MS=$(echo "scale=3; 1000 / $RPS" | bc)
DELAY_S=$(echo "scale=6; $DELAY_MS / 1000" | bc)

# ── Pools ─────────────────────────────────────────────────────────────────────
CONTENT_IDS=("movie-inception-4k" "series-dark-s03e01" "live-ipl-2026" "trailer-avatar3" "documentary-cosmos")
PLAYER_VERSIONS=("5.2.1" "5.3.0" "6.0.0-beta" "4.9.8")
OS_LIST=("iOS" "Android" "WebMac" "WebWindows" "tvOS" "FireTV")
CDN_LIST=("cloudfront" "fastly" "akamai" "cloudflare")

# ── Helpers ───────────────────────────────────────────────────────────────────
rand_int() { echo $(( RANDOM % $1 )); }          # 0 .. (n-1)
rand_range() { echo $(( $1 + RANDOM % ($2-$1) )); }  # lo .. hi-1

# Generate a random UUID-ish string (no uuidgen dependency needed)
rand_uuid() {
  printf '%04x%04x-%04x-4%03x-%04x-%04x%04x%04x' \
    $RANDOM $RANDOM $RANDOM $(( RANDOM & 0xFFF )) \
    $(( (RANDOM & 0x3FFF) | 0x8000 )) \
    $RANDOM $RANDOM $RANDOM
}

# Pick a random element from an array
pick() {
  local arr=("$@")
  echo "${arr[$(( RANDOM % ${#arr[@]} ))]}"
}

# ── Stats counters ────────────────────────────────────────────────────────────
total=0
success=0
fail_req=0
period_start=$(date +%s)
period_ok=0

print_stats() {
  local now; now=$(date +%s)
  local elapsed=$(( now - period_start ))
  local rps_actual; rps_actual=$(echo "scale=1; $period_ok / ($elapsed + 1)" | bc)
  printf "\r\033[K[%s]  sent=%-6d  ok=%-6d  err=%-4d  RPS≈%s" \
    "$(date '+%H:%M:%S')" "$total" "$success" "$fail_req" "$rps_actual"
  period_ok=0
  period_start=$now
}

trap 'echo; echo "Stopped. total=$total ok=$success err=$fail_req"; exit 0' INT TERM

echo "▶  Hamdel traffic simulator"
echo "   Target  : $ENDPOINT"
echo "   Sessions: $SESSIONS   Target RPS: $RPS   Fail rate: $FAIL_RATE"
echo "   Press Ctrl-C to stop"
echo

# ── Main loop ─────────────────────────────────────────────────────────────────
stats_tick=0

while true; do
  # Choose a session from the pool (sessions are long-lived; reuse sessionIds)
  session_idx=$(rand_int "$SESSIONS")
  session_id="session-$(printf '%05d' "$session_idx")"
  event_id=$(rand_uuid)

  content=$(pick "${CONTENT_IDS[@]}")
  player=$(pick "${PLAYER_VERSIONS[@]}")
  os=$(pick "${OS_LIST[@]}")
  cdn=$(pick "${CDN_LIST[@]}")

  # VST: mostly 150-500ms, occasional slow start (500-2000ms)
  if (( RANDOM % 10 == 0 )); then
    vst_ms=$(rand_range 500 2000)
  else
    vst_ms=$(rand_range 150 500)
  fi

  # Playback failure based on FAIL_RATE (compare scaled RANDOM to threshold)
  fail_threshold=$(echo "$FAIL_RATE * 32767 / 1" | bc)
  if (( RANDOM < fail_threshold )); then
    playback_failed=true
    rebuffer_ms=0
    playback_ms=$(rand_range 1000 30000)
  else
    playback_failed=false
    # Rebuffering: 80% of events have 0 rebuffer, rest have 100-3000ms
    if (( RANDOM % 5 == 0 )); then
      rebuffer_ms=$(rand_range 100 3000)
    else
      rebuffer_ms=0
    fi
    playback_ms=$(rand_range 30000 3600000)
  fi

  timestamp_ms=$(python3 -c 'import time; print(int(time.time() * 1000))')

  payload="{\"eventId\":\"$event_id\",\"sessionId\":\"$session_id\",\"clientId\":\"client-$(printf '%04d' "$session_idx")\",\"contentId\":\"$content\",\"timestampMs\":$timestamp_ms,\"videoStartTimeMs\":$vst_ms,\"playbackFailed\":$playback_failed,\"rebufferDurationMs\":$rebuffer_ms,\"playbackDurationMs\":$playback_ms}"

  # Fire request (background so we don't block the loop on slow responses)
  http_status=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$ENDPOINT" \
    -H "Content-Type: application/json" \
    --max-time 2 \
    -d "$payload")

  (( total++ ))
  (( period_ok++ ))
  if [[ "$http_status" == "202" ]]; then
    (( success++ ))
  else
    (( fail_req++ ))
  fi

  # Print stats every ~100 requests
  (( stats_tick++ ))
  if (( stats_tick >= 100 )); then
    print_stats
    stats_tick=0
  fi

  # Introduce occasional burst (skip delay 1 in 8 iterations)
  if (( RANDOM % 8 != 0 )); then
    # Use python3 sleep for sub-second precision
    python3 -c "import time; time.sleep($DELAY_S)" 2>/dev/null \
      || sleep 1
  fi
done
