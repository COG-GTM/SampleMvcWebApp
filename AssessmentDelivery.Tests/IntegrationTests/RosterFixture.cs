using System;
using System.Collections.Generic;
using System.Data.SqlClient;

namespace AssessmentDelivery.Tests.IntegrationTests
{
    /// <summary>
    /// Creates an isolated exam session with one booking per status (plus quoting edge cases) in the
    /// AssessmentDelivery database and removes it again. Rows are inserted directly; nothing in the
    /// seeded data is changed.
    /// </summary>
    internal sealed class RosterFixture
    {
        public const string DefaultConnectionString =
            "Data Source=localhost,1433;Initial Catalog=AssessmentDelivery;Integrated Security=SSPI;Connect Timeout=5;Application Name=AssessmentDeliveryTests";

        public static string ConnectionString
        {
            get { return Environment.GetEnvironmentVariable("AD_TEST_CONNECTION") ?? DefaultConnectionString; }
        }

        public const string NumberPrefix = "NWT3826";

        public int SessionId { get; private set; }
        public int TestCenterId { get; private set; }
        public string CenterCode { get; private set; }
        public string ExamCode { get; private set; }

        /// <summary>CandidateNumber, LastName, FirstName, Status, Channel, AccommodationCode, WaitlistPosition</summary>
        public static readonly object[][] Bookings =
        {
            new object[] { NumberPrefix + "0001", "O'Neil", "\"Jo\"", "BOOKED", "PORTAL", null, null },
            new object[] { NumberPrefix + "0002", "Abbott", "Zara", "ATTENDED", "IMPORT", "ET25", null },
            new object[] { NumberPrefix + "0003", "Smith, Jr.", "Al", "BOOKED", "PORTAL", "SR", null },
            new object[] { NumberPrefix + "0004", "Cancel", "Carl", "CANCELLED", "PORTAL", null, null },
            new object[] { NumberPrefix + "0005", "Young", "Wendy", "WAITLISTED", "PARTNER", null, 1 },
            new object[] { NumberPrefix + "0006", "Baker", "Will", "WAITLISTED", "PORTAL", "RB", 3 },
            new object[] { NumberPrefix + "0007", "Orphan", "Wade", "WAITLISTED", "PORTAL", null, null }
        };

        public static bool DatabaseAvailable(out string reason)
        {
            try
            {
                using (var cn = new SqlConnection(ConnectionString))
                {
                    cn.Open();
                    reason = null;
                    return true;
                }
            }
            catch (Exception ex)
            {
                reason = "AssessmentDelivery database not reachable (" + ex.Message + "); set AD_TEST_CONNECTION or run scripts/run-legacy.ps1";
                return false;
            }
        }

        private static int Scalar(SqlConnection cn, SqlTransaction tx, string sql, params object[] nameValues)
        {
            using (var cmd = new SqlCommand(sql, cn, tx))
            {
                for (var i = 0; i < nameValues.Length; i += 2)
                    cmd.Parameters.AddWithValue((string)nameValues[i], nameValues[i + 1] ?? DBNull.Value);
                var result = cmd.ExecuteScalar();
                return result == null || result is DBNull ? 0 : Convert.ToInt32(result);
            }
        }

        public void Create()
        {
            Remove();
            using (var cn = new SqlConnection(ConnectionString))
            {
                cn.Open();
                using (var tx = cn.BeginTransaction())
                {
                    SessionId = Scalar(cn, tx, @"
INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable, Status)
SELECT s.ExamId, s.TestCenterId, s.RoomId, '2026-12-24T09:00:00', 10, 2, 7, 'OPEN' FROM dbo.ExamSession s WHERE s.SessionId = 154;
SELECT CAST(SCOPE_IDENTITY() AS INT);");

                    var minute = 0;
                    foreach (var b in Bookings)
                    {
                        var number = (string)b[0];
                        var candidateId = Scalar(cn, tx, @"
INSERT INTO dbo.Candidate (CandidateNumber, FirstName, LastName, Email) VALUES (@n, @f, @l, @e);
SELECT CAST(SCOPE_IDENTITY() AS INT);",
                            "@n", number, "@f", b[2], "@l", b[1], "@e", number.ToLowerInvariant() + "@example.test");

                        var bookingId = Scalar(cn, tx, @"
INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel, BookedAt, CancelledAt)
VALUES (@s, @c, @st, @acc, @ch, @at, CASE WHEN @st = 'CANCELLED' THEN @at END);
SELECT CAST(SCOPE_IDENTITY() AS INT);",
                            "@s", SessionId, "@c", candidateId, "@st", b[3], "@acc", b[5], "@ch", b[4],
                            "@at", new DateTime(2026, 11, 1, 8, minute++, 0));

                        if (b[6] != null)
                            Scalar(cn, tx, "INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position) VALUES (@s, @c, @b, @p);",
                                "@s", SessionId, "@c", candidateId, "@b", bookingId, "@p", b[6]);
                    }

                    using (var cmd = new SqlCommand(@"
SELECT s.TestCenterId, c.CenterCode, e.ExamCode FROM dbo.ExamSession s
JOIN dbo.TestCenter c ON c.TestCenterId = s.TestCenterId JOIN dbo.Exam e ON e.ExamId = s.ExamId
WHERE s.SessionId = @s;", cn, tx))
                    {
                        cmd.Parameters.AddWithValue("@s", SessionId);
                        using (var r = cmd.ExecuteReader())
                        {
                            r.Read();
                            TestCenterId = r.GetInt32(0);
                            CenterCode = r.GetString(1);
                            ExamCode = r.GetString(2);
                        }
                    }
                    tx.Commit();
                }
            }
        }

        /// <summary>Deletes every fixture row, including the audit rows the candidate delete trigger writes.</summary>
        public static void Remove()
        {
            using (var cn = new SqlConnection(ConnectionString))
            {
                cn.Open();
                Scalar(cn, null, @"
DECLARE @c TABLE (CandidateId INT PRIMARY KEY);
INSERT INTO @c SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber LIKE @p + '%';
DECLARE @s TABLE (SessionId INT PRIMARY KEY);
INSERT INTO @s SELECT DISTINCT SessionId FROM dbo.Booking WHERE CandidateId IN (SELECT CandidateId FROM @c);
DELETE FROM dbo.Waitlist WHERE CandidateId IN (SELECT CandidateId FROM @c);
DELETE FROM dbo.Booking WHERE CandidateId IN (SELECT CandidateId FROM @c);
DELETE FROM dbo.ExamSession WHERE SessionId IN (SELECT SessionId FROM @s) AND SessionId > 462
    AND NOT EXISTS (SELECT 1 FROM dbo.Booking b WHERE b.SessionId = ExamSession.SessionId);
DELETE FROM dbo.Candidate WHERE CandidateId IN (SELECT CandidateId FROM @c);
DELETE FROM dbo.AuditLog WHERE EntityName = 'Candidate' AND EntityId IN (SELECT CandidateId FROM @c);",
                    "@p", NumberPrefix);
            }
        }
    }
}
