using System;
using System.Linq;
using System.Text;
using AssessmentDelivery.Business.Roster;
using AssessmentDelivery.Tests.Helpers;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    [TestFixture]
    public class RosterCsvTests
    {
        private static RosterLine Line(string number, string last, string first, string status, int? position = null)
        {
            return new RosterLine
            {
                CandidateNumber = number,
                LastName = last,
                FirstName = first,
                Email = number.ToLowerInvariant() + "@example.test",
                BookingStatus = status,
                AccommodationCode = null,
                Channel = "PORTAL",
                BookedAt = new DateTime(2026, 10, 1, 9, 5, 7),
                WaitlistPosition = position
            };
        }

        [Test]
        public void HeaderHasTheAgreedColumnsInOrder()
        {
            var rows = CsvReader.Parse(RosterCsv.Build(Enumerable.Empty<RosterLine>()));

            Assert.AreEqual(1, rows.Count);
            CollectionAssert.AreEqual(new[]
            {
                "CandidateNumber", "LastName", "FirstName", "Email", "BookingStatus",
                "AccommodationCode", "Channel", "BookedAt", "WaitlistPosition"
            }, rows[0]);
        }

        [Test]
        public void WritesEveryFieldOfALine()
        {
            var line = new RosterLine
            {
                CandidateNumber = "NW00000042", LastName = "Ng", FirstName = "Ada", Email = "ada.ng@example.test",
                BookingStatus = "WAITLISTED", AccommodationCode = "ET25", Channel = "PARTNER",
                BookedAt = new DateTime(2026, 11, 3, 14, 30, 0), WaitlistPosition = 3
            };

            var rows = CsvReader.Parse(RosterCsv.Build(new[] { line }));

            CollectionAssert.AreEqual(new[]
            {
                "NW00000042", "Ng", "Ada", "ada.ng@example.test", "WAITLISTED", "ET25", "PARTNER", "2026-11-03 14:30:00", "3"
            }, rows[1]);
        }

        [Test]
        public void ExcludesCancelledAndCountsBookedAttendedAndWaitlisted()
        {
            var lines = new[]
            {
                Line("NW1", "Adams", "Al", "BOOKED"),
                Line("NW2", "Brown", "Bo", "ATTENDED"),
                Line("NW3", "Clark", "Cy", "CANCELLED"),
                Line("NW4", "Davis", "Di", "WAITLISTED", 1),
                Line("NW5", "Evans", "Ed", "WAITLISTED", 3)
            };

            var rows = CsvReader.Parse(RosterCsv.Build(lines));

            Assert.AreEqual(1 + 4, rows.Count);
            Assert.IsFalse(rows.Any(r => r[4] == "CANCELLED"));
            Assert.AreEqual("1", rows.Single(r => r[0] == "NW4")[8]);
            Assert.AreEqual("3", rows.Single(r => r[0] == "NW5")[8]);
            Assert.AreEqual("", rows.Single(r => r[0] == "NW1")[8]);
        }

        [Test]
        public void SeatHoldersFirstThenWaitlistEachSortedByLastNameThenFirstName()
        {
            var lines = new[]
            {
                Line("NW1", "Young", "Zed", "WAITLISTED", 1),
                Line("NW2", "smith", "Bea", "BOOKED"),
                Line("NW3", "Smith", "Ann", "ATTENDED"),
                Line("NW4", "Adams", "Al", "WAITLISTED", 2),
                Line("NW5", "Brown", "Bo", "BOOKED")
            };

            var order = CsvReader.Parse(RosterCsv.Build(lines)).Skip(1).Select(r => r[0]).ToArray();

            CollectionAssert.AreEqual(new[] { "NW5", "NW3", "NW2", "NW4", "NW1" }, order);
        }

        [TestCase("O'Neil, \"Jo\"")]
        [TestCase("O'Neil")]
        [TestCase("\"Jo\"")]
        [TestCase("Smith, Jr.")]
        [TestCase("line\r\nbreak")]
        [TestCase("Zoë Ångström")]
        public void ValuesRoundTripThroughAnRfc4180Parser(string value)
        {
            var line = Line("NW00000777", value, value, "BOOKED");
            line.Email = value;

            var rows = CsvReader.Parse(RosterCsv.Build(new[] { line }));

            Assert.AreEqual(2, rows.Count);
            Assert.AreEqual(value, rows[1][1]);
            Assert.AreEqual(value, rows[1][2]);
            Assert.AreEqual(value, rows[1][3]);
        }

        [Test]
        public void QuotesAndDoublesEmbeddedQuotes()
        {
            Assert.AreEqual("\"O'Neil, \"\"Jo\"\"\"", RosterCsv.Escape("O'Neil, \"Jo\""));
            Assert.AreEqual("O'Neil", RosterCsv.Escape("O'Neil"));
            Assert.AreEqual("", RosterCsv.Escape(null));
        }

        [TestCase("=1+1", "'=1+1")]
        [TestCase("+44", "'+44")]
        [TestCase("-2", "'-2")]
        [TestCase("@SUM(A1)", "'@SUM(A1)")]
        [TestCase("=HYPERLINK(\"x\",\"y\")", "\"'=HYPERLINK(\"\"x\"\",\"\"y\"\")\"")]
        [TestCase("Anne-Marie", "Anne-Marie")]
        public void NeutralisesSpreadsheetFormulas(string value, string expected)
        {
            Assert.AreEqual(expected, RosterCsv.Escape(value));
        }

        [Test]
        public void RecordsEndWithCrLf()
        {
            var csv = RosterCsv.Build(new[] { Line("NW1", "Adams", "Al", "BOOKED") });

            StringAssert.EndsWith("\r\n", csv);
            Assert.AreEqual(2, csv.Split(new[] { "\r\n" }, StringSplitOptions.RemoveEmptyEntries).Length);
        }

        [Test]
        public void BytesAreUtf8WithBom()
        {
            var bytes = RosterCsv.ToBytes("Zoë\r\n");

            CollectionAssert.AreEqual(new byte[] { 0xEF, 0xBB, 0xBF }, bytes.Take(3).ToArray());
            Assert.AreEqual("Zoë\r\n", Encoding.UTF8.GetString(bytes, 3, bytes.Length - 3));
        }

        [Test]
        public void FileNameFollowsCenterExamTimestampPattern()
        {
            Assert.AreEqual("roster-BOS01-AZ-900-20261110-0905.csv",
                RosterCsv.FileName("BOS01", "AZ-900", new DateTime(2026, 11, 10, 9, 5, 59)));
        }

        [Test]
        public void FileNameStripsCharactersThatAreUnsafeInHeaders()
        {
            Assert.AreEqual("roster-B_S_01-NA-20261110-0905.csv",
                RosterCsv.FileName("B\"S;01", null, new DateTime(2026, 11, 10, 9, 5, 0)));
        }
    }
}
