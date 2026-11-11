using System;
using System.Collections.Generic;
using System.Data;
using System.Data.Entity;
using System.Data.SqlClient;
using System.Linq;
using AssessmentDelivery.Business.Rules;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Data.DataClasses.Entities;
using AssessmentDelivery.Services.Common;
using AssessmentDelivery.Services.Eligibility;

namespace AssessmentDelivery.Services.Booking
{
    public class BookingService : IBookingService
    {
        private readonly AssessmentDeliveryDb _db;
        private readonly IEligibilityService _eligibility;

        public BookingService(AssessmentDeliveryDb db, IEligibilityService eligibility)
        {
            _db = db;
            _eligibility = eligibility;
        }

        public IList<SessionListItem> FindOpenSessions(SessionSearchCriteria criteria)
        {
            var q = _db.ExamSessions.Where(s => s.Status == "OPEN");
            if (criteria != null)
            {
                if (!string.IsNullOrWhiteSpace(criteria.ExamCode))
                    q = q.Where(s => s.Exam.ExamCode == criteria.ExamCode);
                if (!string.IsNullOrWhiteSpace(criteria.CenterCode))
                    q = q.Where(s => s.TestCenter.CenterCode == criteria.CenterCode);
                if (criteria.From.HasValue)
                {
                    var from = criteria.From.Value.Date;
                    q = q.Where(s => s.StartsAt >= from);
                }
                if (criteria.To.HasValue)
                {
                    var to = criteria.To.Value.Date.AddDays(1);
                    q = q.Where(s => s.StartsAt < to);
                }
            }
            return Project(q).OrderBy(s => s.StartsAt).ThenBy(s => s.CenterCode).ToList();
        }

        public SessionListItem GetSession(int sessionId)
        {
            return Project(_db.ExamSessions.Where(s => s.SessionId == sessionId)).SingleOrDefault();
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

            var list = ProjectBookings(_db.Bookings.Where(b => b.CandidateId == candidate.CandidateId))
                .OrderByDescending(b => b.StartsAt).ThenByDescending(b => b.BookingId).ToList();
            foreach (var item in list)
                item.CanChange = SeatCounter.CanCancel(item.Status);
            return list;
        }

        public BookingListItem GetBooking(int bookingId)
        {
            var item = ProjectBookings(_db.Bookings.Where(b => b.BookingId == bookingId)).SingleOrDefault();
            if (item != null) item.CanChange = SeatCounter.CanCancel(item.Status);
            return item;
        }

        public BookingOutcome Book(int sessionId, string candidateNumber, string accommodationCode)
        {
            var candidate = FindCandidate(candidateNumber);
            if (candidate == null)
                throw new PortalException("Candidate number " + candidateNumber + " was not found.");

            var session = _db.ExamSessions.Include(s => s.Exam).AsNoTracking().SingleOrDefault(s => s.SessionId == sessionId);
            if (session == null)
                throw new PortalException("Session " + sessionId + " was not found.");

            //blank on the form = use the candidate's default from their profile
            var requested = string.IsNullOrWhiteSpace(accommodationCode) ? candidate.DefaultAccommodationCode : accommodationCode;
            string code;
            if (!AccommodationCodes.TryParse(requested, out code))
                throw new PortalException("Unknown accommodation code '" + requested + "'.");

            //retake check has to happen here, usp_BookSeat does not know about results
            var decision = _eligibility.Check(candidate.CandidateNumber, session.Exam.ExamCode, session.StartsAt);
            if (!decision.Eligible)
                throw new PortalException("Not eligible to book " + session.Exam.ExamCode + ": " + decision.Reason);

            var pBookingId = new SqlParameter("@BookingId", SqlDbType.Int) { Direction = ParameterDirection.Output };
            var pStatus = new SqlParameter("@ResultStatus", SqlDbType.VarChar, 12) { Direction = ParameterDirection.Output };
            try
            {
                _db.Database.ExecuteSqlCommand(TransactionalBehavior.DoNotEnsureTransaction,
                    "EXEC dbo.usp_BookSeat @SessionId, @CandidateId, @AccommodationCode, @BookingId OUTPUT, @ResultStatus OUTPUT",
                    new SqlParameter("@SessionId", sessionId),
                    new SqlParameter("@CandidateId", candidate.CandidateId),
                    new SqlParameter("@AccommodationCode", SqlDbType.VarChar, 10) { Value = (object)code ?? DBNull.Value },
                    pBookingId, pStatus);
            }
            catch (Exception ex)
            {
                var pe = SqlErrorHelper.TryTranslate(ex);
                if (pe != null) throw pe;
                throw;
            }
            return new BookingOutcome { BookingId = (int)pBookingId.Value, Status = (string)pStatus.Value };
        }

