-- MBA-3831: outbox event CandidateCapacityImpactNotified (one per candidate affected by a capacity change,
-- docs/events.md). The relay hands it to NotificationPort instead of AssessmentDelivery (legacy has no notifications,
-- MR-04). The type is appended to whatever ck_outbox_event_type allows at this point, so other migrations that
-- extend the list (e.g. V3) keep their values.
DECLARE @def NVARCHAR(MAX) = (SELECT definition FROM sys.check_constraints
                               WHERE name = 'ck_outbox_event_type' AND parent_object_id = OBJECT_ID('outbox_event'));
IF @def IS NULL
    THROW 50000, 'ck_outbox_event_type not found', 1;
IF CHARINDEX('''CandidateCapacityImpactNotified''', @def) = 0
BEGIN
    ALTER TABLE outbox_event DROP CONSTRAINT ck_outbox_event_type;
    DECLARE @sql NVARCHAR(MAX) = N'ALTER TABLE outbox_event ADD CONSTRAINT ck_outbox_event_type CHECK ('
        + @def + N' OR [event_type] = ''CandidateCapacityImpactNotified'')';
    EXEC sp_executesql @sql;
END
