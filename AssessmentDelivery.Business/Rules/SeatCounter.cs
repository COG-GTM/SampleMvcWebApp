using System.Collections.Generic;
using System.Linq;

namespace AssessmentDelivery.Business.Rules
{
    public static class BookingStatus
    {
        public const string Booked = "BOOKED";
        public const string Cancelled = "CANCELLED";
        public const string Waitlisted = "WAITLISTED";
        public const string Attended = "ATTENDED";
    }

    /// <summary>
    /// Seat counting for the admin screens. BOOKED and ATTENDED hold a seat.
    /// (same rule as usp_BookSeat - keep the two in step)
    /// </summary>
    public static class SeatCounter
    {
        public static bool HoldsSeat(string bookingStatus)
        {
            return bookingStatus == BookingStatus.Booked || bookingStatus == BookingStatus.Attended;
        }

        public static int CountBooked(IEnumerable<string> bookingStatuses)
        {
            return bookingStatuses == null ? 0 : bookingStatuses.Count(HoldsSeat);
        }

        public static bool CanCancel(string bookingStatus)
        {
            return bookingStatus == BookingStatus.Booked || bookingStatus == BookingStatus.Waitlisted;
        }
    }
}
