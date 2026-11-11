#!/usr/bin/env bash
# results-service accepts a score for a session that has not happened yet, and the frozen Passed flag drives retake rules.
. "$(dirname "$0")/common.sh"
BID=${BID:-1956}
sql "SELECT b.BookingId, c.CandidateNumber, b.Status, s.SessionId, s.StartsAt, s.Status AS SessionStatus, e.ExamCode, e.PassingScore
     FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId=b.CandidateId JOIN dbo.ExamSession s ON s.SessionId=b.SessionId JOIN dbo.Exam e ON e.ExamId=s.ExamId WHERE b.BookingId=$BID"
api POST "$RESULTS/api/v1/results" "{\"bookingId\":$BID,\"score\":100}"
sql "SELECT BookingId, Status FROM dbo.Booking WHERE BookingId=$BID; SELECT ResultId, Score, Passed, ScoredAt, SourceFile FROM dbo.Result WHERE BookingId=$BID"
