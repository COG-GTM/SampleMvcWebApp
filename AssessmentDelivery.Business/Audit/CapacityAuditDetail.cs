using System.Globalization;
using System.Text.RegularExpressions;

namespace AssessmentDelivery.Business.Audit
{
    /// <summary>
    /// AuditLog.Detail of a capacity change (EntityName 'ExamSession', Action 'CAPACITY'). Two writers:
    /// the retired portal proc wrote "&lt;old&gt; -&gt; &lt;new&gt;"; the scheduling-service outbox relay writes
    /// "outbox event=N CapacityChanged session=X capacity=C seats=S change=&lt;old&gt; -&gt; &lt;new&gt; by=&lt;actor&gt;".
    /// Mirrors the parsing in db/procs/030_usp_GetCapacityAudit.sql.
    /// </summary>
    public sealed class CapacityAuditDetail
    {
        private static readonly Regex Legacy = new Regex(@"^\s*(-?\d+)\s*->\s*(-?\d+)\s*$", RegexOptions.CultureInvariant);
        private static readonly Regex Relay = new Regex(@"(?:^|\s)change=\s*(-?\d+)\s*->\s*(-?\d+)(?=\s|$)", RegexOptions.CultureInvariant);
        private static readonly Regex By = new Regex(@"(?:^|\s)by=(\S.*?)\s*$", RegexOptions.CultureInvariant);

        public int? OldCapacity { get; private set; }
        public int? NewCapacity { get; private set; }
        /// <summary>The user behind a relay row ("by=" token), null for legacy rows.</summary>
        public string Actor { get; private set; }

        public bool HasChange { get { return OldCapacity.HasValue && NewCapacity.HasValue; } }
        public int? Delta { get { return HasChange ? NewCapacity - OldCapacity : null; } }

        /// <summary>Never throws. Returns false (result still carries any "by=" actor) when no old/new pair is found.</summary>
        public static bool TryParse(string detail, out CapacityAuditDetail result)
        {
            result = new CapacityAuditDetail();
            if (string.IsNullOrWhiteSpace(detail)) return false;

            var by = By.Match(detail);
            if (by.Success) result.Actor = by.Groups[1].Value;

            var m = Legacy.Match(detail);
            if (!m.Success) m = Relay.Match(detail);
            if (!m.Success) return false;

            int oldCapacity, newCapacity;
            if (!int.TryParse(m.Groups[1].Value, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out oldCapacity)
                || !int.TryParse(m.Groups[2].Value, NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out newCapacity))
                return false;

            result.OldCapacity = oldCapacity;
            result.NewCapacity = newCapacity;
            return true;
        }
    }
}
