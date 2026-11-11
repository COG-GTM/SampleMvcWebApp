/*
    Scheduling database (owned by scheduling-service, MBA-3821).

    Owned tables : exam_session, booking, waitlist_entry, capacity_change
    Snapshots    : test_center_snapshot, room_snapshot, exam_snapshot, candidate_snapshot,
                   exam_result_snapshot - copies of rows owned by other systems, loaded by the
                   importer (LegacyReferenceSource).  No foreign keys point at a snapshot and no
                   query ever joins across databases.

    Ids follow the migration integration contract:
      * exam_session.session_id = legacy ExamSession.SessionId (no identity)
      * center ids/codes        = legacy TestCenter.TestCenterId / CenterCode
      * booking.booking_id      = legacy BookingId for LEGACY_IMPORT rows,
                                  booking_id_seq (50,000,001+) for MODERN rows
*/

CREATE TABLE test_center_snapshot (
    center_id       INT            NOT NULL CONSTRAINT pk_test_center_snapshot PRIMARY KEY,
    center_code     VARCHAR(10)    NOT NULL CONSTRAINT uq_test_center_snapshot_code UNIQUE,
    name            NVARCHAR(100)  NOT NULL,
    city            NVARCHAR(60)   NOT NULL,
    region          VARCHAR(20)    NOT NULL,
    time_zone       VARCHAR(40)    NOT NULL,
    is_active       BIT            NOT NULL,
    source_version  BIGINT         NOT NULL,
    synced_at       DATETIME2(3)   NOT NULL
);

CREATE TABLE room_snapshot (
    room_id             INT           NOT NULL CONSTRAINT pk_room_snapshot PRIMARY KEY,
    center_id           INT           NOT NULL,
    room_name           NVARCHAR(50)  NOT NULL,
    seat_count          INT           NOT NULL,
    accommodation_seats INT           NOT NULL,
    is_active           BIT           NOT NULL,
    source_version      BIGINT        NOT NULL,
    synced_at           DATETIME2(3)  NOT NULL
);

CREATE TABLE exam_snapshot (
    exam_id           INT            NOT NULL CONSTRAINT pk_exam_snapshot PRIMARY KEY,
    exam_code         VARCHAR(20)    NOT NULL CONSTRAINT uq_exam_snapshot_code UNIQUE,
    title             NVARCHAR(200)  NOT NULL,
    duration_minutes  INT            NOT NULL,
    passing_score     INT            NOT NULL,
    retake_wait_days  INT            NOT NULL,
    is_published      BIT            NOT NULL,
    source_version    BIGINT         NOT NULL,
    synced_at         DATETIME2(3)   NOT NULL
);

CREATE TABLE candidate_snapshot (
    candidate_id                INT            NOT NULL CONSTRAINT pk_candidate_snapshot PRIMARY KEY,
    candidate_number            VARCHAR(12)    NOT NULL CONSTRAINT uq_candidate_snapshot_number UNIQUE,
    first_name                  NVARCHAR(60)   NOT NULL,
    last_name                   NVARCHAR(60)   NOT NULL,
    email                       NVARCHAR(200)  NOT NULL,
    default_accommodation_code  VARCHAR(10)    NULL,
    source_version              BIGINT         NOT NULL,
    synced_at                   DATETIME2(3)   NOT NULL
);

-- scored attempts (legacy Result + the sitting date); input to the retake rule only
CREATE TABLE exam_result_snapshot (
    result_id          INT           NOT NULL CONSTRAINT pk_exam_result_snapshot PRIMARY KEY,
    candidate_id       INT           NOT NULL,
    exam_id            INT           NOT NULL,
    session_id         INT           NOT NULL,
    session_starts_at  DATETIME2(0)  NOT NULL,
    score              INT           NOT NULL,
    passed             BIT           NOT NULL,
    source_version     BIGINT        NOT NULL,
    synced_at          DATETIME2(3)  NOT NULL
);
CREATE INDEX ix_exam_result_snapshot_candidate_exam ON exam_result_snapshot (candidate_id, exam_id);

