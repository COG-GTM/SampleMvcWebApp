using System;

namespace AssessmentDelivery.Services.Admin
{
    /// <summary>One row of dbo.usp_GetCapacitySyncRuns: a capacity-sync run (AuditLog EntityId 0, CAPACITY_SYNC).</summary>
    public class CapacitySyncRunRow
    {
        public long AuditId { get; set; }
        public DateTime LoggedAt { get; set; }
        public string Trigger { get; set; }
        public string Status { get; set; }
        public int? Checked { get; set; }
        public int? Updated { get; set; }
        public string Reason { get; set; }
        public string Detail { get; set; }
        public string Actor { get; set; }
    }
}
