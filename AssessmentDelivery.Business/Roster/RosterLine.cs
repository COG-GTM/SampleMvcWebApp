using System;

namespace AssessmentDelivery.Business.Roster
{
    /// <summary>
    /// One booking on an exam-session roster (BOOKED, ATTENDED or WAITLISTED).
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
        public DateTime BookedAt { get; set; }
        /// <summary>Waitlist.Position for WAITLISTED bookings; null otherwise or when no waitlist row exists</summary>
        public int? WaitlistPosition { get; set; }
    }
}