        public void Cancel(int bookingId, string reason)
        {
            try
            {
                _db.Database.ExecuteSqlCommand(TransactionalBehavior.DoNotEnsureTransaction,
                    "EXEC dbo.usp_CancelBooking @BookingId, @Reason, @Actor",
                    new SqlParameter("@BookingId", bookingId),
                    new SqlParameter("@Reason", SqlDbType.NVarChar, 200) { Value = string.IsNullOrWhiteSpace(reason) ? (object)DBNull.Value : reason },
                    new SqlParameter("@Actor", SqlDbType.VarChar, 60) { Value = "portal" });
            }
            catch (Exception ex)
            {
                var pe = SqlErrorHelper.TryTranslate(ex);
                if (pe != null) throw pe;
                throw;
            }
        }

        public IList<SessionListItem> FindRescheduleTargets(int bookingId)
        {
            var booking = _db.Bookings.AsNoTracking().Include(b => b.Session).SingleOrDefault(b => b.BookingId == bookingId);
            if (booking == null) return new List<SessionListItem>();
            var examId = booking.Session.ExamId;
            var today = DateTime.Today;
            var q = _db.ExamSessions.Where(s => s.ExamId == examId && s.Status == "OPEN"
                                                && s.SessionId != booking.SessionId && s.StartsAt >= today);
            return Project(q).OrderBy(s => s.StartsAt).ToList();
        }

        public BookingOutcome Reschedule(int bookingId, int newSessionId)
        {
            var pNewId = new SqlParameter("@NewBookingId", SqlDbType.Int) { Direction = ParameterDirection.Output };
            var pStatus = new SqlParameter("@ResultStatus", SqlDbType.VarChar, 12) { Direction = ParameterDirection.Output };
            try
            {
                _db.Database.ExecuteSqlCommand(TransactionalBehavior.DoNotEnsureTransaction,
                    "EXEC dbo.usp_RescheduleBooking @BookingId, @NewSessionId, @NewBookingId OUTPUT, @ResultStatus OUTPUT",
                    new SqlParameter("@BookingId", bookingId),
                    new SqlParameter("@NewSessionId", newSessionId),
                    pNewId, pStatus);
            }
            catch (Exception ex)
            {
                var pe = SqlErrorHelper.TryTranslate(ex);
                if (pe != null) throw pe;
                throw;
            }
            if (pNewId.Value == DBNull.Value)
                throw new PortalException("Booking " + bookingId + " could not be rescheduled.");
            return new BookingOutcome { BookingId = (int)pNewId.Value, Status = (string)pStatus.Value };
        }

        //-------------------------------------------------

        internal static IQueryable<SessionListItem> Project(IQueryable<ExamSession> q)
        {
            return q.Select(s => new SessionListItem
            {
                SessionId = s.SessionId,
                ExamCode = s.Exam.ExamCode,
                ExamTitle = s.Exam.Title,
                CenterCode = s.TestCenter.CenterCode,
                CenterName = s.TestCenter.Name,
                City = s.TestCenter.City,
                RoomName = s.Room.RoomName,
                StartsAt = s.StartsAt,
                DurationMinutes = s.Exam.DurationMinutes,
                Capacity = s.Capacity,
                AccommodationCapacity = s.AccommodationCapacity,
                SeatsAvailable = s.SeatsAvailable,
                Status = s.Status
            });
        }

        private static IQueryable<BookingListItem> ProjectBookings(IQueryable<Data.DataClasses.Entities.Booking> q)
        {
            return q.Select(b => new BookingListItem
            {
                BookingId = b.BookingId,
                SessionId = b.SessionId,
                ExamCode = b.Session.Exam.ExamCode,
                ExamTitle = b.Session.Exam.Title,
                CenterName = b.Session.TestCenter.Name,
                StartsAt = b.Session.StartsAt,
                Status = b.Status,
                AccommodationCode = b.AccommodationCode,
                Channel = b.Channel,
                ExternalRef = b.ExternalRef,
                BookedAt = b.BookedAt,
                CancelledAt = b.CancelledAt,
                CancelReason = b.CancelReason
            });
        }
    }
}
