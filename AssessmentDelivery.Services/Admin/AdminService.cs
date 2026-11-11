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
