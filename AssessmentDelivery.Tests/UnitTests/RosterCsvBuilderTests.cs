using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using AssessmentDelivery.Business.Rosters;
using Microsoft.VisualBasic.FileIO;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    [TestFixture]
    public class RosterCsvBuilderTests
    {
        private static readonly DateTime At = new DateTime(2027, 2, 3, 8, 5, 0);

        private static RosterLine Line(string number, string last, string first, string status, int? position = null)
        {
            return new RosterLine
            {
                CandidateNumber = number,
                LastName = last,
                FirstName = first,
                Email = number.ToLowerInvariant() + "@example.com",
                BookingStatus = status,
                AccommodationCode = null,
                Channel = "PORTAL",
                BookedAt = At,
                WaitlistPosition = position
            };
        }

        private static List<RosterLine> MixedSession()
        {
            return new List<RosterLine>
            {
                Line("NW00000005", "Young", "Yan", "WAITLISTED", 2),
                Line("NW00000001", "Zimmer", "Zoe", "BOOKED"),
                Line("NW00000004", "Baker", "Ben", "CANCELLED"),
                Line("NW00000002", "Abbott", "Ann", "ATTENDED"),
                Line("NW00000006", "Carter", "Cy", "WAITLISTED", 1),
                Line("NW00000003", "Moss", "Max", "BOOKED"),
                Line("NW00000007", "Baker", "Bea", "CANCELLED")
            };
        }

        /// <summary>Independent RFC 4180 parse (VB TextFieldParser), BOM stripped by the reader</summary>
        private static List<string[]> Parse(byte[] csv)
        {
            var rows = new List<string[]>();
            using (var reader = new StreamReader(new MemoryStream(csv), Encoding.UTF8, true))
            using (var parser = new TextFieldParser(reader))
            {
                parser.SetDelimiters(",");
                parser.HasFieldsEnclosedInQuotes = true;
                parser.TrimWhiteSpace = false;
                while (!parser.EndOfData)
                    rows.Add(parser.ReadFields());
            }
            return rows;
        }

        [Test]
        public void HeaderHasTheAgreedColumnsInOrder()
        {
            var text = RosterCsvBuilder.BuildText(new RosterLine[0]);

            Assert.AreEqual("CandidateNumber,LastName,FirstName,Email,BookingStatus,AccommodationCode,Channel,BookedAt,WaitlistPosition\r\n", text);
        }

        [Test]
        public void FileStartsWithUtf8ByteOrderMark()
        {
            var bytes = RosterCsvBuilder.Build(MixedSession());

            CollectionAssert.AreEqual(new byte[] { 0xEF, 0xBB, 0xBF }, bytes.Take(3).ToArray());
            StringAssert.StartsWith(RosterCsvBuilder.Header, Encoding.UTF8.GetString(bytes, 3, bytes.Length - 3));
        }

        [Test]
        public void RowsAreWrittenWithCrLf()
        {
            var text = RosterCsvBuilder.BuildText(MixedSession());

            Assert.AreEqual(7, text.Split(new[] { "\r\n" }, StringSplitOptions.None).Length, "header + 5 rows + trailing empty");
            Assert.IsFalse(text.Replace("\r\n", "").Contains("\n"));
        }

        [Test]
        public void CancelledExcludedAndRowCountIsBookedAttendedWaitlisted()
        {
            var rows = Parse(RosterCsvBuilder.Build(MixedSession()));
            var data = rows.Skip(1).ToList();

            Assert.AreEqual(5, data.Count);
            Assert.IsFalse(data.Any(r => r[4] == "CANCELLED"));
            Assert.AreEqual(2, data.Count(r => r[4] == "BOOKED"));
            Assert.AreEqual(1, data.Count(r => r[4] == "ATTENDED"));
            Assert.AreEqual(2, data.Count(r => r[4] == "WAITLISTED"));
        }

        [Test]
        public void WaitlistedRowsShowPositionOthersAreEmpty()
        {
            var lines = MixedSession();
            lines.Add(new RosterLine { CandidateNumber = "NW00000008", LastName = "Hale", FirstName = "Hal", BookingStatus = "BOOKED", WaitlistPosition = 9 });

            var data = Parse(RosterCsvBuilder.Build(lines)).Skip(1).ToList();

            Assert.AreEqual("1", data.Single(r => r[1] == "Carter")[8]);
            Assert.AreEqual("2", data.Single(r => r[1] == "Young")[8]);
            Assert.IsTrue(data.Where(r => r[4] != "WAITLISTED").All(r => r[8] == ""), "position only on WAITLISTED rows");
        }

        [Test]
        public void BookedGroupThenWaitlistGroupEachSortedByLastThenFirstName()
        {
            var lines = MixedSession();
            lines.Add(Line("NW00000010", "Moss", "Amy", "BOOKED"));
            lines.Add(Line("NW00000011", "abbott", "Zed", "WAITLISTED", 3));

            var data = Parse(RosterCsvBuilder.Build(lines)).Skip(1).ToList();

            CollectionAssert.AreEqual(
                new[] { "NW00000002", "NW00000010", "NW00000003", "NW00000001", "NW00000011", "NW00000006", "NW00000005" },
                data.Select(r => r[0]).ToArray());
        }

        [Test]
        public void SameNameIsOrderedByCandidateNumber()
        {
            var lines = new[] { Line("NW00000020", "Lee", "Kim", "BOOKED"), Line("NW00000019", "Lee", "Kim", "BOOKED") };

            var data = Parse(RosterCsvBuilder.Build(lines)).Skip(1).ToList();

            CollectionAssert.AreEqual(new[] { "NW00000019", "NW00000020" }, data.Select(r => r[0]).ToArray());
        }

        [TestCase("plain", "plain")]
        [TestCase("a,b", "\"a,b\"")]
        [TestCase("say \"hi\"", "\"say \"\"hi\"\"\"")]
        [TestCase("line1\nline2", "\"line1\nline2\"")]
        [TestCase("line1\r\nline2", "\"line1\r\nline2\"")]
        [TestCase("O'Neil", "O'Neil")]
        [TestCase("", "")]
        [TestCase(null, "")]
        public void EscapesPerRfc4180(string value, string expected)
        {
            Assert.AreEqual(expected, RosterCsvBuilder.Escape(value));
        }

        [TestCase("=SUM(A1:A2)", "'=SUM(A1:A2)")]
        [TestCase("+44 1234", "'+44 1234")]
        [TestCase("-x", "'-x")]
        [TestCase("@cmd", "'@cmd")]
        [TestCase("=1,2", "\"'=1,2\"")]
        [TestCase("Smith-Jones", "Smith-Jones")]
        public void FormulaLikeValuesAreNeutralised(string value, string expected)
        {
            Assert.AreEqual(expected, RosterCsvBuilder.Escape(value));
        }

        [Test]
        public void ONeilJoAsSeparateNameFieldsRoundTrips()
        {
            var line = Line("NW00000042", "O'Neil", "\"Jo\"", "BOOKED");

            var text = RosterCsvBuilder.BuildText(new[] { line });
            var row = Parse(RosterCsvBuilder.Build(new[] { line }))[1];

            StringAssert.Contains(",O'Neil,\"\"\"Jo\"\"\",", text);
            Assert.AreEqual("O'Neil", row[1]);
            Assert.AreEqual("\"Jo\"", row[2]);
            Assert.AreEqual(9, row.Length);
        }

        [Test]
        public void ONeilJoAsSingleFieldRoundTrips()
        {
            var line = Line("NW00000043", "O'Neil, \"Jo\"", "Jo", "BOOKED");
            line.Email = "o'neil, \"jo\"@example.com";

            var text = RosterCsvBuilder.BuildText(new[] { line });
            var row = Parse(RosterCsvBuilder.Build(new[] { line }))[1];

            StringAssert.Contains(",\"O'Neil, \"\"Jo\"\"\",", text);
            Assert.AreEqual("O'Neil, \"Jo\"", row[1]);
            Assert.AreEqual("o'neil, \"jo\"@example.com", row[3]);
            Assert.AreEqual(9, row.Length);
        }

        [Test]
        public void EmbeddedNewlineStaysInOneRecord()
        {
            var line = Line("NW00000044", "Multi\r\nLine", "Ann", "BOOKED");

            var rows = Parse(RosterCsvBuilder.Build(new[] { line }));

            Assert.AreEqual(2, rows.Count);
            Assert.AreEqual("Multi\r\nLine", rows[1][1]);
        }

        [Test]
        public void NullFieldsAreWrittenEmpty()
        {
            var line = new RosterLine { CandidateNumber = "NW00000050", BookingStatus = "BOOKED" };

            var text = RosterCsvBuilder.BuildText(new[] { line });

            Assert.AreEqual(RosterCsvBuilder.Header + "\r\nNW00000050,,,,BOOKED,,,,\r\n", text);
        }

        [Test]
        public void AllColumnsAreWrittenInHeaderOrder()
        {
            var line = new RosterLine
            {
                CandidateNumber = "NW00000060", LastName = "Ng", FirstName = "Ana", Email = "ana.ng@example.com",
                BookingStatus = "WAITLISTED", AccommodationCode = "ET25", Channel = "PARTNER",
                BookedAt = new DateTime(2027, 1, 9, 14, 30, 59), WaitlistPosition = 4
            };

            var row = Parse(RosterCsvBuilder.Build(new[] { line }))[1];

            CollectionAssert.AreEqual(
                new[] { "NW00000060", "Ng", "Ana", "ana.ng@example.com", "WAITLISTED", "ET25", "PARTNER", "2027-01-09 14:30", "4" }, row);
        }

        [Test]
        public void NullAndEmptyInputGiveHeaderOnly()
        {
            Assert.AreEqual(RosterCsvBuilder.Header + "\r\n", RosterCsvBuilder.BuildText(null));
            Assert.AreEqual(RosterCsvBuilder.Header + "\r\n", RosterCsvBuilder.BuildText(new RosterLine[] { null }));
        }

        [Test]
        public void FileNameIsCenterExamAndTimestamp()
        {
            Assert.AreEqual("roster-BOS01-HPI-ACC1-20270203-0805.csv", RosterCsvBuilder.BuildFileName("BOS01", "HPI-ACC1", At));
        }

        [Test]
        public void FileNameReplacesCharactersNotAllowedInFileNames()
        {
            Assert.AreEqual("roster-A_B-X_Y-20270203-0805.csv", RosterCsvBuilder.BuildFileName("A/B", "X:Y", At));
            Assert.AreEqual("roster-unknown-unknown-20270203-0805.csv", RosterCsvBuilder.BuildFileName(null, " ", At));
        }
    }
}
