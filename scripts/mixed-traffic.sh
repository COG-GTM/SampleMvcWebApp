#!/usr/bin/env bash
# mixed-traffic.sh - smoke/load script that exercises both booking channels.
#
#   PORTAL channel  : EXEC dbo.usp_BookSeat / usp_CancelBooking through sqlcmd (same call the portal makes),
#                     or POST $PORTAL_URL/Booking/Book when PORTAL_URL is set (Windows box running the portal)
#   PARTNER channel : booking-api REST on $BOOKING_API_URL (default http://localhost:8081)
#
# Run it on a freshly seeded database (scripts/run-legacy.sh). Inputs are picked by query so
# repeated runs on a fresh seed book the same candidates onto the same sessions.
#
# Environment overrides:
#   PORTAL_BOOKINGS (20)  PARTNER_BOOKINGS (20)  CANCELS (3 per channel)  SESSIONS (4)
#   FIRST_CANDIDATE (901)   - candidates NW00000901.. are left unbooked by the seed
#   MSSQL_SA_PASSWORD, BOOKING_API_URL, PORTAL_URL
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORTAL_BOOKINGS="${PORTAL_BOOKINGS:-20}"
PARTNER_BOOKINGS="${PARTNER_BOOKINGS:-20}"
CANCELS="${CANCELS:-3}"
SESSIONS="${SESSIONS:-4}"
FIRST_CANDIDATE="${FIRST_CANDIDATE:-901}"
BOOKING_API_URL="${BOOKING_API_URL:-http://localhost:8081}"
PORTAL_URL="${PORTAL_URL:-}"
MSSQL_SA_PASSWORD="${MSSQL_SA_PASSWORD:-Str0ng!Passw0rd}"

MSSQL_CONTAINER="${MSSQL_CONTAINER:-mssql}"

# host sqlcmd if there is one, otherwise the one inside the SQL Server container (same as run-legacy.sh)
if command -v sqlcmd >/dev/null 2>&1; then SQLCMD=(sqlcmd -S localhost,1433)
elif [ -x /opt/mssql-tools18/bin/sqlcmd ]; then SQLCMD=(/opt/mssql-tools18/bin/sqlcmd -S localhost,1433)
elif [ -x /opt/mssql-tools/bin/sqlcmd ]; then SQLCMD=(/opt/mssql-tools/bin/sqlcmd -S localhost,1433)
elif docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "$MSSQL_CONTAINER"; then
    SQLCMD=(docker exec -i "$MSSQL_CONTAINER" /opt/mssql-tools18/bin/sqlcmd -S localhost)
else echo "sqlcmd not found (install mssql-tools18 or start the $MSSQL_CONTAINER container)" >&2; exit 1; fi

sqlq() {  # sqlq [sqlcmd args...]  -> bare rows, | separated
    "${SQLCMD[@]}" -U sa -P "$MSSQL_SA_PASSWORD" -C -b -d AssessmentDelivery -h -1 -W -s '|' "$@"
}

sql() {  # sql "<tsql>"
    sqlq -Q "SET NOCOUNT ON; $1"
}

log() { echo "[mixed-traffic] $*"; }

curl -s -o /dev/null "$BOOKING_API_URL/api/v1/sessions" || { echo "booking-api not reachable at $BOOKING_API_URL" >&2; exit 1; }

TOTAL=$((PORTAL_BOOKINGS + PARTNER_BOOKINGS))

