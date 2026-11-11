using System;
using System.Collections.Generic;
using System.Linq;
using AssessmentDelivery.Business.Roster;
using AssessmentDelivery.Business.Rules;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Data.DataClasses.Entities;
using AssessmentDelivery.Services.Booking;
using AssessmentDelivery.Services.Common;
using AssessmentDelivery.Services.Scheduling;

namespace AssessmentDelivery.Services.Admin
{
    /// <summary>
    /// Admin screens. Session rows, capacity changes, capacity audit and rosters come from scheduling-service
    /// (owner of ExamSession/Booking/Waitlist); seat re-sync runs come from its reconciler; centres and rooms stay local.
    /// </summary>
    public class AdminService : IAdminService
    {
        private readonly AssessmentDeliveryDb _db;
        private readonly ISchedulingClient _scheduling;

        public AdminService(AssessmentDeliveryDb db, ISchedulingClient scheduling)
        {
            _db = db;
            _scheduling = scheduling;
        }

        public IList<CenterListItem> ListCenters()
        {
            var centers = _db.TestCenters
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
                    RoomCount = c.Rooms.Count()
                }).ToList();

            var counts = _scheduling.SessionSummaryByCenter().ToDictionary(s => s.TestCenterId);
            foreach (var center in centers)
            {
                ApiSessionSummary summary;
                if (!counts.TryGetValue(center.TestCenterId, out summary)) continue;
                center.SessionCount = summary.SessionCount;
                center.OpenSessionCount = summary.OpenSessionCount;
            }
            return centers;
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
            return _scheduling.SearchSessions(new SessionQuery
            {
                TestCenterId = centerId,
                IncludeCounts = true,
                Sort = "startsAt,sessionId"
            }).Select(ToAdminRow).ToList();
        }

        public AdminSessionRow GetSessionRow(int sessionId)
        {
            var session = _scheduling.GetSession(sessionId);
            return session == null ? null : ToAdminRow(session);
        }

        public void ChangeCapacity(int sessionId, int newCapacity, string actor)
        {
            if (newCapacity < 0)
                throw new PortalException("Capacity cannot be negative.");
            BookingService.Call(() => _scheduling.ChangeCapacity(sessionId, newCapacity,
                string.IsNullOrEmpty(actor) ? "portal-admin" : actor));
        }

        public CapacityAuditResult GetCapacityAudit(int centerId, DateTime from, DateTime toExclusive, int? sessionId, int pageNumber, int pageSize)
        {
            var page = _scheduling.GetCapacityAudit(centerId, from, toExclusive, sessionId, pageNumber, pageSize);
            var rows = (page.Rows ?? new List<ApiCapacityAuditRow>()).Select(r =>
            {
                var parsed = CapacityAuditDetail.TryParse(r.Detail);
                return new CapacityAuditRow
                {
                    AuditId = r.AuditId,
                    LoggedAt = r.LoggedAt,
                    Actor = r.Actor,
                    Detail = r.Detail,
                    SessionId = r.SessionId,
                    ExamCode = r.ExamCode,
                    SessionStartsAt = r.SessionStartsAt,
                    RoomName = r.RoomName,
                    OldCapacity = parsed == null ? (int?)null : parsed.OldCapacity,
                    NewCapacity = parsed == null ? (int?)null : parsed.NewCapacity,
                    Delta = parsed == null ? (long?)null : parsed.Delta,
                    CurrentCapacity = r.CurrentCapacity,
                    CurrentSeatsAvailable = r.CurrentSeatsAvailable,
                    TotalRows = page.TotalRows
                };
            }).ToList();
            return new CapacityAuditResult { Rows = rows, TotalRows = page.TotalRows };
        }

        /// <summary>
        /// Seat re-sync runs from the scheduling-service reconciler (capacity-sync is retired into it). Rows keep the
        /// legacy CAPACITY_SYNC audit shape: status COMPLETED/FAILED, actor capacity-sync, detail 'trigger= status= checked= updated='.
        /// </summary>
        public IList<CapacitySyncRunRow> ListCapacitySyncRuns(DateTime from, DateTime toExclusive, int max)
        {
            // The service filters on StartedAt; legacy filtered on the audit row time (= finish). Ask a day earlier so
            // a run that crossed 'from' is included, and one extra row in case the newest run crosses 'toExclusive',
            // then filter on the finish time.
            var runs = BookingService.Call(() => _scheduling.ListReconcilerRuns(from.AddDays(-1), toExclusive, max + 1))
                       ?? new List<ApiReconcilerRun>();
            return runs.Select(r =>
            {
                var status = r.Status == "OK" ? "COMPLETED" : r.Status;
                return new CapacitySyncRunRow
                {
                    AuditId = r.RunId,
                    LoggedAt = r.FinishedAt ?? r.StartedAt,
                    Actor = string.IsNullOrEmpty(r.Actor) ? "capacity-sync" : r.Actor,
                    Detail = "trigger=" + r.Trigger + " status=" + status + " checked=" + r.Checked + " updated=" + r.Updated,
                    Trigger = r.Trigger,
                    Status = status,
                    SessionsChecked = r.Checked,
                    SessionsUpdated = r.Updated
                };
            }).Where(row => row.LoggedAt >= from && row.LoggedAt < toExclusive).Take(max).ToList();
        }

        /// <summary>
        /// Read-only. WAITLISTED rows carry MIN(Waitlist.Position) for the booking, or null when the booking has no
        /// Waitlist row (docs/as-is/hidden-behaviours.md HB-28); positions are not gap-free (HB-06).
        /// </summary>
        public SessionRoster GetRoster(int sessionId)
        {
            var roster = _scheduling.GetRoster(sessionId);
            if (roster == null) return null;

            var lines = (roster.Lines ?? new List<ApiRosterLine>()).Select(l => new RosterLine
            {
                CandidateNumber = l.CandidateNumber,
                LastName = l.LastName,
                FirstName = l.FirstName,
                Email = l.Email,
                BookingStatus = l.BookingStatus,
                AccommodationCode = l.AccommodationCode,
                Channel = l.Channel,
                BookedAt = l.BookedAt,
                WaitlistPosition = l.WaitlistPosition
            }).ToList();

            return new SessionRoster
            {
                SessionId = roster.SessionId,
                CenterCode = roster.CenterCode,
                ExamCode = roster.ExamCode,
                StartsAt = roster.StartsAt,
                Lines = RosterCsv.Order(lines)
            };
        }

        internal static AdminSessionRow ToAdminRow(ApiSession s)
        {
            return new AdminSessionRow
            {
                SessionId = s.SessionId,
                TestCenterId = s.TestCenterId,
                CenterName = s.CenterName,
                ExamCode = s.ExamCode,
                ExamTitle = s.ExamTitle,
                RoomName = s.RoomName,
                StartsAt = s.StartsAt,
                Capacity = s.Capacity,
                AccommodationCapacity = s.AccommodationCapacity,
                SeatsAvailable = s.SeatsAvailable,
                //BOOKED + ATTENDED counted by the service from booking rows, not from SeatsAvailable
                Booked = s.BookedSeats ?? 0,
                WaitlistCount = s.WaitlistCount ?? 0,
                Status = s.Status
            };
        }
    }
}
