/*
    Seed 040 - reference data: test centres, rooms, exams.
    DEV / DEMO DATA ONLY.  All names are fictional.

    The seed scripts (040..049) are deterministic: same rows, same ids on every
    run.  No NEWID()/RAND(), no GETDATE()-relative dates - everything hangs off
    fixed anchor dates.  Run them in order after schema, procs and triggers.
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

-- clear down in FK order so the seed can be re-run on its own
-- (AuditLog last: the candidate trigger logs the deletes)
DELETE FROM dbo.Result;
DELETE FROM dbo.Waitlist;
DELETE FROM dbo.Booking;
DELETE FROM dbo.Candidate;
DELETE FROM dbo.ExamSession;
DELETE FROM dbo.Exam;
DELETE FROM dbo.Room;
DELETE FROM dbo.TestCenter;
DELETE FROM dbo.AuditLog;

-- RESEED 0 on a table that never had rows would hand out id 0, so only reseed used tables
DECLARE @t SYSNAME, @sql NVARCHAR(400);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR
    SELECT OBJECT_NAME(object_id) FROM sys.identity_columns
     WHERE OBJECT_SCHEMA_NAME(object_id) = 'dbo' AND last_value IS NOT NULL
       AND OBJECT_NAME(object_id) IN ('AuditLog','Result','Waitlist','Booking','Candidate','ExamSession','Exam','Room','TestCenter');
OPEN c;
FETCH NEXT FROM c INTO @t;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'DBCC CHECKIDENT (''dbo.' + @t + N''', RESEED, 0) WITH NO_INFOMSGS;';
    EXEC (@sql);
    FETCH NEXT FROM c INTO @t;
END
CLOSE c; DEALLOCATE c;
GO

/* ---------- test centres ---------- */
INSERT INTO dbo.TestCenter (CenterCode, Name, City, Region, TimeZone, IsActive, CreatedOn) VALUES
 ('BOS01', N'Harborview Testing Centre',        N'Boston',        'NA-EAST', 'America/New_York',    1, '20110314'),
 ('NYC01', N'Midtown Assessment Suites (old)',  N'New York',      'NA-EAST', 'America/New_York',    0, '20110314'),
 ('NYC02', N'Midtown Assessment Suites',        N'New York',      'NA-EAST', 'America/New_York',    1, '20160905'),
 ('PHL01', N'Schuylkill Learning Centre',       N'Philadelphia',  'NA-EAST', 'America/New_York',    1, '20120102'),
 ('ATL01', N'Peachtree Exam Centre',            N'Atlanta',       'NA-EAST', 'America/New_York',    1, '20120611'),
 ('TOR01', N'Lakeshore Assessment Centre',      N'Toronto',       'NA-EAST', 'America/Toronto',     1, '20130218'),
 ('SEA01', N'Puget Testing Rooms',              N'Seattle',       'NA-WEST', 'America/Los_Angeles', 1, '20110801'),
 ('SFO01', N'Bayside Assessment Centre',        N'San Francisco', 'NA-WEST', 'America/Los_Angeles', 1, '20110801'),
 ('DEN01', N'Front Range Exam Centre',          N'Denver',        'NA-WEST', 'America/Denver',      1, '20140407'),
 ('PHX01', N'Desert Ridge Testing',             N'Phoenix',       'NA-WEST', 'America/Phoenix',     1, '20140407'),
 ('VAN01', N'Granville Exam Centre',            N'Vancouver',     'NA-WEST', 'America/Vancouver',   1, '20150119'),
 ('LDN01', N'Thameside Assessment Centre',      N'London',        'EMEA',    'Europe/London',       1, '20110502'),
 ('MAN01', N'Northgate Testing Centre',         N'Manchester',    'EMEA',    'Europe/London',       1, '20120910'),
 ('LDS01', N'Aire Valley Exam Centre',          N'Leeds',         'EMEA',    'Europe/London',       1, '20130603'),
 ('DUB01', N'Liffey Assessment Rooms',          N'Dublin',        'EMEA',    'Europe/Dublin',       1, '20131104'),
 ('AMS01', N'Canal Ring Testing',               N'Amsterdam',     'EMEA',    'Europe/Amsterdam',    1, '20150608'),
 ('FRA01', N'Mainhafen Exam Centre',            N'Frankfurt',     'EMEA',    'Europe/Berlin',       1, '20160111'),
 ('SYD01', N'Harbourside Exam Centre',          N'Sydney',        'APAC',    'Australia/Sydney',    1, '20140915'),
 ('MEL01', N'Yarra Assessment Centre',          N'Melbourne',     'APAC',    'Australia/Melbourne', 1, '20150302'),
 ('SIN01', N'Marina Testing Hub',               N'Singapore',     'APAC',    'Asia/Singapore',      1, '20160509'),
 ('AKL01', N'Waitemata Exam Rooms',             N'Auckland',      'APAC',    'Pacific/Auckland',    1, '20170821');
