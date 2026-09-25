-- Infra-owned database objects. The app owns its tables (EF6 CreateDatabaseIfNotExists creates
-- Blogs/Posts/Tags/TagPosts on first start; the schema is what EF6 conventions produce).
-- Idempotent so `make up` / `make reset` can re-run it.
IF DB_ID('SampleWebAppDb') IS NULL
    CREATE DATABASE SampleWebAppDb;
GO
USE SampleWebAppDb;
GO

-- Least-privilege login used by the reporting consumer (read + execute only).
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'reporting_svc')
    CREATE LOGIN reporting_svc WITH PASSWORD = '$(REPORTING_DB_PASSWORD)', CHECK_POLICY = OFF;
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'reporting_svc')
    CREATE USER reporting_svc FOR LOGIN reporting_svc;
ALTER ROLE db_datareader ADD MEMBER reporting_svc;
GO

-- Stored procedure consumed by reporting/ (nightly "posts per blog" digest).
-- Depends on the EF6 conventional many-to-many join table dbo.TagPosts(Tag_TagId, Post_PostId).
CREATE OR ALTER PROCEDURE dbo.usp_PostSummaryByBlog
AS
BEGIN
    SET NOCOUNT ON;
    SELECT  b.BlogId,
            b.Name                              AS BlogName,
            COUNT(DISTINCT p.PostId)            AS PostCount,
            COUNT(tp.Tag_TagId)                 AS TagLinks,
            MAX(p.LastUpdated)                  AS LastPost
    FROM    dbo.Blogs b
            LEFT JOIN dbo.Posts    p  ON p.BlogId = b.BlogId
            LEFT JOIN dbo.TagPosts tp ON tp.Post_PostId = p.PostId
    GROUP BY b.BlogId, b.Name
    ORDER BY b.BlogId;
END
GO
GRANT EXECUTE ON dbo.usp_PostSummaryByBlog TO reporting_svc;
GO
