using System;
using System.Collections.Generic;
using AssessmentDelivery.Services.Admin;
using AssessmentDelivery.Services.Common;
using AssessmentDelivery.Services.Scheduling;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    /// <summary>Change-capacity goes to scheduling-service; validation and messages are the ones the portal showed before (S-9).</summary>
    [TestFixture]
    public class AdminCapacityTests
    {
        private class CapacityOnlyClient : ISchedulingClient
        {
            public int Calls;
            public string Actor;
            public Exception Throw;

            public ApiCapacityChangeResult ChangeCapacity(int sessionId, int newCapacity, string actor)
            {
                Calls++;
                Actor = actor;
                if (Throw != null) throw Throw;
                return new ApiCapacityChangeResult { SessionId = sessionId, NewCapacity = newCapacity };
            }

            public IList<ApiSession> SearchSessions(SessionQuery query) { throw new NotImplementedException(); }
            public ApiSession GetSession(int sessionId) { throw new NotImplementedException(); }
            public IList<ApiSessionSummary> SessionSummaryByCenter() { throw new NotImplementedException(); }
            public ApiCapacityAuditPage GetCapacityAudit(int testCenterId, DateTime from, DateTime to, int? sessionId, int page, int pageSize) { throw new NotImplementedException(); }
            public ApiRoster GetRoster(int sessionId) { throw new NotImplementedException(); }
            public List<ApiReconcilerRun> Runs;
            public IList<ApiReconcilerRun> ListReconcilerRuns(DateTime from, DateTime toExclusive, int max) { return Runs; }
            public IList<ApiBooking> SearchBookings(int candidateId, string sort) { throw new NotImplementedException(); }
            public ApiBooking GetBooking(int bookingId) { throw new NotImplementedException(); }
            public ApiBookingResult Book(ApiBookRequest request, string actor) { throw new NotImplementedException(); }
            public ApiBooking Cancel(int bookingId, string reason, string actor) { throw new NotImplementedException(); }
            public ApiBookingResult Reschedule(int bookingId, int newSessionId, string channel, string actor) { throw new NotImplementedException(); }
        }

        [Test]
        public void ReconcilerRunsKeepTheLegacyCapacitySyncRowShape()
        {
            var client = new CapacityOnlyClient
            {
                Runs = new List<ApiReconcilerRun>
                {
                    new ApiReconcilerRun { RunId = 4, Trigger = "SCHEDULED", Status = "OK", StartedAt = new DateTime(2026, 11, 11, 2, 0, 0),
                        FinishedAt = new DateTime(2026, 11, 11, 2, 0, 3), Checked = 285, Updated = 112 },
                    new ApiReconcilerRun { RunId = 3, Trigger = "MANUAL", Status = "FAILED", StartedAt = new DateTime(2026, 11, 10, 9, 0, 0), Actor = "ops" }
                }
            };
            var rows = new AdminService(null, client).ListCapacitySyncRuns(new DateTime(2026, 11, 1), new DateTime(2026, 11, 12), 51);

            Assert.AreEqual(2, rows.Count);
            Assert.AreEqual("COMPLETED", rows[0].Status);
            Assert.AreEqual("capacity-sync", rows[0].Actor);
            Assert.AreEqual(new DateTime(2026, 11, 11, 2, 0, 3), rows[0].LoggedAt);
            Assert.AreEqual("trigger=SCHEDULED status=COMPLETED checked=285 updated=112", rows[0].Detail);
            Assert.AreEqual(285, rows[0].SessionsChecked);
            Assert.AreEqual(112, rows[0].SessionsUpdated);
            Assert.AreEqual("FAILED", rows[1].Status);
            Assert.AreEqual("ops", rows[1].Actor);
            Assert.AreEqual(new DateTime(2026, 11, 10, 9, 0, 0), rows[1].LoggedAt);
        }

        [Test]
        public void NegativeCapacityIsRejectedLocally()
        {
            var client = new CapacityOnlyClient();
            var ex = Assert.Throws<PortalException>(() => new AdminService(null, client).ChangeCapacity(7, -1, "admin"));
            Assert.AreEqual("Capacity cannot be negative.", ex.Message);
            Assert.AreEqual(0, client.Calls);
        }

        [Test]
        public void ServiceRejectionShowsTheLegacyMessage()
        {
            var client = new CapacityOnlyClient
            {
                Throw = new SchedulingApiException(422, "INVALID_CAPACITY", "Invalid capacity 1 for session 7.", 5)
            };
            var ex = Assert.Throws<PortalException>(() => new AdminService(null, client).ChangeCapacity(7, 1, "admin"));
            Assert.AreEqual("Invalid capacity 1 for session 7.", ex.Message);
        }

        [Test]
        public void ActorDefaultsToPortalAdmin()
        {
            var client = new CapacityOnlyClient();
            new AdminService(null, client).ChangeCapacity(7, 30, null);
            Assert.AreEqual(1, client.Calls);
            Assert.AreEqual("portal-admin", client.Actor);
        }
    }
}