GO

/* ---------- rooms ---------- */
INSERT INTO dbo.Room (TestCenterId, RoomName, SeatCount, AccommodationSeats, IsActive)
SELECT tc.TestCenterId, r.RoomName, r.SeatCount, r.AccSeats, r.IsActive
FROM (VALUES
 ('BOS01', N'Room A',          24, 2, 1), ('BOS01', N'Room B',          12, 0, 1),
 ('NYC01', N'Suite 1',         20, 0, 0),
 ('NYC02', N'Suite 4',         30, 3, 1), ('NYC02', N'Suite 5',         16, 1, 1), ('NYC02', N'Quiet Room', 8, 2, 1),
 ('PHL01', N'Lab 1',           18, 1, 1),
 ('ATL01', N'Room 101',        20, 0, 1), ('ATL01', N'Room 102',        14, 2, 1),
 ('TOR01', N'Main Hall',       28, 2, 1), ('TOR01', N'Annex',           10, 0, 1),
 ('SEA01', N'Room 1',          16, 1, 1),
 ('SFO01', N'Room North',      22, 2, 1), ('SFO01', N'Room South',      12, 0, 1),
 ('DEN01', N'Testing Lab',     15, 1, 1),
 ('PHX01', N'Room 1',          12, 0, 1), ('PHX01', N'Room 2',          10, 1, 1),
 ('VAN01', N'Exam Room',       18, 2, 1),
 ('LDN01', N'Room 1',          26, 3, 1), ('LDN01', N'Room 2',          16, 0, 1), ('LDN01', N'Small Room', 8, 1, 1),
 ('MAN01', N'Room A',          20, 1, 1), ('MAN01', N'Room B',          12, 0, 1),
 ('LDS01', N'Lab',             14, 2, 1),
 ('DUB01', N'Room 1',          18, 0, 1), ('DUB01', N'Room 2',          10, 2, 1),
 ('AMS01', N'Zaal 1',          16, 1, 1),
 ('FRA01', N'Raum 1',          20, 2, 1), ('FRA01', N'Raum 2',          12, 0, 1),
 ('SYD01', N'Level 3 Room',    24, 2, 1), ('SYD01', N'Level 4 Room',    12, 0, 1),
 ('MEL01', N'Room 1',          18, 1, 1),
 ('SIN01', N'Room A',          22, 2, 1), ('SIN01', N'Room B',          14, 0, 1), ('SIN01', N'Room C', 9, 1, 1),
 ('AKL01', N'Exam Room',       14, 1, 1)
) r (CenterCode, RoomName, SeatCount, AccSeats, IsActive)
JOIN dbo.TestCenter tc ON tc.CenterCode = r.CenterCode
ORDER BY tc.TestCenterId, r.RoomName;
GO

/* ---------- exams ---------- */
INSERT INTO dbo.Exam (ExamCode, Title, OwnerName, DurationMinutes, PassingScore, RetakeWaitDays, IsPublished, CreatedOn) VALUES
 ('HPI-ACC1',  N'Foundations of Financial Accounting',      N'Halden Professional Institute',     120, 650, 30, 1, '20110601'),
 ('HPI-ACC2',  N'Management Accounting',                    N'Halden Professional Institute',     150, 650, 30, 1, '20110601'),
 ('HPI-TAX1',  N'Business Taxation',                        N'Halden Professional Institute',     120, 600, 30, 1, '20130114'),
 ('BCB-PM100', N'Project Management Practitioner',          N'Brightwater Certification Board',   180, 700, 30, 1, '20120305'),
 ('BCB-PM200', N'Programme Management Advanced',            N'Brightwater Certification Board',   180, 720, 30, 1, '20140210'),
 ('BCB-AGL',   N'Agile Delivery Essentials',                N'Brightwater Certification Board',    90, 650, 30, 1, '20170403'),
 ('CTA-NET1',  N'Network Fundamentals',                     N'Corvane Technical Academy',          90, 600, 30, 1, '20120917'),
 ('CTA-SEC2',  N'Security Operations Associate',            N'Corvane Technical Academy',         120, 700, 30, 1, '20150518'),
 ('CTA-CLD3',  N'Cloud Infrastructure Professional',        N'Corvane Technical Academy',         150, 720, 30, 1, '20180226'),
 ('MHL-RN1',   N'Registered Nurse Licensure - Part 1',      N'Meridale Health Licensing Council', 240, 650, 30, 1, '20131007'),
 ('MHL-RN2',   N'Registered Nurse Licensure - Part 2',      N'Meridale Health Licensing Council', 240, 650, 30, 1, '20131007'),
 ('MHL-PHT',   N'Pharmacy Technician Certificate',          N'Meridale Health Licensing Council', 120, 600, 30, 1, '20160822');
GO
