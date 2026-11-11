using System;
using System.Collections.Generic;
using System.Data;
using System.Data.Entity;
using System.Data.SqlClient;
using System.Linq;
using AssessmentDelivery.Business.Roster;
using AssessmentDelivery.Business.Rules;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Data.DataClasses.Entities;
using AssessmentDelivery.Services.Common;

namespace AssessmentDelivery.Services.Admin
{
    public class AdminService : IAdminService
    {
        private readonly AssessmentDeliveryDb _db;

        public AdminService(AssessmentDeliveryDb db)
        {
            _db = db;
        }

        public IList<CenterListItem> ListCenters()
        {
            return _db.TestCenters
                .OrderBy(c => c.CenterCode)
                .Select(c => new CenterListItem
                {
                    TestCenterId = c.TestCenterId,
                    CenterCode = c.CenterCode,
                    Name = c.Name,
                    City = c.City,
                    Region = c.Region,
                    TimeZone = c.TimeZone,
                    IsActive = c.IsActive,
                    RoomCount = c.Rooms.Count(),
                    SessionCount = c.Sessions.Count(),
                    OpenSessionCount = c.Sessions.Count(s => s.Status == "OPEN")
                }).ToList();
        }

        public TestCenter GetCenter(int centerId)
        {
            return _db.TestCenters.AsNoTracking().SingleOrDefault(c => c.TestCenterId == centerId);
        }

        /// <summary>
        /// Simple edit, straight through EF (no proc for this one)
        /// </summary>
        public void UpdateCenter(int centerId, string name, bool isActive)
        {
            var center = _db.TestCenters.Find(centerId);
            if (center == null)
                throw new PortalException("Test centre " + centerId + " was not found.");
            if (string.IsNullOrWhiteSpace(name))
                throw new PortalException("Centre name is required.");
            center.Name = name.Trim();
            center.IsActive = isActive;
            _db.SaveChanges();
        }

        public IList<AdminSessionRow> ListSessions(int centerId)
        {
            return Load(_db.ExamSessions.Where(s => s.TestCenterId == centerId));
        }

        public AdminSessionRow GetSessionRow(int sessionId)
        {
            return Load(_db.ExamSessions.Where(s => s.SessionId == sessionId)).SingleOrDefault();
        }

        public void ChangeCapacity(int sessionId, int newCapacity, string actor)
        {
            if (newCapacity < 0)
                throw new PortalException("Capacity cannot be negative.");
            try
            {
                _db.Database.ExecuteSqlCommand(TransactionalBehavior.DoNotEnsureTransaction,
                    "EXEC dbo.usp_ChangeCapacity @SessionId, @NewCapacity, @Actor",
                    new SqlParameter("@SessionId", sessionId),
                    new SqlParameter("@NewCapacity", newCapacity),
                    new SqlParameter("@Actor", SqlDbType.VarChar, 60) { Value = string.IsNullOrEmpty(actor) ? "portal-admin" : actor });
            }
            catch (Exception ex)
            {
                var pe = SqlErrorHelper.TryTranslate(ex);
                if (pe != null) throw pe;
                throw;
            }
        }

        public CapacityAuditResult GetCapacityAudit(int centerId, DateTime from, DateTime toExclusive, int? sessionId, int pageNumber, int pageSize)
        {
            var rows = QueryCapacityAudit(centerId, from, toExclusive, sessionId, pageNumber, pageSize);
            foreach (var row in rows)
            {
                var parsed = CapacityAuditDetail.TryParse(row.Detail);
                row.OldCapacity = parsed == null ? (int?)null : parsed.OldCapacity;
                row.NewCapacity = parsed == null ? (int?)null : parsed.NewCapacity;
                row.Delta = parsed == null ? (long?)null : parsed.Delta;
            }

            int total;
            if (rows.Count > 0) total = rows[0].TotalRows;
            else if (pageNumber > 1)
            {
                // past the last page: still report how many rows match
                var first = QueryCapacityAudit(centerId, from, toExclusive, sessionId, 1, 1);
                total = first.Count == 0 ? 0 : first[0].TotalRows;
            }
            else total = 0;
            return new CapacityAuditResult { Rows = rows, TotalRows = total };
        }

