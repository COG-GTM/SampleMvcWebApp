using System.Globalization;
using System.Text.RegularExpressions;

namespace AssessmentDelivery.Business.Audit
{
    /// <summary>
    /// AuditLog.Detail of a capacity-sync run (EntityId 0, Action 'CAPACITY_SYNC', CapacitySyncService.audit):
    /// "trigger=X status=Y checked=N updated=M [reason=...]". The reason is free text and always last.
    /// </summary>
    public sealed class CapacitySyncDetail
    {
        private static readonly Regex Pattern = new Regex(
            @"^\s*trigger=(?<trigger>\S+)\s+status=(?<status>\S+)\s+checked=(?<checked>\d+)\s+updated=(?<updated>\d+)(?:\s+reason=(?<reason>.*))?\s*$",
            RegexOptions.CultureInvariant | RegexOptions.Singleline);

        public string Trigger { get; private set; }
        public string Status { get; private set; }
        public int Checked { get; private set; }
        public int Updated { get; private set; }
        public string Reason { get; private set; }

        public static bool TryParse(string detail, out CapacitySyncDetail result)
        {
            result = null;
            if (string.IsNullOrWhiteSpace(detail)) return false;
            var m = Pattern.Match(detail);
            int sessionsChecked, sessionsUpdated;
            if (!m.Success
                || !int.TryParse(m.Groups["checked"].Value, NumberStyles.None, CultureInfo.InvariantCulture, out sessionsChecked)
                || !int.TryParse(m.Groups["updated"].Value, NumberStyles.None, CultureInfo.InvariantCulture, out sessionsUpdated))
                return false;
            result = new CapacitySyncDetail
            {
                Trigger = m.Groups["trigger"].Value,
                Status = m.Groups["status"].Value,
                Checked = sessionsChecked,
                Updated = sessionsUpdated,
                Reason = m.Groups["reason"].Success ? m.Groups["reason"].Value.Trim() : null
            };
            return true;
        }
    }
}
