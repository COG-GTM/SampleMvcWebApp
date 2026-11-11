using System;

namespace AssessmentDelivery.Services.Booking
{
    public class BookingListItem
    {
        public int BookingId { get; set; }
        public int SessionId { get; set; }
        public string ExamCode { get; set; }
        public string ExamTitle { get; set; }
        public string CenterName { get; set; }
        public DateTime StartsAt { get; set; }
        public string Status { get; set; }
        public string AccommodationCode { get; set; }
        public string Channel { get; set; }
        public string ExternalRef { get; set; }
        public DateTime BookedAt { get; set; }
        public DateTime? CancelledAt { get; set; }
        public string CancelReason { get; set; }
        public bool CanChange { get; set; }
    }
}
