#!/usr/bin/env bash
# booking-api counts the retake wait in WORKING days; portal (RetakeRules.cs) and docs use calendar days.
. "$(dirname "$0")/common.sh"
CAND=${CAND:-NW00000113}; EXAM=${EXAM:-HPI-ACC1}
sql "SELECT c.CandidateNumber, e.ExamCode, s.StartsAt AS LastAttempt, r.Passed, e.RetakeWaitDays,
            CONVERT(date, DATEADD(DAY, e.RetakeWaitDays, s.StartsAt)) AS CalendarRuleEarliest
     FROM dbo.Result r JOIN dbo.Candidate c ON c.CandidateId=r.CandidateId JOIN dbo.Exam e ON e.ExamId=r.ExamId
     JOIN dbo.ExamSession s ON s.SessionId=r.SessionId WHERE c.CandidateNumber='$CAND' AND e.ExamCode='$EXAM'"
api GET "$BOOKING/api/v1/candidates/$CAND/eligibility?examCode=$EXAM&date=2026-12-07"
api GET "$BOOKING/api/v1/candidates/$CAND/eligibility?examCode=$EXAM&date=2026-12-18"
SID=$(val "SELECT TOP 1 s.SessionId FROM dbo.ExamSession s JOIN dbo.Exam e ON e.ExamId=s.ExamId WHERE e.ExamCode='$EXAM' AND s.Status='OPEN'
           AND s.StartsAt >= '2026-12-07' AND s.StartsAt < '2026-12-18' AND s.SeatsAvailable > 0 ORDER BY s.StartsAt")
sql "SELECT SessionId, StartsAt, SeatsAvailable FROM dbo.ExamSession WHERE SessionId=$SID"
api POST "$BOOKING/api/v1/bookings" "{\"sessionId\":$SID,\"candidateNumber\":\"$CAND\"}"
