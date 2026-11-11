#!/usr/bin/env bash
# capacity-sync writes Capacity - AccommodationCapacity - non-ET bookings; seat-drift.sql / procs expect Capacity - (BOOKED+ATTENDED).
. "$(dirname "$0")/common.sh"
DRIFT="SELECT COUNT(*) AS DriftRows FROM dbo.ExamSession s LEFT JOIN (SELECT SessionId, COUNT(*) a FROM dbo.Booking WHERE Status IN ('BOOKED','ATTENDED') GROUP BY SessionId) b ON b.SessionId=s.SessionId WHERE s.SeatsAvailable <> s.Capacity-ISNULL(b.a,0)"
say "seat-drift.sql row count before"; sql "$DRIFT"
seat_row 154
api POST "$SYNC/sync/run"
say "seat-drift.sql row count after"; sql "$DRIFT"
seat_row 154
sql "SELECT s.Status, COUNT(*) AS DriftRows FROM dbo.ExamSession s LEFT JOIN (SELECT SessionId, COUNT(*) a FROM dbo.Booking WHERE Status IN ('BOOKED','ATTENDED') GROUP BY SessionId) b ON b.SessionId=s.SessionId WHERE s.SeatsAvailable <> s.Capacity-ISNULL(b.a,0) GROUP BY s.Status"
sql "SELECT TOP 1 EntityName, EntityId, Action, Detail, Actor FROM dbo.AuditLog WHERE Action='CAPACITY_SYNC' ORDER BY AuditId DESC"
api POST "$SYNC/sync/run"