        private List<CapacityAuditRow> QueryCapacityAudit(int centerId, DateTime from, DateTime toExclusive, int? sessionId, int pageNumber, int pageSize)
        {
            return _db.Database.SqlQuery<CapacityAuditRow>(
                "EXEC dbo.usp_GetCapacityAudit @TestCenterId, @From, @To, @SessionId, @PageNumber, @PageSize",
                new SqlParameter("@TestCenterId", centerId),
                new SqlParameter("@From", SqlDbType.DateTime) { Value = from },
                new SqlParameter("@To", SqlDbType.DateTime) { Value = toExclusive },
                new SqlParameter("@SessionId", SqlDbType.Int) { Value = sessionId.HasValue ? (object)sessionId.Value : DBNull.Value },
                new SqlParameter("@PageNumber", pageNumber),
                new SqlParameter("@PageSize", pageSize)).ToList();
        }

        public IList<CapacitySyncRunRow> ListCapacitySyncRuns(DateTime from, DateTime toExclusive, int max)
        {
            var raw = _db.AuditLogs.AsNoTracking()
                .Where(a => a.EntityName == "ExamSession" && a.Action == "CAPACITY_SYNC"
                            && a.LoggedAt >= from && a.LoggedAt < toExclusive)
                .OrderByDescending(a => a.LoggedAt).ThenByDescending(a => a.AuditId)
                .Take(max)
                .ToList();

            return raw.Select(a =>
            {
                var d = CapacitySyncDetail.Parse(a.Detail);
                return new CapacitySyncRunRow
                {
                    AuditId = a.AuditId,
                    LoggedAt = a.LoggedAt,
                    Actor = a.Actor,
                    Detail = a.Detail,
                    Trigger = d.Trigger,
                    Status = d.Status,
                    SessionsChecked = d.Checked,
                    SessionsUpdated = d.Updated
                };
            }).ToList();
        }

        /// <summary>
        /// Read-only (no tracking, no procs, no SaveChanges). WAITLISTED rows take their position
        /// from the Waitlist row with the same BookingId; the schema does not guarantee one exists
        /// (docs/as-is/hidden-behaviours.md HB-28) or that positions are gap-free (HB-06).
        /// </summary>
        public SessionRoster GetRoster(int sessionId)
        {
            var session = _db.ExamSessions.AsNoTracking()
                .Where(s => s.SessionId == sessionId)
                .Select(s => new { s.SessionId, s.TestCenter.CenterCode, s.Exam.ExamCode, s.StartsAt })
                .SingleOrDefault();
            if (session == null) return null;

            var lines = _db.Bookings.AsNoTracking()
                .Where(b => b.SessionId == sessionId
                            && (b.Status == BookingStatus.Booked
                                || b.Status == BookingStatus.Attended
                                || b.Status == BookingStatus.Waitlisted))
                .Select(b => new RosterLine
                {
                    CandidateNumber = b.Candidate.CandidateNumber,
                    LastName = b.Candidate.LastName,
                    FirstName = b.Candidate.FirstName,
                    Email = b.Candidate.Email,
                    BookingStatus = b.Status,
                    AccommodationCode = b.AccommodationCode,
                    Channel = b.Channel,
                    BookedAt = b.BookedAt,
                    WaitlistPosition = b.Status == BookingStatus.Waitlisted
                        ? _db.Waitlist.Where(w => w.BookingId == b.BookingId).Min(w => (int?)w.Position)
                        : null
                }).ToList();

            return new SessionRoster
            {
                SessionId = session.SessionId,
                CenterCode = session.CenterCode,
                ExamCode = session.ExamCode,
                StartsAt = session.StartsAt,
                Lines = RosterCsv.Order(lines)
            };
        }

        private IList<AdminSessionRow> Load(IQueryable<ExamSession> q)
        {
            var raw = q.OrderBy(s => s.StartsAt)
                .Select(s => new
                {
                    s.SessionId,
                    s.TestCenterId,
                    CenterName = s.TestCenter.Name,
                    s.Exam.ExamCode,
                    s.Exam.Title,
                    s.Room.RoomName,
                    s.StartsAt,
                    s.Capacity,
                    s.AccommodationCapacity,
                    s.SeatsAvailable,
                    s.Status,
                    Statuses = s.Bookings.Select(b => b.Status),
                    WaitlistCount = s.Waitlist.Count()
                }).ToList();

            //Booked is counted from the booking rows here
            return raw.Select(r => new AdminSessionRow
            {
                SessionId = r.SessionId,
                TestCenterId = r.TestCenterId,
                CenterName = r.CenterName,
                ExamCode = r.ExamCode,
                ExamTitle = r.Title,
                RoomName = r.RoomName,
                StartsAt = r.StartsAt,
                Capacity = r.Capacity,
                AccommodationCapacity = r.AccommodationCapacity,
                SeatsAvailable = r.SeatsAvailable,
                Booked = SeatCounter.CountBooked(r.Statuses),
                WaitlistCount = r.WaitlistCount,
                Status = r.Status
            }).ToList();
        }
    }
}
