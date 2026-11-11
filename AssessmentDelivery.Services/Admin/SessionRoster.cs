using System;
using System.Collections.Generic;
using AssessmentDelivery.Business.Rosters;

namespace AssessmentDelivery.Services.Admin
{
    public class SessionRoster
    {
        public int SessionId { get; set; }
        public string CenterCode { get; set; }
        public string ExamCode { get; set; }
        public DateTime StartsAt { get; set; }
        /// <summary>BOOKED, ATTENDED and WAITLISTED bookings (unsorted - RosterCsvBuilder orders them)</summary>
        public IList<RosterLine> Lines { get; set; }
    }
}
