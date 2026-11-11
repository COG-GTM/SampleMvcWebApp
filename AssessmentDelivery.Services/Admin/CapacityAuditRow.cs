using System;
using System.Collections.Generic;

namespace AssessmentDelivery.Services.Admin
{
    /// <summary>One row of dbo.usp_GetCapacityAudit (column names must match the proc).</summary>
    public class CapacityAuditRow
    {
        public long AuditId { get; set; }
        public DateTime LoggedAt { get; set; }
        public string Actor { get; set; }
        public string Detail { get; set; }
        public int SessionId { get; set; }
        public string ExamCode { get; set; }
        public DateTime SessionStartsAt { get; set; }
        public string RoomName { get; set; }
        /// <summary>Null when Detail is not '&lt;int&gt; -&gt; &lt;int&gt;' (show Detail instead).</summary>
        public int? OldCapacity { get; set; }
        public int? NewCapacity { get; set; }
        public long? Delta { get; set; }
        public int CurrentCapacity { get; set; }
        public int CurrentSeatsAvailable { get; set; }
        public int TotalRows { get; set; }
    }

    public class CapacityAuditResult
    {
        public IList<CapacityAuditRow> Rows { get; set; }
        /// <summary>Matching rows across all pages.</summary>
        public int TotalRows { get; set; }
    }
}
