/*
    row-counts.sql - row counts per table, handy for comparing two runs of the seed.
    Read-only.
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;

SELECT 'TestCenter' AS TableName, COUNT(*) AS Rows FROM dbo.TestCenter
UNION ALL SELECT 'Room',        COUNT(*) FROM dbo.Room
UNION ALL SELECT 'Exam',        COUNT(*) FROM dbo.Exam
UNION ALL SELECT 'ExamSession', COUNT(*) FROM dbo.ExamSession
UNION ALL SELECT 'Candidate',   COUNT(*) FROM dbo.Candidate
UNION ALL SELECT 'Booking',     COUNT(*) FROM dbo.Booking
UNION ALL SELECT 'Waitlist',    COUNT(*) FROM dbo.Waitlist
UNION ALL SELECT 'Result',      COUNT(*) FROM dbo.Result
UNION ALL SELECT 'AuditLog',    COUNT(*) FROM dbo.AuditLog;

SELECT s.Status AS SessionStatus, b.Status AS BookingStatus, COUNT(*) AS Bookings
FROM dbo.Booking b
JOIN dbo.ExamSession s ON s.SessionId = b.SessionId
GROUP BY s.Status, b.Status
ORDER BY s.Status, b.Status;
GO
