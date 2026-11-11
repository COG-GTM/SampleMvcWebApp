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
    }
}
