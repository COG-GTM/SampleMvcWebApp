using System;
using System.Collections.Generic;

namespace AssessmentDelivery.Services.Scheduling
{
    /// <summary>
    /// Typed client for scheduling-service (docs/to-be/scheduling-service-openapi.yaml), the single owner of
    /// ExamSession, Booking and Waitlist. Commands throw SchedulingApiException on 4xx; lookups return null on 404.
    /// </summary>
    public interface ISchedulingClient
    {
        IList<ApiSession> SearchSessions(SessionQuery query);
        ApiSession GetSession(int sessionId);
        IList<ApiSessionSummary> SessionSummaryByCenter();
        ApiCapacityChangeResult ChangeCapacity(int sessionId, int newCapacity, string actor);
        ApiCapacityAuditPage GetCapacityAudit(int testCenterId, DateTime from, DateTime to, int? sessionId, int page, int pageSize);
        ApiRoster GetRoster(int sessionId);
        IList<ApiReconcilerRun> ListReconcilerRuns(DateTime from, DateTime toExclusive, int max);

        IList<ApiBooking> SearchBookings(int candidateId, string sort);
        ApiBooking GetBooking(int bookingId);
        ApiBookingResult Book(ApiBookRequest request, string actor);
        ApiBooking Cancel(int bookingId, string reason, string actor);
        ApiBookingResult Reschedule(int bookingId, int newSessionId, string channel, string actor);
    }

    public class SessionQuery
    {
        public string Status { get; set; }
        public string ExamCode { get; set; }
        public int? ExamId { get; set; }
        public string CenterCode { get; set; }
        public int? TestCenterId { get; set; }
        public DateTime? From { get; set; }
        public DateTime? To { get; set; }
        public int? ExcludeSessionId { get; set; }
        public bool IncludeCounts { get; set; }
        public string Sort { get; set; }
    }
}
