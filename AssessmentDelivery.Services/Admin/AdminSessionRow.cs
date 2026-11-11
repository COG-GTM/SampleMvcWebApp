using System;

namespace AssessmentDelivery.Services.Admin
{
    public class AdminSessionRow
    {
        public int SessionId { get; set; }
        public int TestCenterId { get; set; }
        public string CenterName { get; set; }
        public string ExamCode { get; set; }
        public string ExamTitle { get; set; }
        public string RoomName { get; set; }
        public DateTime StartsAt { get; set; }
        public int Capacity { get; set; }
        public int AccommodationCapacity { get; set; }
        public int SeatsAvailable { get; set; }
        /// <summary>BOOKED + ATTENDED, counted here rather than trusting SeatsAvailable</summary>
        public int Booked { get; set; }
        public int WaitlistCount { get; set; }
        public string Status { get; set; }
    }
}
