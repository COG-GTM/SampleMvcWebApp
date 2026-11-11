-- V1__scheduling_schema.sql - Scheduling database owned by scheduling-service (MBA-3821).
-- Nothing here references AssessmentDelivery: reference data arrives as snapshots copied by the
-- importer (importer/SnapshotImporter); there are no cross-database joins or linked servers.
-- Seats are NOT a stored counter: availability is derived from booking rows (ADR 0002, ODD-01/03/13).

-- ---------------------------------------------------------------- reference snapshots
CREATE TABLE test_center_snapshot (
    test_center_id  INT           NOT NULL CONSTRAINT pk_test_center_snapshot PRIMARY KEY,
    center_code     VARCHAR(10)   NOT NULL CONSTRAINT uq_test_center_snapshot_code UNIQUE,
    name            NVARCHAR(100) NOT NULL,
    city            NVARCHAR(60)  NOT NULL,
    region          VARCHAR(20)   NOT NULL,
    time_zone       VARCHAR(40)   NOT NULL,
    is_active       BIT           NOT NULL,
    source_version  BIGINT        NOT NULL,
    synced_at       DATETIME2(0)  NOT NULL
);

CREATE TABLE room_snapshot (
    room_id             INT          NOT NULL CONSTRAINT pk_room_snapshot PRIMARY KEY,
    test_center_id      INT          NOT NULL CONSTRAINT fk_room_snapshot_center REFERENCES test_center_snapshot(test_center_id),
    room_name           NVARCHAR(50) NOT NULL,
    seat_count          INT          NOT NULL,
    accommodation_seats INT          NOT NULL,
    is_active           BIT          NOT NULL,
    source_version      BIGINT       NOT NULL,
    synced_at           DATETIME2(0) NOT NULL
);

CREATE TABLE exam_snapshot (
    exam_id           INT           NOT NULL CONSTRAINT pk_exam_snapshot PRIMARY KEY,
    exam_code         VARCHAR(20)   NOT NULL CONSTRAINT uq_exam_snapshot_code UNIQUE,
    title             NVARCHAR(200) NOT NULL,
    owner_name        NVARCHAR(100) NOT NULL,
    duration_minutes  INT           NOT NULL,
    passing_score     INT           NOT NULL,
    retake_wait_days  INT           NOT NULL,
    is_published      BIT           NOT NULL,
    source_version    BIGINT        NOT NULL,
    synced_at         DATETIME2(0)  NOT NULL
);

CREATE TABLE candidate_snapshot (
    candidate_id               INT           NOT NULL CONSTRAINT pk_candidate_snapshot PRIMARY KEY,
    candidate_number           VARCHAR(12)   NOT NULL CONSTRAINT uq_candidate_snapshot_number UNIQUE,
    first_name                 NVARCHAR(60)  NOT NULL,
    last_name                  NVARCHAR(60)  NOT NULL,
    email                      NVARCHAR(200) NOT NULL,
    default_accommodation_code VARCHAR(10)   NULL,
    source_version             BIGINT        NOT NULL,
    synced_at                  DATETIME2(0)  NOT NULL
);

-- result history used only for retake eligibility (owned by results-service in legacy)
CREATE TABLE exam_result_snapshot (
    result_id       INT          NOT NULL CONSTRAINT pk_exam_result_snapshot PRIMARY KEY,
    candidate_id    INT          NOT NULL,
    exam_id         INT          NOT NULL,
    session_id      INT          NOT NULL,
    session_starts_at DATETIME2(0) NOT NULL,
    score           INT          NOT NULL,
    passed          BIT          NOT NULL,
    scored_at       DATETIME2(0) NOT NULL,
    source_version  BIGINT       NOT NULL,
    synced_at       DATETIME2(0) NOT NULL
);
CREATE INDEX ix_exam_result_snapshot_candidate_exam ON exam_result_snapshot (candidate_id, exam_id);

-- ---------------------------------------------------------------- seat inventory (owned here)
CREATE TABLE exam_session (
    session_id             INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_exam_session PRIMARY KEY,
    exam_id                INT          NOT NULL CONSTRAINT fk_exam_session_exam REFERENCES exam_snapshot(exam_id),
    test_center_id         INT          NOT NULL CONSTRAINT fk_exam_session_center REFERENCES test_center_snapshot(test_center_id),
    room_id                INT          NOT NULL CONSTRAINT fk_exam_session_room REFERENCES room_snapshot(room_id),
    starts_at              DATETIME2(0) NOT NULL,
    capacity               INT          NOT NULL CONSTRAINT ck_exam_session_capacity CHECK (capacity >= 0),
    accommodation_capacity INT          NOT NULL CONSTRAINT ck_exam_session_acc CHECK (accommodation_capacity >= 0),
    status                 VARCHAR(12)  NOT NULL CONSTRAINT ck_exam_session_status CHECK (status IN ('OPEN','CLOSED','DELIVERED','CANCELLED')),
    imported_at            DATETIME2(0) NULL,
    last_modified          DATETIME2(0) NOT NULL CONSTRAINT df_exam_session_last_modified DEFAULT (SYSDATETIME())
);
CREATE INDEX ix_exam_session_center_starts ON exam_session (test_center_id, starts_at);
CREATE INDEX ix_exam_session_exam_starts ON exam_session (exam_id, starts_at);

