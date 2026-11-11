using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;
using AssessmentDelivery.Business.Rules;

namespace AssessmentDelivery.Business.Rosters
{
    /// <summary>
    /// Builds the proctor check-in roster for an exam session as CSV (RFC 4180, UTF-8 with BOM, CRLF).
    /// BOOKED and ATTENDED rows come first, then the WAITLISTED rows; each group is sorted by
    /// LastName, FirstName. CANCELLED rows are left out.
    /// </summary>
    public static class RosterCsvBuilder
    {
        public const string Header = "CandidateNumber,LastName,FirstName,Email,BookingStatus,AccommodationCode,Channel,BookedAt,WaitlistPosition";
        public const string BookedAtFormat = "yyyy-MM-dd HH:mm";
        private const string NewLine = "\r\n";

        private static readonly StringComparer NameComparer = StringComparer.InvariantCultureIgnoreCase;
        private static readonly char[] CharsNeedingQuotes = { ',', '"', '\r', '\n' };
        private static readonly char[] FormulaPrefixes = { '=', '+', '-', '@', '\t', '\r' };

        /// <summary>
        /// Rows in roster order: BOOKED/ATTENDED by name, then WAITLISTED by name. CANCELLED dropped.
        /// </summary>
        public static IList<RosterLine> Arrange(IEnumerable<RosterLine> lines)
        {
            var list = (lines ?? Enumerable.Empty<RosterLine>()).Where(l => l != null).ToList();
            var booked = list.Where(l => l.BookingStatus == BookingStatus.Booked || l.BookingStatus == BookingStatus.Attended);
            var waitlisted = list.Where(l => l.BookingStatus == BookingStatus.Waitlisted);
            return SortByName(booked).Concat(SortByName(waitlisted)).ToList();
        }

        public static string BuildText(IEnumerable<RosterLine> lines)
        {
            var sb = new StringBuilder();
            sb.Append(Header).Append(NewLine);
            foreach (var l in Arrange(lines))
            {
                var waitlisted = l.BookingStatus == BookingStatus.Waitlisted;
                sb.Append(string.Join(",", new[]
                {
                    Escape(l.CandidateNumber),
                    Escape(l.LastName),
                    Escape(l.FirstName),
                    Escape(l.Email),
                    Escape(l.BookingStatus),
                    Escape(l.AccommodationCode),
                    Escape(l.Channel),
                    l.BookedAt.HasValue ? l.BookedAt.Value.ToString(BookedAtFormat, CultureInfo.InvariantCulture) : "",
                    waitlisted && l.WaitlistPosition.HasValue ? l.WaitlistPosition.Value.ToString(CultureInfo.InvariantCulture) : ""
                })).Append(NewLine);
            }
            return sb.ToString();
        }

        /// <summary>
        /// The CSV file content: UTF-8 with a byte order mark so Excel picks the right encoding
        /// </summary>
        public static byte[] Build(IEnumerable<RosterLine> lines)
        {
            var encoding = new UTF8Encoding(true);
            return encoding.GetPreamble().Concat(encoding.GetBytes(BuildText(lines))).ToArray();
        }

        /// <summary>
        /// RFC 4180 field escaping: a field holding a comma, quote, CR or LF is quoted and its quotes doubled.
        /// A field starting with = + - @ (or tab/CR) gets a leading apostrophe so spreadsheets do not
        /// evaluate it as a formula. Null is an empty field.
        /// </summary>
        public static string Escape(string value)
        {
            if (string.IsNullOrEmpty(value)) return "";
            if (Array.IndexOf(FormulaPrefixes, value[0]) >= 0)
                value = "'" + value;
            if (value.IndexOfAny(CharsNeedingQuotes) < 0)
                return value;
            return "\"" + value.Replace("\"", "\"\"") + "\"";
        }

        /// <summary>
        /// roster-{CenterCode}-{ExamCode}-{yyyyMMdd-HHmm}.csv
        /// </summary>
        public static string BuildFileName(string centerCode, string examCode, DateTime generatedAt)
        {
            return string.Format(CultureInfo.InvariantCulture, "roster-{0}-{1}-{2}.csv",
                SafeFilePart(centerCode), SafeFilePart(examCode),
                generatedAt.ToString("yyyyMMdd-HHmm", CultureInfo.InvariantCulture));
        }

        private static IEnumerable<RosterLine> SortByName(IEnumerable<RosterLine> lines)
        {
            return lines.OrderBy(l => l.LastName ?? "", NameComparer)
                .ThenBy(l => l.FirstName ?? "", NameComparer)
                .ThenBy(l => l.CandidateNumber ?? "", StringComparer.Ordinal);
        }

        private static string SafeFilePart(string value)
        {
            if (string.IsNullOrWhiteSpace(value)) return "unknown";
            var invalid = Path.GetInvalidFileNameChars();
            return new string(value.Trim().Select(c => Array.IndexOf(invalid, c) >= 0 || c == ' ' ? '_' : c).ToArray());
        }
    }
}
