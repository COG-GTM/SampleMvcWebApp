using System.Collections.Generic;
using AssessmentDelivery.Data.DataClasses.Entities;

namespace AssessmentDelivery.Services.Booking
{
    public interface IBookingService
    {
        IList<SessionListItem> FindOpenSessions(SessionSearchCriteria criteria);
        SessionListItem GetSession(int sessionId);
        IList<Exam> ListExams();
        IList<TestCenter> ListActiveCenters();

        Candidate FindCandidate(string candidateNumber);
        IList<BookingListItem> GetBookingsForCandidate(string candidateNumber);
        BookingListItem GetBooking(int bookingId);

        BookingOutcome Book(int sessionId, string candidateNumber, string accommodationCode);
        void Cancel(int bookingId, string reason);
        IList<SessionListItem> FindRescheduleTargets(int bookingId);
        BookingOutcome Reschedule(int bookingId, int newSessionId);
    }
}
