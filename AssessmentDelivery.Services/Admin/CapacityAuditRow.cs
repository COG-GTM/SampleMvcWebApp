using System;

namespace AssessmentDelivery.Services.Admin
{
    /// <summary>One row of dbo.usp_GetCapacityAudit (column names must match the proc).</summary>
    public class CapacityAuditRow
    {
        public long AuditId { get; set; }
        public DateTime LoggedAt { get; set; }
        public int SessionId { get; set; }
        public string ExamCode { get; set; }
        public DateTime StartsAt { get; set; }
        public string RoomName { get; set; }
        /// <summary>Null when Detail has no old/new pair; the view then shows Detail raw.</summary>
        public int? OldCapacity { get; set; }
        public int? NewCapacity { get; set; }
        public int? Delta { get; set; }
        /// <summary>The "by=" user for relay rows, else AuditLog.Actor.</summary>
        public string Actor { get; set; }
        public string RawActor { get; set; }
        public string Detail { get; set; }
        public int CurrentCapacity { get; set; }
        public int SeatsAvailable { get; set; }
        public int TotalRows { get; set; }
    }
}
