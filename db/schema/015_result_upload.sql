/*
    dbo.ResultUpload - one row per imported score file (MBA-3827).
    results-service looks a file up by the SHA-256 of its bytes: a re-upload of the
    same content is only re-checked and inserts nothing. Additive and safe to re-run
    on an existing database (no drop).
*/
USE AssessmentDelivery;
GO

IF OBJECT_ID('dbo.ResultUpload') IS NULL
CREATE TABLE dbo.ResultUpload (
    UploadId   INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ResultUpload PRIMARY KEY,
    FileName   NVARCHAR(200) NOT NULL,
    Sha256     CHAR(64)      NOT NULL CONSTRAINT UQ_ResultUpload_Sha256 UNIQUE,
    RowsRead   INT           NOT NULL,
    Imported   INT           NOT NULL,
    Duplicates INT           NOT NULL,
    Conflicts  INT           NOT NULL,
    Errors     INT           NOT NULL,
    Actor      VARCHAR(60)   NOT NULL,
    UploadedAt DATETIME      NOT NULL CONSTRAINT DF_ResultUpload_UploadedAt DEFAULT (GETDATE())
);
GO
