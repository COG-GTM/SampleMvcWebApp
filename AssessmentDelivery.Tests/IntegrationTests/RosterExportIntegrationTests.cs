using System;
using System.Collections.Generic;
using System.Data.SqlClient;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using AssessmentDelivery.Business.Roster;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Services.Admin;
using AssessmentDelivery.Services.Scheduling;
using AssessmentDelivery.Tests.Helpers;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.IntegrationTests
{
    /// <summary>
    /// MBA-3826 roster export against the real AssessmentDelivery database (scripts/run-legacy.ps1).
    /// Ignored when the database is not reachable.
    /// </summary>
    [TestFixture, Category("Integration")]
    public class RosterExportIntegrationTests
    {
        private RosterFixture _fixture;

        [OneTimeSetUp]
        public void CreateFixture()
        {
            string reason;
            if (!RosterFixture.DatabaseAvailable(out reason)) Assert.Ignore(reason);
            _fixture = new RosterFixture();
            _fixture.Create();
        }

        [OneTimeTearDown]
        public void RemoveFixture()
        {
            if (_fixture != null) RosterFixture.Remove();
        }

        internal static AdminService NewService(AssessmentDeliveryDb db)
        {
            return new AdminService(db, new SchedulingClient());
        }

        internal static AssessmentDeliveryDb NewDb()
        {
            return new AssessmentDeliveryDb(RosterFixture.ConnectionString);
        }

        /// <summary>Signature of everything the export could plausibly write to.</summary>
        internal static string WriteSignature(int sessionId)
        {
            using (var cn = new SqlConnection(RosterFixture.ConnectionString))
            using (var cmd = new SqlCommand(@"
SELECT CONCAT(
  (SELECT COUNT_BIG(*) FROM dbo.AuditLog), '|', (SELECT MAX(AuditId) FROM dbo.AuditLog), '|',
  (SELECT CONCAT(SeatsAvailable, '/', Capacity, '/', Status, '/', CONVERT(VARCHAR(23), LastModified, 126)) FROM dbo.ExamSession WHERE SessionId = @s), '|',
  (SELECT CHECKSUM_AGG(CHECKSUM(BookingId, Status, Channel, AccommodationCode, LastModified)) FROM dbo.Booking WHERE SessionId = @s), '|',
  (SELECT CHECKSUM_AGG(CHECKSUM(WaitlistId, BookingId, Position)) FROM dbo.Waitlist WHERE SessionId = @s), '|',
  (SELECT COUNT_BIG(*) FROM dbo.Booking), '|', (SELECT COUNT_BIG(*) FROM dbo.Waitlist), '|',
  (SELECT COUNT_BIG(*) FROM dbo.Candidate), '|', (SELECT COUNT_BIG(*) FROM dbo.Result))", cn))
            {
                cmd.Parameters.AddWithValue("@s", sessionId);
                cn.Open();
                return (string)cmd.ExecuteScalar();
            }
        }

        [Test]
        public void RosterExcludesCancelledAndCarriesWaitlistPositions()
        {
            using (var db = NewDb())
            {
                var roster = NewService(db).GetRoster(_fixture.SessionId);

                Assert.IsNotNull(roster);
                Assert.AreEqual(_fixture.CenterCode, roster.CenterCode);
                Assert.AreEqual(_fixture.ExamCode, roster.ExamCode);

                var expected = RosterFixture.Bookings.Count(b => (string)b[3] != "CANCELLED");
                Assert.AreEqual(expected, roster.Lines.Count, "BOOKED + ATTENDED + WAITLISTED");
                Assert.IsFalse(roster.Lines.Any(l => l.BookingStatus == "CANCELLED"));

                var byNumber = roster.Lines.ToDictionary(l => l.CandidateNumber);
                Assert.AreEqual(1, byNumber[RosterFixture.NumberPrefix + "0005"].WaitlistPosition);
                Assert.AreEqual(3, byNumber[RosterFixture.NumberPrefix + "0006"].WaitlistPosition, "gaps in Position are kept (HB-06)");
                Assert.IsNull(byNumber[RosterFixture.NumberPrefix + "0007"].WaitlistPosition, "WAITLISTED without a Waitlist row (HB-28)");
                Assert.IsNull(byNumber[RosterFixture.NumberPrefix + "0001"].WaitlistPosition);
                Assert.AreEqual("ET25", byNumber[RosterFixture.NumberPrefix + "0002"].AccommodationCode);
                Assert.AreEqual("IMPORT", byNumber[RosterFixture.NumberPrefix + "0002"].Channel);
                Assert.AreEqual(new DateTime(2026, 11, 1, 8, 4, 0), byNumber[RosterFixture.NumberPrefix + "0005"].BookedAt);
            }
        }

        [Test]
        public void RosterIsSortedSeatHoldersThenWaitlistByName()
        {
            using (var db = NewDb())
            {
                var names = NewService(db).GetRoster(_fixture.SessionId).Lines.Select(l => l.LastName).ToArray();

                CollectionAssert.AreEqual(new[] { "Abbott", "O'Neil", "Smith, Jr.", "Baker", "Orphan", "Young" }, names);
            }
        }

        [Test]
        public void ExportedCsvParsesBackToTheStoredValues()
        {
            using (var db = NewDb())
            {
                var roster = NewService(db).GetRoster(_fixture.SessionId);
                var rows = CsvReader.Parse(RosterCsv.Build(roster.Lines));

                var oneil = rows.Single(r => r[0] == RosterFixture.NumberPrefix + "0001");
                Assert.AreEqual("O'Neil", oneil[1]);
                Assert.AreEqual("\"Jo\"", oneil[2]);
                Assert.AreEqual("Smith, Jr.", rows.Single(r => r[0] == RosterFixture.NumberPrefix + "0003")[1]);
                Assert.AreEqual(1 + 6, rows.Count);
            }
        }

        [Test]
        public void UnknownSessionGivesNoRoster()
        {
            using (var db = NewDb())
            {
                Assert.IsNull(NewService(db).GetRoster(int.MaxValue));
            }
        }

        [Test]
        public void ExportIssuesOnlySelectsAndWritesNothing()
        {
            var before = WriteSignature(_fixture.SessionId);
            var sql = new StringBuilder();
            using (var db = NewDb())
            {
                db.Database.Log = s => sql.Append(s);
                var roster = NewService(db).GetRoster(_fixture.SessionId);
                RosterCsv.ToBytes(RosterCsv.Build(roster.Lines));
                Assert.AreEqual(0, db.ChangeTracker.Entries().Count(), "no tracked entities");
            }
            var after = WriteSignature(_fixture.SessionId);

            Assert.AreEqual(before, after, "AuditLog / ExamSession / Booking / Waitlist unchanged");
            Assert.IsFalse(Regex.IsMatch(sql.ToString(), @"\b(INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE)\b", RegexOptions.IgnoreCase),
                "export SQL must be read-only:\n" + sql);
        }

        [Test]
        public void RowCountMatchesTheDatabaseForEverySeededSession()
        {
            var expected = new Dictionary<int, int[]>();
            using (var cn = new SqlConnection(RosterFixture.ConnectionString))
            using (var cmd = new SqlCommand(@"
SELECT s.SessionId,
       SUM(CASE WHEN b.Status IN ('BOOKED','ATTENDED') THEN 1 ELSE 0 END),
       SUM(CASE WHEN b.Status = 'WAITLISTED' THEN 1 ELSE 0 END)
FROM dbo.ExamSession s LEFT JOIN dbo.Booking b ON b.SessionId = s.SessionId
WHERE s.SessionId <= 462
GROUP BY s.SessionId", cn))
            {
                cn.Open();
                using (var r = cmd.ExecuteReader())
                    while (r.Read()) expected[r.GetInt32(0)] = new[] { r.GetInt32(1), r.GetInt32(2) };
            }
            Assert.That(expected.Count, Is.GreaterThan(0));

            var waitlisted = 0;
            using (var db = NewDb())
            {
                var service = NewService(db);
                foreach (var e in expected)
                {
                    var lines = service.GetRoster(e.Key).Lines;
                    Assert.AreEqual(e.Value[0], lines.Count(l => l.BookingStatus != "WAITLISTED"), "seat holders, session " + e.Key);
                    Assert.AreEqual(e.Value[1], lines.Count(l => l.BookingStatus == "WAITLISTED"), "waitlisted, session " + e.Key);
                    waitlisted += e.Value[1];
                }
            }
            Assert.That(waitlisted, Is.GreaterThan(0), "seed has waitlisted bookings");
        }
    }
}
