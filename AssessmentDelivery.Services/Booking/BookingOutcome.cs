namespace AssessmentDelivery.Services.Booking
{
    public class BookingOutcome
    {
        public int BookingId { get; set; }
        /// <summary>BOOKED or WAITLISTED</summary>
        public string Status { get; set; }
    }
}
