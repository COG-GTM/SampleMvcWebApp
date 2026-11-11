using System;
using System.Collections.Generic;
using System.Data;
using System.Data.SqlClient;
using System.Data.Entity;
using System.Linq;
using AssessmentDelivery.Business.Audit;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Data.DataClasses.Entities;
using AssessmentDelivery.Services.Common;

namespace AssessmentDelivery.Services.Admin
{
    public class AdminService : IAdminService
    {
        private static readonly string[] SeatHoldingStatuses = { "BOOKED", "ATTENDED" };

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

        private IList<AdminSessionRow> Load(IQueryable<ExamSession> q)
        {
            var seatHolding = SeatHoldingStatuses;
            return q.OrderBy(s => s.StartsAt)
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
                    Booked = s.Bookings.Count(b => seatHolding.Contains(b.Status)),
                    WaitlistCount = s.Waitlist.Count()
                }).ToList()
                .Select(r => new AdminSessionRow
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
                    Booked = r.Booked,
                    WaitlistCount = r.WaitlistCount,
                    Status = r.Status
                }).ToList();
        }

        public IList<CapacityAuditRow> GetCapacityAudit(int centerId, DateTime from, DateTime? to, int? sessionId, int page)
        {
            var rows = _db.Database.SqlQuery(typeof(CapacityAuditRow),
                    "EXEC dbo.usp_GetCapacityAudit @TestCenterId, @From, @To, @SessionId, @Page, @PageSize",
                    IntParam("@TestCenterId", centerId),
                    DateParam("@From", from),
                    DateParam("@To", to),
                    IntParam("@SessionId", sessionId),
                    IntParam("@Page", page < 1 ? 1 : page),
                    IntParam("@PageSize", CapacityAuditQuery.PageSize))
                .Cast<CapacityAuditRow>()
                .ToList();
            foreach (var r in rows)
            {
                if (r.OldCapacity.HasValue && r.NewCapacity.HasValue) continue;
                // the proc returns NULLs for anything it cannot parse; the shared parser is the second opinion
                CapacityAuditDetail parsed;
                if (!CapacityAuditDetail.TryParse(r.Detail, out parsed)) continue;
                r.OldCapacity = parsed.OldCapacity;
                r.NewCapacity = parsed.NewCapacity;
                r.Delta = parsed.Delta;
            }
            return rows;
        }

        public IList<CapacitySyncRunRow> ListCapacitySyncRuns(DateTime from, DateTime? to)
        {
            return _db.Database.SqlQuery(typeof(CapacitySyncRunRow),
                    "EXEC dbo.usp_GetCapacitySyncRuns @From, @To",
                    DateParam("@From", from),
                    DateParam("@To", to))
                .Cast<CapacitySyncRunRow>()
                .ToList();
        }

        private static SqlParameter IntParam(string name, int? value)
        {
            return new SqlParameter(name, SqlDbType.Int) { Value = (object)value ?? DBNull.Value };
        }

        private static SqlParameter DateParam(string name, DateTime? value)
        {
            return new SqlParameter(name, SqlDbType.DateTime) { Value = (object)value ?? DBNull.Value };
        }
    }
}
