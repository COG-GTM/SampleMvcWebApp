/*
    Seed 041 - candidates NW00000001 .. NW00001000.
    Names are built from two fixed lists; ~5% have a default accommodation.
    NW00000901 .. NW00000999 are an unassigned block (no bookings, no results).
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;
GO

DECLARE @first TABLE (i INT PRIMARY KEY, nm NVARCHAR(60));
DECLARE @last  TABLE (i INT PRIMARY KEY, nm NVARCHAR(60));

INSERT INTO @first (i, nm) VALUES
 (0,N'Amelia'),(1,N'Oliver'),(2,N'Priya'),(3,N'James'),(4,N'Sofia'),(5,N'Liam'),(6,N'Hannah'),(7,N'Noah'),
 (8,N'Chloe'),(9,N'Ethan'),(10,N'Isabel'),(11,N'Lucas'),(12,N'Grace'),(13,N'Mateo'),(14,N'Aisha'),(15,N'Daniel'),
 (16,N'Mei'),(17,N'Samuel'),(18,N'Zara'),(19,N'Thomas'),(20,N'Leah'),(21,N'Arjun'),(22,N'Emma'),(23,N'Owen'),
 (24,N'Fatima'),(25,N'Jack'),(26,N'Lucia'),(27,N'Ryan'),(28,N'Nora'),(29,N'Kenji'),(30,N'Ruby'),(31,N'Adam'),
 (32,N'Elena'),(33,N'Marcus'),(34,N'Freya'),(35,N'Tariq'),(36,N'Ivy'),(37,N'Connor'),(38,N'Maya'),(39,N'Felix');

INSERT INTO @last (i, nm) VALUES
 (0,N'Ashworth'),(1,N'Bramley'),(2,N'Castell'),(3,N'Dunmore'),(4,N'Ellison'),(5,N'Fairbank'),(6,N'Garside'),(7,N'Hollis'),
 (8,N'Iqbal'),(9,N'Jessop'),(10,N'Kowalski'),(11,N'Lindqvist'),(12,N'Moreau'),(13,N'Nakamura'),(14,N'Okafor'),(15,N'Pennington'),
 (16,N'Quinlan'),(17,N'Rahman'),(18,N'Stroud'),(19,N'Thorne'),(20,N'Underwood'),(21,N'Varga'),(22,N'Whitlock'),(23,N'Yardley'),
 (24,N'Zielinski'),(25,N'Abbott'),(26,N'Beaumont'),(27,N'Chandra'),(28,N'Delacroix'),(29,N'Esposito'),(30,N'Fenwick'),(31,N'Gallagher'),
 (32,N'Haldane'),(33,N'Ivanova'),(34,N'Kearney'),(35,N'Lockhart'),(36,N'Marsh'),(37,N'Navarro'),(38,N'Osei'),(39,N'Pryce');

;WITH n AS (
    SELECT TOP (1000) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS n
    FROM sys.all_objects a CROSS JOIN sys.all_objects b
)
INSERT INTO dbo.Candidate (CandidateNumber, FirstName, LastName, Email, DateOfBirth, DefaultAccommodationCode, CreatedOn)
SELECT 'NW' + RIGHT('00000000' + CAST(n.n AS VARCHAR(8)), 8),
       f.nm, l.nm,
       LOWER(f.nm + N'.' + l.nm) + CAST(n.n AS NVARCHAR(8))
         + CASE n.n % 3 WHEN 0 THEN N'@example.com' WHEN 1 THEN N'@example.net' ELSE N'@example.org' END,
       DATEADD(DAY, CAST((CAST(n.n AS BIGINT) * 7919) % 13500 AS INT), CAST('19650101' AS DATE)),
       CASE WHEN n.n % 20 = 3 THEN
            CASE (n.n / 20) % 4 WHEN 0 THEN 'ET25' WHEN 1 THEN 'ET50' WHEN 2 THEN 'SR' ELSE 'RB' END
       END,
       DATEADD(MINUTE, n.n * 37, DATEADD(DAY, n.n * 5, CAST('20120102' AS DATETIME)))
FROM n
JOIN @first f ON f.i = (n.n * 7) % 40
JOIN @last  l ON l.i = (n.n * 13 + n.n / 40) % 40
ORDER BY n.n;
GO