# --- inputs ---------------------------------------------------------------
mapfile -t SESSION_IDS < <(sql "SELECT TOP ($SESSIONS) s.SessionId FROM dbo.ExamSession s
    WHERE s.Status = 'OPEN' AND s.StartsAt > GETDATE() AND s.SeatsAvailable >= 5
    ORDER BY s.SessionId")
[ "${#SESSION_IDS[@]}" -gt 0 ] || { echo "no OPEN future sessions with free seats - is the DB seeded?" >&2; exit 1; }

# candidate rows: CandidateId|CandidateNumber, ordered upward from NW00000901
mapfile -t CANDIDATES < <(sql "SELECT TOP ($TOTAL) c.CandidateId, c.CandidateNumber FROM dbo.Candidate c
    WHERE c.CandidateNumber >= 'NW$(printf '%08d' "$FIRST_CANDIDATE")'
      AND (c.DefaultAccommodationCode IS NULL OR c.DefaultAccommodationCode NOT IN ('ET25','ET50'))
    ORDER BY c.CandidateNumber")
[ "${#CANDIDATES[@]}" -ge "$TOTAL" ] || { echo "need $TOTAL candidates from NW$(printf '%08d' "$FIRST_CANDIDATE"), found ${#CANDIDATES[@]}" >&2; exit 1; }

log "sessions: ${SESSION_IDS[*]}"
log "candidates: ${CANDIDATES[0]#*|} .. ${CANDIDATES[$((TOTAL-1))]#*|}"

PORTAL_IDS=(); PARTNER_IDS=()
portal_ok=0; portal_fail=0; partner_ok=0; partner_fail=0

book_portal() {  # book_portal <candidateId> <candidateNumber> <sessionId>
    local out
    if [ -n "$PORTAL_URL" ]; then
        if curl -s -f -o /dev/null -X POST "$PORTAL_URL/Booking/Book" \
                --data-urlencode "SessionId=$3" --data-urlencode "CandidateNumber=$2"; then
            out="$(sql "SELECT TOP 1 BookingId FROM dbo.Booking WHERE SessionId = $3 AND CandidateId = $1 ORDER BY BookingId DESC")"
        fi
    else
        out="$(sql "DECLARE @b INT, @s VARCHAR(12);
            EXEC dbo.usp_BookSeat @SessionId = $3, @CandidateId = $1, @BookingId = @b OUTPUT, @ResultStatus = @s OUTPUT;
            SELECT CAST(@b AS VARCHAR(12))" 2>/dev/null || true)"
    fi
    out="$(echo "${out:-}" | tr -d '[:space:]')"
    if [[ "$out" =~ ^[0-9]+$ ]]; then
        PORTAL_IDS+=("$out"); portal_ok=$((portal_ok + 1))
    else
        portal_fail=$((portal_fail + 1))
    fi
}

book_partner() {  # book_partner <candidateNumber> <sessionId> <ref>
    local body code
    body="$(curl -s -w '\n%{http_code}' -H 'Content-Type: application/json' -X POST "$BOOKING_API_URL/api/v1/bookings" \
        -d "{\"sessionId\":$2,\"candidateNumber\":\"$1\",\"externalRef\":\"$3\"}")"
    code="${body##*$'\n'}"
    if [ "$code" = "201" ]; then
        PARTNER_IDS+=("$(echo "$body" | grep -o '"bookingId" *: *[0-9]*' | grep -o '[0-9]*$')")
        partner_ok=$((partner_ok + 1))
    else
        partner_fail=$((partner_fail + 1))
    fi
}

# --- bookings: interleave the two channels, round-robin over sessions ----
log "booking $PORTAL_BOOKINGS portal + $PARTNER_BOOKINGS partner seats"
i=0; p=0; q=0
while [ $p -lt "$PORTAL_BOOKINGS" ] || [ $q -lt "$PARTNER_BOOKINGS" ]; do
    if [ $p -lt "$PORTAL_BOOKINGS" ]; then
        row="${CANDIDATES[$i]}"; sid="${SESSION_IDS[$((i % ${#SESSION_IDS[@]}))]}"
        book_portal "${row%%|*}" "${row#*|}" "$sid"
        p=$((p + 1)); i=$((i + 1))
    fi
    if [ $q -lt "$PARTNER_BOOKINGS" ]; then
        row="${CANDIDATES[$i]}"; sid="${SESSION_IDS[$((i % ${#SESSION_IDS[@]}))]}"
        book_partner "${row#*|}" "$sid" "MT-$(printf '%04d' $((q + 1)))"
        q=$((q + 1)); i=$((i + 1))
    fi
done
log "portal : $portal_ok ok, $portal_fail failed"
log "partner: $partner_ok ok, $partner_fail failed"

# --- cancels: the first few bookings on each channel ---------------------
c_portal=0; c_partner=0
for ((k = 0; k < CANCELS && k < ${#PORTAL_IDS[@]}; k++)); do
    sql "EXEC dbo.usp_CancelBooking @BookingId = ${PORTAL_IDS[$k]}, @Reason = N'mixed-traffic', @Actor = 'portal'" >/dev/null \
        && c_portal=$((c_portal + 1))
done
for ((k = 0; k < CANCELS && k < ${#PARTNER_IDS[@]}; k++)); do
    code="$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BOOKING_API_URL/api/v1/bookings/${PARTNER_IDS[$k]}?reason=mixed-traffic")"
    [ "$code" = "200" ] && c_partner=$((c_partner + 1))
done
log "cancelled: $c_portal portal, $c_partner partner"

# --- summary ---------------------------------------------------------------
log "session summary (SessionId|Capacity|SeatsAvailable|Booked):"
sql "SELECT s.SessionId, s.Capacity, s.SeatsAvailable,
        (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED'))
     FROM dbo.ExamSession s WHERE s.SessionId IN ($(IFS=,; echo "${SESSION_IDS[*]}")) ORDER BY s.SessionId" | sed 's/^/    /'

CHECK="$ROOT/db/checks/seat-drift.sql"
if [ -f "$CHECK" ]; then
    rows="$( { echo "SET NOCOUNT ON;"; cat "$CHECK"; } | sqlq | grep -cv '^\s*$' || true)"
    log "db/checks/seat-drift.sql: $rows row(s)"
else
    log "db/checks/seat-drift.sql not found, skipping the seat check"
fi
log "done"
