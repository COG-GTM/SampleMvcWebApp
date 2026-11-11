/*
    AssessmentDelivery core schema  (v1 2011, last structural change 2017)
    Shared by the portal (.NET) and the partner/results services (Java).
    Drops and recreates everything - DEV ONLY.
*/
USE AssessmentDelivery;
GO

IF OBJECT_ID('dbo.AuditLog')    IS NOT NULL DROP TABLE dbo.AuditLog;
IF OBJECT_ID('dbo.Result')      IS NOT NULL DROP TABLE dbo.Result;
IF OBJECT_ID('dbo.Waitlist')    IS NOT NULL DROP TABLE dbo.Waitlist;
IF OBJECT_ID('dbo.Booking')     IS NOT NULL DROP TABLE dbo.Booking;
IF OBJECT_ID('dbo.Candidate')   IS NOT NULL DROP TABLE dbo.Candidate;
IF OBJECT_ID('dbo.ExamSession') IS NOT NULL DROP TABLE dbo.ExamSession;
IF OBJECT_ID('dbo.Exam')        IS NOT NULL DROP TABLE dbo.Exam;
IF OBJECT_ID('dbo.Room')        IS NOT NULL DROP TABLE dbo.Room;
IF OBJECT_ID('dbo.TestCenter')  IS NOT NULL DROP TABLE dbo.TestCenter;
GO

CREATE TABLE dbo.TestCenter (
    TestCenterId    INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_TestCenter PRIMARY KEY,
    CenterCode      VARCHAR(10)   NOT NULL CONSTRAINT UQ_TestCenter_Code UNIQUE,
    Name            NVARCHAR(100) NOT NULL,
    City            NVARCHAR(60)  NOT NULL,
    Region          VARCHAR(20)   NOT NULL,          -- NA-EAST, NA-WEST, EMEA, APAC
    TimeZone        VARCHAR(40)   NOT NULL,          -- IANA id, display only (times stored as center-local)
    IsActive        BIT           NOT NULL CONSTRAINT DF_TestCenter_IsActive DEFAULT (1),
    CreatedOn       DATETIME      NOT NULL CONSTRAINT DF_TestCenter_CreatedOn DEFAULT (GETDATE())
);

CREATE TABLE dbo.Room (
    RoomId              INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Room PRIMARY KEY,
    TestCenterId        INT          NOT NULL CONSTRAINT FK_Room_TestCenter REFERENCES dbo.TestCenter(TestCenterId),
    RoomName            NVARCHAR(50) NOT NULL,
    SeatCount           INT          NOT NULL,
    AccommodationSeats  INT          NOT NULL CONSTRAINT DF_Room_AccSeats DEFAULT (0),  -- seats usable for extra-time candidates
    IsActive            BIT          NOT NULL CONSTRAINT DF_Room_IsActive DEFAULT (1)
);

CREATE TABLE dbo.Exam (
    ExamId          INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Exam PRIMARY KEY,
    ExamCode        VARCHAR(20)   NOT NULL CONSTRAINT UQ_Exam_Code UNIQUE,
    Title           NVARCHAR(200) NOT NULL,
    OwnerName       NVARCHAR(100) NOT NULL,          -- exam owner / publisher (fictional)
    DurationMinutes INT           NOT NULL,
    PassingScore    INT           NOT NULL,          -- scaled 0-1000
    RetakeWaitDays  INT           NOT NULL CONSTRAINT DF_Exam_RetakeWaitDays DEFAULT (30),
    IsPublished     BIT           NOT NULL CONSTRAINT DF_Exam_IsPublished DEFAULT (1),
    CreatedOn       DATETIME      NOT NULL CONSTRAINT DF_Exam_CreatedOn DEFAULT (GETDATE())
);

CREATE TABLE dbo.ExamSession (
    SessionId             INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ExamSession PRIMARY KEY,
    ExamId                INT         NOT NULL CONSTRAINT FK_ExamSession_Exam REFERENCES dbo.Exam(ExamId),
    TestCenterId          INT         NOT NULL CONSTRAINT FK_ExamSession_TestCenter REFERENCES dbo.TestCenter(TestCenterId),
    RoomId                INT         NOT NULL CONSTRAINT FK_ExamSession_Room REFERENCES dbo.Room(RoomId),
    StartsAt              DATETIME    NOT NULL,      -- center-local time
    Capacity              INT         NOT NULL,      -- total seats incl. accommodation seats
    AccommodationCapacity INT         NOT NULL CONSTRAINT DF_ExamSession_AccCap DEFAULT (0),
    SeatsAvailable        INT         NOT NULL,      -- denormalised counter, maintained by usp_BookSeat / usp_CancelBooking
    Status                VARCHAR(12) NOT NULL CONSTRAINT DF_ExamSession_Status DEFAULT ('OPEN'),
    LastModified          DATETIME    NOT NULL CONSTRAINT DF_ExamSession_LastModified DEFAULT (GETDATE()),
    CONSTRAINT CK_ExamSession_Status CHECK (Status IN ('OPEN','CLOSED','DELIVERED','CANCELLED'))
);
CREATE INDEX IX_ExamSession_Exam_Starts ON dbo.ExamSession (ExamId, StartsAt);
CREATE INDEX IX_ExamSession_Center ON dbo.ExamSession (TestCenterId, StartsAt);

