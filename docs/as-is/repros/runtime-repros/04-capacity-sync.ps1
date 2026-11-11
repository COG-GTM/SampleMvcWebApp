# capacity-sync (POST /sync/run) versus the seat-drift formula, after the writes made by repros 01-03.
. "$PSScriptRoot\_common.ps1"
$driftSql = "SELECT COUNT(*) FROM dbo.ExamSession s WHERE s.SeatsAvailable <> s.Capacity - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED'))"
$watch = @(Q "SELECT TOP 3 s.SessionId FROM dbo.ExamSession s WHERE s.Status='OPEN' AND s.SeatsAvailable <> s.Capacity - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) ORDER BY s.SessionId DESC")
$acc = @(Q "SELECT TOP 1 s.SessionId FROM dbo.ExamSession s WHERE s.Status='OPEN' AND s.AccommodationCapacity > 0 AND s.StartsAt > GETDATE()
    AND EXISTS (SELECT 1 FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status='BOOKED' AND b.AccommodationCode IN ('ET25','ET50')) ORDER BY s.SessionId")
$plain = @(Q "SELECT TOP 1 s.SessionId FROM dbo.ExamSession s WHERE s.Status='OPEN' AND s.AccommodationCapacity > 0 AND s.StartsAt > GETDATE()
    AND NOT EXISTS (SELECT 1 FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.AccommodationCode IN ('ET25','ET50')) ORDER BY s.SessionId")
$ids = @($watch + $acc + $plain) | Select-Object -Unique
Show 'sessions drifting by seat-drift.sql formula (before sync)' (Q $driftSql)
foreach ($s in $ids) { Show "session $s before" (SeatState $s) }
$t = Measure-Command { $script:resp = Api POST "$Sync/sync/run" }
Show "POST /sync/run ($([int]$t.TotalMilliseconds) ms)" $script:resp
foreach ($s in $ids) { Show "session $s after" (SeatState $s) }
Show 'sessions drifting by seat-drift.sql formula (after sync)' (Q $driftSql)
Show 'drift after sync by AccommodationCapacity>0 (count)' (Q "SELECT CASE WHEN s.AccommodationCapacity>0 THEN 'AccCap>0' ELSE 'AccCap=0' END+' '+s.Status+' '+CAST(COUNT(*) AS varchar) FROM dbo.ExamSession s WHERE s.SeatsAvailable <> s.Capacity - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) GROUP BY CASE WHEN s.AccommodationCapacity>0 THEN 'AccCap>0' ELSE 'AccCap=0' END, s.Status")
Show 'latest CAPACITY_SYNC audit row' (Q "SELECT TOP 1 Action, EntityName, EntityId, Actor, LEFT(ISNULL(Detail,'NULL'),100) FROM dbo.AuditLog WHERE Action='CAPACITY_SYNC' ORDER BY AuditId DESC")
Show 'second POST /sync/run immediately after' (Api POST "$Sync/sync/run")