CREATE TABLE exam_session (
    session_id              INT           NOT NULL CONSTRAINT pk_exam_session PRIMARY KEY,
    exam_id                 INT           NOT NULL,
    center_id               INT           NOT NULL,
    room_id                 INT           NOT NULL,
    starts_at               DATETIME2(0)  NOT NULL,   -- center-local wall clock, as in legacy
    capacity                INT           NOT NULL,
    accommodation_capacity  INT           NOT NULL,
    status                  VARCHAR(12)   NOT NULL,
    -- 1 once scheduling-service has written to the session (booking, cancel, capacity):
    -- from then on it owns the session and re-imports leave it (and its bookings) alone
    locally_modified        BIT           NOT NULL CONSTRAINT df_exam_session_locally_modified DEFAULT (0),
    legacy_last_modified    DATETIME2(3)  NULL,
    imported_at             DATETIME2(3)  NULL,
    last_modified           DATETIME2(3)  NOT NULL,
    CONSTRAINT ck_exam_session_status CHECK (status IN ('OPEN','CLOSED','DELIVERED','CANCELLED')),
    CONSTRAINT ck_exam_session_capacity CHECK (capacity >= 0 AND accommodation_capacity >= 0)
);
CREATE INDEX ix_exam_session_center_starts ON exam_session (center_id, starts_at);
CREATE INDEX ix_exam_session_exam_starts ON exam_session (exam_id, starts_at);

CREATE SEQUENCE booking_id_seq AS INT START WITH 50000001 INCREMENT BY 1 MINVALUE 50000001 NO CYCLE;

CREATE TABLE booking (
    booking_id          INT            NOT NULL CONSTRAINT pk_booking PRIMARY KEY,
    session_id          INT            NOT NULL CONSTRAINT fk_booking_session REFERENCES exam_session (session_id),
    candidate_id        INT            NOT NULL,
    status              VARCHAR(12)    NOT NULL,
    accommodation_code  VARCHAR(10)    NULL,
    channel             VARCHAR(10)    NOT NULL,
    external_ref        VARCHAR(40)    NULL,
    booked_at           DATETIME2(3)   NOT NULL,
    cancelled_at        DATETIME2(3)   NULL,
    cancel_reason       NVARCHAR(200)  NULL,
    origin              VARCHAR(13)    NOT NULL,
    legacy_booking_id   INT            NULL,       -- legacy id; filled by the M4 relay for MODERN rows
    last_modified       DATETIME2(3)   NOT NULL,
    CONSTRAINT ck_booking_status CHECK (status IN ('BOOKED','CANCELLED','WAITLISTED','ATTENDED')),
    CONSTRAINT ck_booking_channel CHECK (channel IN ('PORTAL','PARTNER','IMPORT')),
    CONSTRAINT ck_booking_origin CHECK (origin IN ('LEGACY_IMPORT','MODERN')),
    CONSTRAINT ck_booking_id_range CHECK ((origin = 'MODERN' AND booking_id >= 50000000)
                                       OR (origin = 'LEGACY_IMPORT' AND booking_id < 50000000))
);
CREATE INDEX ix_booking_session_status ON booking (session_id, status);
CREATE INDEX ix_booking_candidate ON booking (candidate_id, booked_at);

CREATE TABLE waitlist_entry (
    waitlist_entry_id   INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_waitlist_entry PRIMARY KEY,
    session_id          INT           NOT NULL CONSTRAINT fk_waitlist_entry_session REFERENCES exam_session (session_id),
    booking_id          INT           NOT NULL CONSTRAINT fk_waitlist_entry_booking REFERENCES booking (booking_id),
    candidate_id        INT           NOT NULL,
    position            INT           NOT NULL,
    added_at            DATETIME2(3)  NOT NULL,
    legacy_waitlist_id  INT           NULL,
    CONSTRAINT uq_waitlist_entry_booking UNIQUE (booking_id)
);
CREATE INDEX ix_waitlist_entry_session_position ON waitlist_entry (session_id, position);

CREATE TABLE capacity_change (
    capacity_change_id  BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_capacity_change PRIMARY KEY,
    session_id          INT            NOT NULL CONSTRAINT fk_capacity_change_session REFERENCES exam_session (session_id),
    old_capacity        INT            NOT NULL,
    new_capacity        INT            NOT NULL,
    bumped_booking_ids  VARCHAR(4000)  NULL,     -- comma separated, bump order
    actor               VARCHAR(60)    NOT NULL,
    changed_at          DATETIME2(3)   NOT NULL
);
CREATE INDEX ix_capacity_change_session ON capacity_change (session_id, changed_at);
