-- scheduling-service own schema (database Scheduling). Migrated from db/schema/010_tables.sql:
-- ExamSession, Booking, Waitlist (+ the AuditLog rows the procs wrote). Column names, types, defaults and CHECKs are kept.
-- No triggers: trg_Booking_SeatSync and trg_ExamSession_Update are explicit service code.
-- No foreign keys to reference data: Exam/TestCenter/Room/Candidate are read-only replicas (contract section 5).

CREATE TABLE dbo.ExamSession (
    SessionId             INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ExamSession PRIMARY KEY,
    ExamId                INT         NOT NULL,
    TestCenterId          INT         NOT NULL,
    RoomId                INT         NOT NULL,
    StartsAt              DATETIME    NOT NULL,
    Capacity              INT         NOT NULL,
    AccommodationCapacity INT         NOT NULL CONSTRAINT DF_ExamSession_AccCap DEFAULT (0),
    SeatsAvailable        INT         NOT NULL,
    Status                VARCHAR(12) NOT NULL CONSTRAINT DF_ExamSession_Status DEFAULT ('OPEN'),
    LastModified          DATETIME    NOT NULL CONSTRAINT DF_ExamSession_LastModified DEFAULT (GETDATE()),
    RowVer                ROWVERSION  NOT NULL,
    CONSTRAINT CK_ExamSession_Status CHECK (Status IN ('OPEN','CLOSED','DELIVERED','CANCELLED'))
);
CREATE INDEX IX_ExamSession_Exam_Starts ON dbo.ExamSession (ExamId, StartsAt);
CREATE INDEX IX_ExamSession_Center ON dbo.ExamSession (TestCenterId, StartsAt);

CREATE TABLE dbo.Booking (
    BookingId         INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Booking PRIMARY KEY,
    SessionId         INT           NOT NULL CONSTRAINT FK_Booking_Session REFERENCES dbo.ExamSession(SessionId),
    CandidateId       INT           NOT NULL,
    Status            VARCHAR(12)   NOT NULL,
    AccommodationCode VARCHAR(10)   NULL,
    Channel           VARCHAR(10)   NOT NULL CONSTRAINT DF_Booking_Channel DEFAULT ('PORTAL'),
    ExternalRef       VARCHAR(40)   NULL,
    BookedAt          DATETIME      NOT NULL CONSTRAINT DF_Booking_BookedAt DEFAULT (GETDATE()),
    CancelledAt       DATETIME      NULL,
    CancelReason      NVARCHAR(200) NULL,
    LastModified      DATETIME      NOT NULL CONSTRAINT DF_Booking_LastModified DEFAULT (GETDATE()),
    CONSTRAINT CK_Booking_Status CHECK (Status IN ('BOOKED','CANCELLED','WAITLISTED','ATTENDED')),
    CONSTRAINT CK_Booking_Channel CHECK (Channel IN ('PORTAL','PARTNER','IMPORT'))
);
CREATE INDEX IX_Booking_Session ON dbo.Booking (SessionId, Status);
CREATE INDEX IX_Booking_Candidate ON dbo.Booking (CandidateId);

CREATE TABLE dbo.Waitlist (
    WaitlistId  INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Waitlist PRIMARY KEY,
    SessionId   INT      NOT NULL CONSTRAINT FK_Waitlist_Session REFERENCES dbo.ExamSession(SessionId),
    CandidateId INT      NOT NULL,
    BookingId   INT      NOT NULL CONSTRAINT FK_Waitlist_Booking REFERENCES dbo.Booking(BookingId),
    Position    INT      NOT NULL,
    AddedAt     DATETIME NOT NULL CONSTRAINT DF_Waitlist_AddedAt DEFAULT (GETDATE())
);
CREATE INDEX IX_Waitlist_Session ON dbo.Waitlist (SessionId, Position);
CREATE INDEX IX_Waitlist_Booking ON dbo.Waitlist (BookingId);

-- Booking / ExamSession audit (BOOK, CANCEL, CAPACITY, CAPACITY_SYNC). Actor defaults to the SQL login as in legacy (HB-17).
CREATE TABLE dbo.AuditLog (
    AuditId    BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_AuditLog PRIMARY KEY,
    EntityName VARCHAR(40)    NOT NULL,
    EntityId   INT            NOT NULL,
    Action     VARCHAR(30)    NOT NULL,
    Detail     NVARCHAR(1000) NULL,
    Actor      VARCHAR(60)    NOT NULL CONSTRAINT DF_AuditLog_Actor DEFAULT (SUSER_SNAME()),
    LoggedAt   DATETIME       NOT NULL CONSTRAINT DF_AuditLog_LoggedAt DEFAULT (GETDATE())
);
CREATE INDEX IX_AuditLog_Entity ON dbo.AuditLog (EntityName, EntityId);

