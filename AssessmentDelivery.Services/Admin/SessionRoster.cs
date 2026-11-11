using System;
using System.Collections.Generic;
using AssessmentDelivery.Business.Roster;

namespace AssessmentDelivery.Services.Admin
{
    public class SessionRoster
    {
        public int SessionId { get; set; }
        public string CenterCode { get; set; }
        public string ExamCode { get; set; }
        /// <summary>centre local time</summary>
        public DateTime StartsAt { get; set; }
        /// <summary>BOOKED, ATTENDED and WAITLISTED bookings, ordered by <see cref="RosterCsv.Order"/></summary>
        public IList<RosterLine> Lines { get; set; }
    }
}
