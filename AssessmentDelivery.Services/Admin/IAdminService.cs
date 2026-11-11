using System;
using System.Collections.Generic;
using AssessmentDelivery.Data.DataClasses.Entities;

namespace AssessmentDelivery.Services.Admin
{
    public interface IAdminService
    {
        IList<CenterListItem> ListCenters();
        TestCenter GetCenter(int centerId);
        void UpdateCenter(int centerId, string name, bool isActive);
        IList<AdminSessionRow> ListSessions(int centerId);

        /// <summary>Read-only: one page (CapacityAuditQuery.PageSize rows) of capacity changes at a centre, newest first.</summary>
        IList<CapacityAuditRow> GetCapacityAudit(int centerId, DateTime from, DateTime? to, int? sessionId, int page);

        /// <summary>Read-only: capacity-sync runs (network-wide, never tied to a session), newest first.</summary>
        IList<CapacitySyncRunRow> ListCapacitySyncRuns(DateTime from, DateTime? to);
    }
}
