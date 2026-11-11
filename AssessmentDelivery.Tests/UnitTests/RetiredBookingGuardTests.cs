using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    /// <summary>
    /// MBA-3824: the booking/capacity slice is served by scheduling-service only. Same patterns as
    /// scripts/check-retired-booking.sh (CI); fails if C# booking writes or the retired procs come back.
    /// </summary>
    [TestFixture]
    public class RetiredBookingGuardTests
    {
        private static string RepoRoot()
        {
            var dir = new DirectoryInfo(TestContext.CurrentContext.TestDirectory);
            while (dir != null && !File.Exists(Path.Combine(dir.FullName, "AssessmentDelivery.sln")))
                dir = dir.Parent;
            if (dir == null) Assert.Inconclusive("AssessmentDelivery.sln not found above the test directory");
            return dir.FullName;
        }

        private static IList<Regex> Patterns()
        {
            return File.ReadAllLines(Path.Combine(RepoRoot(), "scripts", "retired-booking-patterns.txt"))
                .Where(l => l.Trim().Length > 0)
                .Select(l => new Regex(l, RegexOptions.IgnoreCase))
                .ToList();
        }

        private static bool Flags(string line)
        {
            return Patterns().Any(p => p.IsMatch(line));
        }

        [TestCase("EXEC dbo.usp_BookSeat @SessionId, @CandidateId")]
        [TestCase("\"EXEC dbo.usp_ChangeCapacity @SessionId, @NewCapacity, @Actor\"")]
        [TestCase("_db.Database.ExecuteSqlCommand(sql)")]
        [TestCase("session.SeatsAvailable = session.SeatsAvailable - 1;")]
        [TestCase("session.SeatsAvailable--;")]
        [TestCase("s.SeatsAvailable += 1;")]
        [TestCase("UPDATE dbo.ExamSession SET SeatsAvailable = 0")]
        [TestCase("INSERT INTO dbo.Booking (SessionId) VALUES (1)")]
        [TestCase("_db.Bookings.Add(booking);")]
        [TestCase("var b = new Booking { SessionId = 1 };")]
        [TestCase("public class BookingController : Controller")]
        [TestCase("Booked = SeatCounter.CountBooked(r.Statuses)")]
        public void FlagsBookingWritesAndRetiredCode(string line)
        {
            Assert.IsTrue(Flags(line), line);
        }

        [TestCase("SeatsAvailable = r.SeatsAvailable,")]
        [TestCase("s.SeatsAvailable,")]
        [TestCase("Booked = s.Bookings.Count(b => seatHolding.Contains(b.Status)),")]
        [TestCase("public DbSet<Booking> Bookings { get; set; }")]
        [TestCase("<td class=\"num\">@s.SeatsAvailable</td>")]
        [TestCase("if (s.SeatsAvailable == 0)")]
        public void AllowsReadOnlyUse(string line)
        {
            Assert.IsFalse(Flags(line), line);
        }

        [Test]
        public void PortalSourceHasNoBookingWritesOrRetiredProcs()
        {
            var root = RepoRoot();
            var patterns = Patterns();
            var hits = new List<string>();
            foreach (var project in Directory.GetDirectories(root, "AssessmentDelivery.*"))
            {
                if (Path.GetFileName(project) == "AssessmentDelivery.Tests") continue;
                var files = Directory.GetFiles(project, "*.cs", SearchOption.AllDirectories)
                    .Concat(Directory.GetFiles(project, "*.cshtml", SearchOption.AllDirectories))
                    .Where(f => !f.Contains(Path.DirectorySeparatorChar + "obj" + Path.DirectorySeparatorChar)
                             && !f.Contains(Path.DirectorySeparatorChar + "bin" + Path.DirectorySeparatorChar));
                foreach (var file in files)
                {
                    var lines = File.ReadAllLines(file);
                    for (var i = 0; i < lines.Length; i++)
                        if (patterns.Any(p => p.IsMatch(lines[i])))
                            hits.Add(file.Substring(root.Length + 1) + ":" + (i + 1) + ": " + lines[i].Trim());
                }
            }
            Assert.IsEmpty(hits, "Retired booking/capacity code found:" + Environment.NewLine + string.Join(Environment.NewLine, hits));
        }
    }
}
