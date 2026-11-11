/*
    Supporting index for usp_GetCapacityAudit (MBA-3828): AuditLog lookups by entity and time.
*/
USE AssessmentDelivery;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_AuditLog_Entity_LoggedAt' AND object_id = OBJECT_ID('dbo.AuditLog'))
    CREATE INDEX IX_AuditLog_Entity_LoggedAt ON dbo.AuditLog (EntityName, EntityId, LoggedAt) INCLUDE (Action, Actor);
GO
