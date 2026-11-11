using System;
using System.Globalization;
using System.Text.RegularExpressions;

namespace AssessmentDelivery.Business.Rules
{
    /// <summary>
    /// Parses the AuditLog.Detail written by usp_ChangeCapacity ('&lt;old&gt; -&gt; &lt;new&gt;').
    /// Same format rule as db/procs/030_usp_GetCapacityAudit.sql - keep the two in step.
    /// </summary>
    public sealed class CapacityAuditDetail
    {
        private static readonly Regex Format = new Regex(@"^(-?[0-9]{1,10}) -> (-?[0-9]{1,10})\z", RegexOptions.CultureInvariant);

        private CapacityAuditDetail(int oldCapacity, int newCapacity)
        {
            OldCapacity = oldCapacity;
            NewCapacity = newCapacity;
        }

        public int OldCapacity { get; private set; }
        public int NewCapacity { get; private set; }
        public long Delta { get { return (long)NewCapacity - OldCapacity; } }

        /// <summary>Returns null when the detail is not exactly '&lt;int&gt; -&gt; &lt;int&gt;'.</summary>
        public static CapacityAuditDetail TryParse(string detail)
        {
            if (string.IsNullOrEmpty(detail)) return null;
            var m = Format.Match(detail);
            if (!m.Success) return null;
            int oldCap, newCap;
            if (!int.TryParse(m.Groups[1].Value, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out oldCap)) return null;
            if (!int.TryParse(m.Groups[2].Value, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out newCap)) return null;
            return new CapacityAuditDetail(oldCap, newCap);
        }
    }

    /// <summary>
    /// Parses the capacity-sync run row Detail
    /// ('trigger=&lt;t&gt; status=&lt;s&gt; checked=&lt;n&gt; updated=&lt;n&gt;', CapacitySyncService.audit).
    /// Missing or non-numeric fields come back null.
    /// </summary>
    public sealed class CapacitySyncDetail
    {
        private static readonly Regex Field = new Regex(@"(?:^|\s)(trigger|status|checked|updated)=(\S*)", RegexOptions.CultureInvariant);

        public string Trigger { get; private set; }
        public string Status { get; private set; }
        public int? Checked { get; private set; }
        public int? Updated { get; private set; }

        public static CapacitySyncDetail Parse(string detail)
        {
            var result = new CapacitySyncDetail();
            if (string.IsNullOrEmpty(detail)) return result;
            foreach (Match m in Field.Matches(detail))
            {
                var value = m.Groups[2].Value;
                switch (m.Groups[1].Value)
                {
                    case "trigger": result.Trigger = value; break;
                    case "status": result.Status = value; break;
                    case "checked": result.Checked = ToInt(value); break;
                    case "updated": result.Updated = ToInt(value); break;
                }
            }
            return result;
        }

        private static int? ToInt(string value)
        {
            int n;
            return int.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out n) ? n : (int?)null;
        }
    }

    /// <summary>
    /// Date range and paging for the capacity audit page: default last 30 days, 50 rows a page,
    /// 'to' is an inclusive calendar day (the query uses the exclusive bound ToExclusive).
    /// </summary>
    public sealed class CapacityAuditRange
    {
        public const int DefaultDays = 30;
        public const int PageSize = 50;

        public DateTime From { get; private set; }
        public DateTime To { get; private set; }
        public DateTime ToExclusive { get { return To.AddDays(1); } }
        public int Page { get; private set; }

        public static CapacityAuditRange Create(DateTime today, DateTime? from, DateTime? to, int? page)
        {
            var t = (to ?? today).Date;
            var f = (from ?? t.AddDays(-DefaultDays)).Date;
            if (f > t)
            {
                var swap = f; f = t; t = swap;
            }
            return new CapacityAuditRange { From = f, To = t, Page = page.HasValue && page.Value > 1 ? page.Value : 1 };
        }

        public static int PageCount(int totalRows)
        {
            return totalRows <= 0 ? 1 : (totalRows + PageSize - 1) / PageSize;
        }
    }
}
