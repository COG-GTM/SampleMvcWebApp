using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Text;
using AssessmentDelivery.Business.Rules;

namespace AssessmentDelivery.Business.Roster
{
    /// <summary>
    /// Builds the exam-day roster CSV (RFC 4180, CRLF line ends, UTF-8 with BOM so Excel opens it).
    /// Seat holders (BOOKED, ATTENDED) come first, then the WAITLISTED section; each section is
    /// sorted by LastName, FirstName. CANCELLED bookings are never written.
    /// </summary>
    public static class RosterCsv
    {
        public const string ContentType = "text/csv";

        public static readonly string[] Columns =
        {
            "CandidateNumber", "LastName", "FirstName", "Email", "BookingStatus",
            "AccommodationCode", "Channel", "BookedAt", "WaitlistPosition"
        };

        public const string BookedAtFormat = "yyyy-MM-dd HH:mm:ss";

        private static readonly char[] CharsNeedingQuotes = { ',', '"', '\r', '\n' };
        private static readonly char[] FormulaTriggers = { '=', '+', '-', '@', '\t', '\r' };

        public static bool IsOnRoster(string bookingStatus)
        {
            return bookingStatus == BookingStatus.Booked || bookingStatus == BookingStatus.Attended || bookingStatus == BookingStatus.Waitlisted;
        }

        public static IList<RosterLine> Order(IEnumerable<RosterLine> lines)
        {
            return (lines ?? Enumerable.Empty<RosterLine>())
                .Where(l => l != null && IsOnRoster(l.BookingStatus))
                .OrderBy(l => l.BookingStatus == BookingStatus.Waitlisted ? 1 : 0)
                .ThenBy(l => l.LastName ?? "", StringComparer.OrdinalIgnoreCase)
                .ThenBy(l => l.FirstName ?? "", StringComparer.OrdinalIgnoreCase)
                .ThenBy(l => l.CandidateNumber ?? "", StringComparer.Ordinal)
                .ToList();
        }

        public static string Build(IEnumerable<RosterLine> lines)
        {
            var sb = new StringBuilder();
            AppendRecord(sb, Columns);
            foreach (var l in Order(lines))
            {
                AppendRecord(sb, new[]
                {
                    l.CandidateNumber,
                    l.LastName,
                    l.FirstName,
                    l.Email,
                    l.BookingStatus,
                    l.AccommodationCode,
                    l.Channel,
                    l.BookedAt.ToString(BookedAtFormat, CultureInfo.InvariantCulture),
                    l.WaitlistPosition.HasValue ? l.WaitlistPosition.Value.ToString(CultureInfo.InvariantCulture) : ""
                });
            }
            return sb.ToString();
        }

        /// <summary>UTF-8 bytes with a leading BOM</summary>
        public static byte[] ToBytes(string csv)
        {
            var encoding = new UTF8Encoding(true);
            var preamble = encoding.GetPreamble();
            var body = encoding.GetBytes(csv ?? "");
            var bytes = new byte[preamble.Length + body.Length];
            Buffer.BlockCopy(preamble, 0, bytes, 0, preamble.Length);
            Buffer.BlockCopy(body, 0, bytes, preamble.Length, body.Length);
            return bytes;
        }

        /// <summary>roster-{CenterCode}-{ExamCode}-{yyyyMMdd-HHmm}.csv</summary>
        public static string FileName(string centerCode, string examCode, DateTime generatedAt)
        {
            return "roster-" + SafeFilePart(centerCode) + "-" + SafeFilePart(examCode) + "-"
                   + generatedAt.ToString("yyyyMMdd-HHmm", CultureInfo.InvariantCulture) + ".csv";
        }

        /// <summary>
        /// RFC 4180 field: quoted when it holds a comma, quote or line break, with quotes doubled.
        /// A leading =, +, -, @, tab or CR is prefixed with an apostrophe so spreadsheet apps do not
        /// run the cell as a formula (OWASP CSV injection guidance).
        /// </summary>
        public static string Escape(string value)
        {
            if (string.IsNullOrEmpty(value)) return "";
            if (Array.IndexOf(FormulaTriggers, value[0]) >= 0) value = "'" + value;
            if (value.IndexOfAny(CharsNeedingQuotes) < 0) return value;
            return "\"" + value.Replace("\"", "\"\"") + "\"";
        }

        private static void AppendRecord(StringBuilder sb, IEnumerable<string> fields)
        {
            sb.Append(string.Join(",", fields.Select(Escape)));
            sb.Append("\r\n");
        }

        private static string SafeFilePart(string value)
        {
            if (string.IsNullOrWhiteSpace(value)) return "NA";
            var chars = value.Trim().Select(c => c < 128 && (char.IsLetterOrDigit(c) || c == '-' || c == '_') ? c : '_').ToArray();
            return new string(chars);
        }
    }
}
