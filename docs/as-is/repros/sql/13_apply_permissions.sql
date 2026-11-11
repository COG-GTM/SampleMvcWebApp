-- What db/apply.* leaves behind: ad_app role membership/permissions and login policy. Read-only.
USE AssessmentDelivery;
SET NOCOUNT ON;
SELECT r.name AS RoleName FROM sys.database_role_members m JOIN sys.database_principals r ON r.principal_id=m.role_principal_id
  JOIN sys.database_principals u ON u.principal_id=m.member_principal_id WHERE u.name='ad_app';
SELECT permission_name, state_desc, class_desc FROM sys.database_permissions WHERE grantee_principal_id = USER_ID('ad_app');
SELECT name, is_policy_checked, is_expiration_checked, default_database_name FROM sys.sql_logins WHERE name='ad_app';
EXECUTE AS LOGIN='ad_app';
SELECT HAS_PERMS_BY_NAME('dbo.ExamSession','OBJECT','UPDATE') AS CanUpdateExamSession, HAS_PERMS_BY_NAME('dbo.AuditLog','OBJECT','DELETE') AS CanDeleteAuditLog,
       HAS_PERMS_BY_NAME('dbo.Booking','OBJECT','INSERT') AS CanInsertBooking, HAS_PERMS_BY_NAME('AssessmentDelivery','DATABASE','ALTER') AS CanAlterDb;
REVERT;
SELECT name, compatibility_level, recovery_model_desc, collation_name FROM sys.databases WHERE name='AssessmentDelivery';
SELECT @@VERSION AS SqlVersion;
