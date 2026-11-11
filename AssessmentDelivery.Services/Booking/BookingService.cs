using System;
using System.Collections.Generic;
using System.Linq;
using AssessmentDelivery.Business.Rules;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Data.DataClasses.Entities;
using AssessmentDelivery.Services.Common;
using AssessmentDelivery.Services.Eligibility;
using AssessmentDelivery.Services.Scheduling;

namespace AssessmentDelivery.Services.Booking
{
    /// <summary>
    /// Portal booking flows. ExamSession, Booking and Waitlist are owned by scheduling-service and reached
    /// through ISchedulingClient (docs/to-be/portal-contract-spec.md); Exam, TestCenter and Candidate stay
    /// local reference reads. Retake eligibility stays here: the service does not check it (HB-08).
    /// </summary>
    public class BookingService : IBookingService
    {
        public const string Channel = "PORTAL";
        public const string Actor = "portal";

        private readonly AssessmentDeliveryDb _db;
        private readonly IEligibilityService _eligibility;
        private readonly ISchedulingClient _scheduling;

        public BookingService(AssessmentDeliveryDb db, IEligibilityService eligibility, ISchedulingClient scheduling)
        {
            _db = db;
            _eligibility = eligibility;
            _scheduling = scheduling;
        }

        public IList<SessionListItem> FindOpenSessions(SessionSearchCriteria criteria)
        {
            var query = new SessionQuery { Status = "OPEN", Sort = "startsAt,centerCode" };
            if (criteria != null)
            {
                if (!string.IsNullOrWhiteSpace(criteria.ExamCode)) query.ExamCode = criteria.ExamCode;
                if (!string.IsNullOrWhiteSpace(criteria.CenterCode)) query.CenterCode = criteria.CenterCode;
                if (criteria.From.HasValue) query.From = criteria.From.Value.Date;
                if (criteria.To.HasValue) query.To = criteria.To.Value.Date;
            }
            return _scheduling.SearchSessions(query).Select(ToSessionListItem).ToList();
        }

        public SessionListItem GetSession(int sessionId)
        {
            var session = _scheduling.GetSession(sessionId);
            return session == null ? null : ToSessionListItem(session);
        }

        public IList<Exam> ListExams()
        {
            return _db.Exams.AsNoTracking().Where(e => e.IsPublished).OrderBy(e => e.ExamCode).ToList();
        }

        public IList<TestCenter> ListActiveCenters()
        {
            return _db.TestCenters.AsNoTracking().Where(c => c.IsActive).OrderBy(c => c.CenterCode).ToList();
        }

        public Candidate FindCandidate(string candidateNumber)
        {
            if (string.IsNullOrWhiteSpace(candidateNumber)) return null;
            var number = candidateNumber.Trim().ToUpper();
            return _db.Candidates.AsNoTracking().SingleOrDefault(c => c.CandidateNumber == number);
        }

        public IList<BookingListItem> GetBookingsForCandidate(string candidateNumber)
        {
            var candidate = FindCandidate(candidateNumber);
            if (candidate == null) return new List<BookingListItem>();

            //sessionStartsAt,desc = StartsAt DESC, BookingId DESC (the old EF ordering)
            return _scheduling.SearchBookings(candidate.CandidateId, "sessionStartsAt,desc")
                .Select(ToBookingListItem).ToList();
        }

        public BookingListItem GetBooking(int bookingId)
        {
            var booking = _scheduling.GetBooking(bookingId);
            return booking == null ? null : ToBookingListItem(booking);
        }

        public BookingOutcome Book(int sessionId, string candidateNumber, string accommodationCode)
        {
            var candidate = FindCandidate(candidateNumber);
            if (candidate == null)
                throw new PortalException("Candidate number " + candidateNumber + " was not found.");

            var session = _scheduling.GetSession(sessionId);
            if (session == null)
                throw new PortalException("Session " + sessionId + " was not found.");

            //blank on the form = use the candidate's default from their profile (HB-09: the service takes it literally)
            var requested = string.IsNullOrWhiteSpace(accommodationCode) ? candidate.DefaultAccommodationCode : accommodationCode;
            string code;
            if (!AccommodationCodes.TryParse(requested, out code))
                throw new PortalException("Unknown accommodation code '" + requested + "'.");

            //retake check has to happen here, scheduling-service does not know about results
            var decision = _eligibility.Check(candidate.CandidateNumber, session.ExamCode, session.StartsAt);
            if (!decision.Eligible)
                throw new PortalException("Not eligible to book " + session.ExamCode + ": " + decision.Reason);

            var result = Call(() => _scheduling.Book(new ApiBookRequest
            {
                SessionId = sessionId,
                CandidateId = candidate.CandidateId,
                AccommodationCode = code,
                Channel = Channel
            }, Actor));
            return new BookingOutcome { BookingId = result.BookingId, Status = result.Status };
        }

