using System;

namespace AssessmentDelivery.Business.Rosters
{
    /// <summary>
    /// One booking on a session roster (CANCELLED rows are dropped by <see cref="RosterCsvBuilder"/>)
    /// </summary>
    public class RosterLine
    {
        public string CandidateNumber { get; set; }
        public string LastName { get; set; }
        public string FirstName { get; set; }
        public string Email { get; set; }
        public string BookingStatus { get; set; }
        public string AccommodationCode { get; set; }
        public string Channel { get; set; }
        public DateTime? BookedAt { get; set; }
        /// <summary>Waitlist.Position - only written for WAITLISTED rows</summary>
        public int? WaitlistPosition { get; set; }
    }
}
