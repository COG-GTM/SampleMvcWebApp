using System;

namespace AssessmentDelivery.Services.Booking
{
    public class SessionListItem
    {
        public int SessionId { get; set; }
        public string ExamCode { get; set; }
        public string ExamTitle { get; set; }
        public string CenterCode { get; set; }
        public string CenterName { get; set; }
        public string City { get; set; }
        public string RoomName { get; set; }
        public DateTime StartsAt { get; set; }
        public int DurationMinutes { get; set; }
        public int Capacity { get; set; }
        public int AccommodationCapacity { get; set; }
        public int SeatsAvailable { get; set; }
        public string Status { get; set; }
    }
}