CREATE TABLE dbo.Candidate (
    CandidateId              INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Candidate PRIMARY KEY,
    CandidateNumber          VARCHAR(12)   NOT NULL CONSTRAINT UQ_Candidate_Number UNIQUE,   -- e.g. NW00000042
    FirstName                NVARCHAR(60)  NOT NULL,
    LastName                 NVARCHAR(60)  NOT NULL,
    Email                    NVARCHAR(200) NOT NULL,
    DateOfBirth              DATE          NULL,
    DefaultAccommodationCode VARCHAR(10)   NULL,     -- see Booking.AccommodationCode
    CreatedOn                DATETIME      NOT NULL CONSTRAINT DF_Candidate_CreatedOn DEFAULT (GETDATE())
);

CREATE TABLE dbo.Booking (
    BookingId         INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Booking PRIMARY KEY,
    SessionId         INT           NOT NULL CONSTRAINT FK_Booking_Session REFERENCES dbo.ExamSession(SessionId),
    CandidateId       INT           NOT NULL CONSTRAINT FK_Booking_Candidate REFERENCES dbo.Candidate(CandidateId),
    Status            VARCHAR(12)   NOT NULL,
    AccommodationCode VARCHAR(10)   NULL,            -- ET25 / ET50 = extra time (needs accommodation seat), SR = separate room, RB = rest breaks
    Channel           VARCHAR(10)   NOT NULL CONSTRAINT DF_Booking_Channel DEFAULT ('PORTAL'),  -- PORTAL, PARTNER, IMPORT
    ExternalRef       VARCHAR(40)   NULL,            -- partner reference
    BookedAt          DATETIME      NOT NULL CONSTRAINT DF_Booking_BookedAt DEFAULT (GETDATE()),
    CancelledAt       DATETIME      NULL,
    CancelReason      NVARCHAR(200) NULL,
    LastModified      DATETIME      NOT NULL CONSTRAINT DF_Booking_LastModified DEFAULT (GETDATE()),
    CONSTRAINT CK_Booking_Status CHECK (Status IN ('BOOKED','CANCELLED','WAITLISTED','ATTENDED')),
    CONSTRAINT CK_Booking_Channel CHECK (Channel IN ('PORTAL','PARTNER','IMPORT'))
);
CREATE INDEX IX_Booking_Session_Status ON dbo.Booking (SessionId, Status);
CREATE INDEX IX_Booking_Candidate ON dbo.Booking (CandidateId);

CREATE TABLE dbo.Waitlist (
    WaitlistId  INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Waitlist PRIMARY KEY,
    SessionId   INT      NOT NULL CONSTRAINT FK_Waitlist_Session REFERENCES dbo.ExamSession(SessionId),
    CandidateId INT      NOT NULL CONSTRAINT FK_Waitlist_Candidate REFERENCES dbo.Candidate(CandidateId),
    BookingId   INT      NOT NULL CONSTRAINT FK_Waitlist_Booking REFERENCES dbo.Booking(BookingId),
    Position    INT      NOT NULL,
    AddedAt     DATETIME NOT NULL CONSTRAINT DF_Waitlist_AddedAt DEFAULT (GETDATE())
);

CREATE TABLE dbo.Result (
    ResultId    INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Result PRIMARY KEY,
    BookingId   INT           NOT NULL CONSTRAINT FK_Result_Booking REFERENCES dbo.Booking(BookingId),
    CandidateId INT           NOT NULL CONSTRAINT FK_Result_Candidate REFERENCES dbo.Candidate(CandidateId),
    ExamId      INT           NOT NULL CONSTRAINT FK_Result_Exam REFERENCES dbo.Exam(ExamId),
    SessionId   INT           NOT NULL CONSTRAINT FK_Result_Session REFERENCES dbo.ExamSession(SessionId),
    Score       INT           NOT NULL,
    Passed      BIT           NOT NULL,
    ScoredAt    DATETIME      NOT NULL,
    SourceFile  NVARCHAR(200) NULL,                  -- score file name, NULL when posted via REST
    ReceivedAt  DATETIME      NOT NULL CONSTRAINT DF_Result_ReceivedAt DEFAULT (GETDATE()),
    CONSTRAINT UQ_Result_Booking UNIQUE (BookingId)
);
CREATE INDEX IX_Result_Candidate_Exam ON dbo.Result (CandidateId, ExamId);

CREATE TABLE dbo.AuditLog (
    AuditId    BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_AuditLog PRIMARY KEY,
    EntityName VARCHAR(40)    NOT NULL,
    EntityId   INT            NOT NULL,
    Action     VARCHAR(30)    NOT NULL,
    Detail     NVARCHAR(1000) NULL,
    Actor      VARCHAR(60)    NOT NULL CONSTRAINT DF_AuditLog_Actor DEFAULT (SUSER_SNAME()),
    LoggedAt   DATETIME       NOT NULL CONSTRAINT DF_AuditLog_LoggedAt DEFAULT (GETDATE())
);
GO