CREATE TABLE booking (
    booking_id         INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_booking PRIMARY KEY,
    session_id         INT           NOT NULL CONSTRAINT fk_booking_session REFERENCES exam_session(session_id),
    candidate_id       INT           NOT NULL CONSTRAINT fk_booking_candidate REFERENCES candidate_snapshot(candidate_id),
    status             VARCHAR(12)   NOT NULL CONSTRAINT ck_booking_status CHECK (status IN ('BOOKED','CANCELLED','WAITLISTED','ATTENDED')),
    accommodation_code VARCHAR(10)   NULL CONSTRAINT ck_booking_acc CHECK (accommodation_code IN ('ET25','ET50','SR','RB')),
    channel            VARCHAR(10)   NOT NULL CONSTRAINT ck_booking_channel CHECK (channel IN ('PORTAL','PARTNER','IMPORT')),
    external_ref       VARCHAR(40)   NULL,
    booked_at          DATETIME2(0)  NOT NULL CONSTRAINT df_booking_booked_at DEFAULT (SYSDATETIME()),
    cancelled_at       DATETIME2(0)  NULL,
    cancel_reason      NVARCHAR(200) NULL,
    rescheduled_from   INT           NULL CONSTRAINT fk_booking_rescheduled_from REFERENCES booking(booking_id),
    last_modified      DATETIME2(0)  NOT NULL CONSTRAINT df_booking_last_modified DEFAULT (SYSDATETIME())
);
CREATE INDEX ix_booking_session_status ON booking (session_id, status);
CREATE INDEX ix_booking_candidate ON booking (candidate_id, status);

CREATE TABLE waitlist_entry (
    waitlist_entry_id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_waitlist_entry PRIMARY KEY,
    session_id        INT          NOT NULL CONSTRAINT fk_waitlist_entry_session REFERENCES exam_session(session_id),
    candidate_id      INT          NOT NULL CONSTRAINT fk_waitlist_entry_candidate REFERENCES candidate_snapshot(candidate_id),
    booking_id        INT          NOT NULL CONSTRAINT fk_waitlist_entry_booking REFERENCES booking(booking_id)
                                            CONSTRAINT uq_waitlist_entry_booking UNIQUE,
    position          INT          NOT NULL,
    added_at          DATETIME2(0) NOT NULL CONSTRAINT df_waitlist_entry_added_at DEFAULT (SYSDATETIME())
);
CREATE INDEX ix_waitlist_entry_session ON waitlist_entry (session_id, position);

CREATE TABLE capacity_change (
    capacity_change_id INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_capacity_change PRIMARY KEY,
    session_id         INT          NOT NULL CONSTRAINT fk_capacity_change_session REFERENCES exam_session(session_id),
    old_capacity       INT          NOT NULL,
    new_capacity       INT          NOT NULL,
    old_accommodation_capacity INT  NOT NULL,
    new_accommodation_capacity INT  NOT NULL,
    demoted_bookings   INT          NOT NULL,
    reason             NVARCHAR(200) NULL,
    changed_by         VARCHAR(60)  NOT NULL,
    changed_at         DATETIME2(0) NOT NULL CONSTRAINT df_capacity_change_changed_at DEFAULT (SYSDATETIME())
);
CREATE INDEX ix_capacity_change_session ON capacity_change (session_id, changed_at);

-- one row per import run (who/when/how many) so operators can see snapshot freshness
CREATE TABLE snapshot_import_run (
    import_run_id  INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_snapshot_import_run PRIMARY KEY,
    mode           VARCHAR(20)  NOT NULL,
    centers        INT NOT NULL, rooms INT NOT NULL, exams INT NOT NULL, candidates INT NOT NULL,
    results        INT NOT NULL, sessions INT NOT NULL, bookings INT NOT NULL, waitlist INT NOT NULL,
    started_at     DATETIME2(0) NOT NULL,
    finished_at    DATETIME2(0) NOT NULL
);
