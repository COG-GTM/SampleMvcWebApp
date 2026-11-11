using System;

namespace AssessmentDelivery.Services.Admin
{
    /// <summary>A capacity-sync run audit row (AuditLog Action 'CAPACITY_SYNC', EntityId 0).</summary>
    public class CapacitySyncRunRow
    {
        public long AuditId { get; set; }
        public DateTime LoggedAt { get; set; }
        public string Actor { get; set; }
        public string Detail { get; set; }
        public string Trigger { get; set; }
        public string Status { get; set; }
        public int? SessionsChecked { get; set; }
        public int? SessionsUpdated { get; set; }
    }
}
