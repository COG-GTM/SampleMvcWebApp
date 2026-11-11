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
        AdminSessionRow GetSessionRow(int sessionId);
        void ChangeCapacity(int sessionId, int newCapacity, string actor);

        /// <summary>Capacity changes for sessions at a centre via usp_GetCapacityAudit, newest first. Reads only.</summary>
        CapacityAuditResult GetCapacityAudit(int centerId, DateTime from, DateTime toExclusive, int? sessionId, int pageNumber, int pageSize);

        /// <summary>capacity-sync run rows logged in [from, toExclusive), newest first. Reads only.</summary>
        IList<CapacitySyncRunRow> ListCapacitySyncRuns(DateTime from, DateTime toExclusive, int max);

        /// <summary>Read-only roster for exam day; null when the session does not exist</summary>
        SessionRoster GetRoster(int sessionId);
    }
}