-- Reference-data replicas (primary keys only, no FKs either way). SourceVersion = change-tracking version.
CREATE TABLE dbo.RefExam (
    ExamId INT NOT NULL CONSTRAINT PK_RefExam PRIMARY KEY,
    ExamCode VARCHAR(20) NULL, Title NVARCHAR(200) NULL, OwnerName NVARCHAR(100) NULL, DurationMinutes INT NULL, PassingScore INT NULL,
    RetakeWaitDays INT NULL, IsPublished BIT NULL,
    SourceVersion BIGINT NOT NULL, Deleted BIT NOT NULL CONSTRAINT DF_RefExam_Deleted DEFAULT (0),
    AppliedAt DATETIME NOT NULL CONSTRAINT DF_RefExam_AppliedAt DEFAULT (GETDATE())
);
CREATE TABLE dbo.RefTestCenter (
    TestCenterId INT NOT NULL CONSTRAINT PK_RefTestCenter PRIMARY KEY,
    CenterCode VARCHAR(10) NULL, Name NVARCHAR(100) NULL, City NVARCHAR(60) NULL, Region VARCHAR(20) NULL,
    TimeZone VARCHAR(40) NULL, IsActive BIT NULL,
    SourceVersion BIGINT NOT NULL, Deleted BIT NOT NULL CONSTRAINT DF_RefTestCenter_Deleted DEFAULT (0),
    AppliedAt DATETIME NOT NULL CONSTRAINT DF_RefTestCenter_AppliedAt DEFAULT (GETDATE())
);
CREATE TABLE dbo.RefRoom (
    RoomId INT NOT NULL CONSTRAINT PK_RefRoom PRIMARY KEY,
    TestCenterId INT NULL, RoomName NVARCHAR(50) NULL, SeatCount INT NULL, AccommodationSeats INT NULL, IsActive BIT NULL,
    SourceVersion BIGINT NOT NULL, Deleted BIT NOT NULL CONSTRAINT DF_RefRoom_Deleted DEFAULT (0),
    AppliedAt DATETIME NOT NULL CONSTRAINT DF_RefRoom_AppliedAt DEFAULT (GETDATE())
);
CREATE TABLE dbo.RefCandidate (
    CandidateId INT NOT NULL CONSTRAINT PK_RefCandidate PRIMARY KEY,
    CandidateNumber VARCHAR(12) NULL, FirstName NVARCHAR(60) NULL, LastName NVARCHAR(60) NULL, Email NVARCHAR(200) NULL,
    DefaultAccommodationCode VARCHAR(10) NULL,
    SourceVersion BIGINT NOT NULL, Deleted BIT NOT NULL CONSTRAINT DF_RefCandidate_Deleted DEFAULT (0),
    AppliedAt DATETIME NOT NULL CONSTRAINT DF_RefCandidate_AppliedAt DEFAULT (GETDATE())
);
CREATE INDEX IX_RefCandidate_Number ON dbo.RefCandidate (CandidateNumber);
CREATE INDEX IX_RefExam_Code ON dbo.RefExam (ExamCode);

CREATE TABLE dbo.ReplicationState (
    Entity VARCHAR(20) NOT NULL CONSTRAINT PK_ReplicationState PRIMARY KEY,
    LastVersion BIGINT NOT NULL,
    LastAppliedAt DATETIME NULL,
    LastPolledAt DATETIME NULL
);

-- Attempt outcomes for retake eligibility (contract section 7, option B): attendance result block + Result backfill.
CREATE TABLE dbo.Attempt (
    ResultId    INT NOT NULL CONSTRAINT PK_Attempt PRIMARY KEY,
    BookingId   INT NOT NULL,
    CandidateId INT NOT NULL,
    ExamId      INT NOT NULL,
    SessionId   INT NOT NULL,
    Score       INT NOT NULL,
    Passed      BIT NOT NULL,
    ScoredAt    DATETIME NULL,
    RecordedAt  DATETIME NOT NULL CONSTRAINT DF_Attempt_RecordedAt DEFAULT (GETDATE())
);
CREATE INDEX IX_Attempt_Candidate_Exam ON dbo.Attempt (CandidateId, ExamId);

CREATE TABLE dbo.ReconcilerRun (
    RunId      INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ReconcilerRun PRIMARY KEY,
    TriggerType VARCHAR(40) NOT NULL,
    Status     VARCHAR(10) NOT NULL,
    StartedAt  DATETIME NOT NULL,
    FinishedAt DATETIME NULL,
    Checked    INT NOT NULL CONSTRAINT DF_ReconcilerRun_Checked DEFAULT (0),
    Updated    INT NOT NULL CONSTRAINT DF_ReconcilerRun_Updated DEFAULT (0),
    Skipped    INT NOT NULL CONSTRAINT DF_ReconcilerRun_Skipped DEFAULT (0),
    Error      NVARCHAR(2000) NULL,
    Actor      VARCHAR(60) NULL
);

CREATE TABLE dbo.IdempotencyKey (
    IdemKey     VARCHAR(64)  NOT NULL,
    Client      VARCHAR(60)  NOT NULL,
    RequestHash CHAR(64)     NOT NULL,
    StatusCode  INT          NOT NULL, -- 0 = in progress
    Body        NVARCHAR(MAX) NULL,
    CreatedAt   DATETIME     NOT NULL CONSTRAINT DF_IdempotencyKey_CreatedAt DEFAULT (GETDATE()),
    CONSTRAINT PK_IdempotencyKey PRIMARY KEY (IdemKey, Client)
);

CREATE TABLE dbo.ImportRun (
    ImportId   INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ImportRun PRIMARY KEY,
    StartedAt  DATETIME NOT NULL CONSTRAINT DF_ImportRun_StartedAt DEFAULT (GETDATE()),
    Sessions   INT NOT NULL, Bookings INT NOT NULL, WaitlistRows INT NOT NULL, AuditRows INT NOT NULL, Attempts INT NOT NULL
);
