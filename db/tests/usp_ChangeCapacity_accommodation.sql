/*
    Regression test for usp_ChangeCapacity on sessions with accommodation seats.
    Creates its own rows, prints PASS/FAIL per case, removes the rows again.
    Run after schema/ and procs/:  sqlcmd -S localhost,1433 -U sa -P ... -C -b -i db/tests/usp_ChangeCapacity_accommodation.sql
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;

DECLARE @tc INT, @room INT, @exam INT, @s1 INT, @s2 INT, @c1 INT, @c2 INT, @fail INT = 0,
        @cap INT, @acc INT, @avail INT, @err NVARCHAR(2048);

INSERT INTO dbo.TestCenter (CenterCode, Name, City, Region, TimeZone) VALUES ('ZZTEST', N'Test', N'Test', 'EMEA', 'UTC');
SET @tc = SCOPE_IDENTITY();
INSERT INTO dbo.Room (TestCenterId, RoomName, SeatCount, AccommodationSeats) VALUES (@tc, N'T', 12, 3);
SET @room = SCOPE_IDENTITY();
INSERT INTO dbo.Exam (ExamCode, Title, OwnerName, DurationMinutes, PassingScore) VALUES ('ZZ-TEST', N'Test', N'Test', 60, 500);
SET @exam = SCOPE_IDENTITY();
INSERT INTO dbo.Candidate (CandidateNumber, FirstName, LastName, Email) VALUES ('NW99999901', N'A', N'Test', N'a@test'), ('NW99999902', N'B', N'Test', N'b@test');
SELECT @c1 = CandidateId FROM dbo.Candidate WHERE CandidateNumber = 'NW99999901';
SELECT @c2 = CandidateId FROM dbo.Candidate WHERE CandidateNumber = 'NW99999902';

-- s1: capacity 12, 3 accommodation seats, one standard booking, no ET bookings
INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable)
VALUES (@exam, @tc, @room, '20270115 09:00', 12, 3, 11);
SET @s1 = SCOPE_IDENTITY();
INSERT INTO dbo.Booking (SessionId, CandidateId, Status) VALUES (@s1, @c1, 'BOOKED');

-- s2: capacity 12, 3 accommodation seats, two ET25 bookings
INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable)
VALUES (@exam, @tc, @room, '20270122 09:00', 12, 3, 10);
SET @s2 = SCOPE_IDENTITY();
INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode) VALUES (@s2, @c1, 'BOOKED', 'ET25'), (@s2, @c2, 'BOOKED', 'ET25');

-- 1. reduce s1 to 2 seats: accommodation seats are unbooked, so the change goes through
BEGIN TRY
    EXEC dbo.usp_ChangeCapacity @SessionId = @s1, @NewCapacity = 2, @Actor = 'test';
    SELECT @cap = Capacity, @acc = AccommodationCapacity, @avail = SeatsAvailable FROM dbo.ExamSession WHERE SessionId = @s1;
    IF @cap = 2 AND @acc = 2 AND @avail = 1 PRINT 'PASS 1 reduction below unbooked accommodation seats accepted (capacity 2, accommodation 2, available 1)';
    ELSE BEGIN SET @fail += 1; PRINT CONCAT('FAIL 1 capacity=', @cap, ' accommodation=', @acc, ' available=', @avail); END
END TRY
BEGIN CATCH
    SET @fail += 1; PRINT 'FAIL 1 rejected: ' + ERROR_MESSAGE();
END CATCH

-- 2. reduce s2 to 1 seat: two ET25 seats are booked, so the change is rejected and nothing changes
BEGIN TRY
    EXEC dbo.usp_ChangeCapacity @SessionId = @s2, @NewCapacity = 1, @Actor = 'test';
    SET @fail += 1; PRINT 'FAIL 2 reduction below booked accommodation seats was accepted';
END TRY
BEGIN CATCH
    SET @err = ERROR_MESSAGE();
    SELECT @cap = Capacity, @acc = AccommodationCapacity FROM dbo.ExamSession WHERE SessionId = @s2;
    IF ERROR_STATE() = 5 AND @cap = 12 AND @acc = 3 PRINT 'PASS 2 rejected: ' + @err;
    ELSE BEGIN SET @fail += 1; PRINT CONCAT('FAIL 2 state=', ERROR_STATE(), ' capacity=', @cap, ' accommodation=', @acc); END
END CATCH

-- 3. reduce s2 to 8 seats: above the accommodation allocation, unchanged behaviour
BEGIN TRY
    EXEC dbo.usp_ChangeCapacity @SessionId = @s2, @NewCapacity = 8, @Actor = 'test';
    SELECT @cap = Capacity, @acc = AccommodationCapacity, @avail = SeatsAvailable FROM dbo.ExamSession WHERE SessionId = @s2;
    IF @cap = 8 AND @acc = 3 AND @avail = 6 PRINT 'PASS 3 ordinary reduction (capacity 8, accommodation 3, available 6)';
    ELSE BEGIN SET @fail += 1; PRINT CONCAT('FAIL 3 capacity=', @cap, ' accommodation=', @acc, ' available=', @avail); END
END TRY
BEGIN CATCH
    SET @fail += 1; PRINT 'FAIL 3 rejected: ' + ERROR_MESSAGE();
END CATCH

DELETE FROM dbo.AuditLog WHERE EntityName = 'ExamSession' AND EntityId IN (@s1, @s2);
DELETE FROM dbo.AuditLog WHERE EntityName IN ('Booking','Candidate','Exam') AND Actor = 'test';
DELETE FROM dbo.Booking WHERE SessionId IN (@s1, @s2);
DELETE FROM dbo.ExamSession WHERE SessionId IN (@s1, @s2);
DELETE FROM dbo.Candidate WHERE CandidateId IN (@c1, @c2);
DELETE FROM dbo.Exam WHERE ExamId = @exam;
DELETE FROM dbo.Room WHERE RoomId = @room;
DELETE FROM dbo.TestCenter WHERE TestCenterId = @tc;

IF @fail > 0 RAISERROR('%d usp_ChangeCapacity test case(s) failed.', 16, 1, @fail);
ELSE PRINT 'ALL PASS';
GO