        public void Cancel(int bookingId, string reason)
        {
            Call(() => _scheduling.Cancel(bookingId, reason, Actor));
        }

        public IList<SessionListItem> FindRescheduleTargets(int bookingId)
        {
            var booking = _scheduling.GetBooking(bookingId);
            if (booking == null) return new List<SessionListItem>();
            var examId = booking.ExamId;
            if (!examId.HasValue)
            {
                var current = _scheduling.GetSession(booking.SessionId);
                if (current == null) return new List<SessionListItem>();
                examId = current.ExamId;
            }
            return _scheduling.SearchSessions(new SessionQuery
            {
                Status = "OPEN",
                ExamId = examId,
                ExcludeSessionId = booking.SessionId,
                From = DateTime.Today,
                Sort = "startsAt,sessionId"
            }).Select(ToSessionListItem).ToList();
        }

        public BookingOutcome Reschedule(int bookingId, int newSessionId)
        {
            var booking = _scheduling.GetBooking(bookingId);
            var target = _scheduling.GetSession(newSessionId);
            if (booking != null && target != null)
            {
                //same retake check as Book - the service only checks the exam matches
                var decision = _eligibility.Check(booking.CandidateNumber, target.ExamCode, target.StartsAt);
                if (!decision.Eligible)
                    throw new PortalException("Not eligible to book " + target.ExamCode + ": " + decision.Reason);
            }

            //channel PORTAL = usp_RescheduleBooking semantics: new booking is PORTAL with no ExternalRef (HB-10)
            var result = Call(() => _scheduling.Reschedule(bookingId, newSessionId, Channel, Actor));
            if (result == null || result.BookingId == 0)
                throw new PortalException("Booking " + bookingId + " could not be rescheduled.");
            return new BookingOutcome { BookingId = result.BookingId, Status = result.Status };
        }

        //-------------------------------------------------

        /// <summary>
        /// 4xx answers carry the legacy RAISERROR text in message (contract section 4), which is exactly what the
        /// portal used to surface through SqlErrorHelper, so it is shown unchanged.
        /// </summary>
        internal static T Call<T>(Func<T> call)
        {
            try
            {
                return call();
            }
            catch (SchedulingApiException ex)
            {
                throw new PortalException(ex.Message, ex);
            }
        }

        internal static SessionListItem ToSessionListItem(ApiSession s)
        {
            return new SessionListItem
            {
                SessionId = s.SessionId,
                ExamCode = s.ExamCode,
                ExamTitle = s.ExamTitle,
                CenterCode = s.CenterCode,
                CenterName = s.CenterName,
                City = s.City,
                RoomName = s.RoomName,
                StartsAt = s.StartsAt,
                DurationMinutes = s.DurationMinutes ?? 0,
                Capacity = s.Capacity,
                AccommodationCapacity = s.AccommodationCapacity,
                SeatsAvailable = s.SeatsAvailable,
                Status = s.Status
            };
        }

        internal static BookingListItem ToBookingListItem(ApiBooking b)
        {
            return new BookingListItem
            {
                BookingId = b.BookingId,
                SessionId = b.SessionId,
                ExamCode = b.ExamCode,
                ExamTitle = b.ExamTitle,
                CenterName = b.CenterName,
                StartsAt = b.SessionStartsAt,
                Status = b.Status,
                AccommodationCode = b.AccommodationCode,
                Channel = b.Channel,
                ExternalRef = b.ExternalRef,
                BookedAt = b.BookedAt,
                CancelledAt = b.CancelledAt,
                CancelReason = b.CancelReason,
                CanChange = SeatCounter.CanCancel(b.Status)
            };
        }
    }
}
