using AssessmentDelivery.Business.Rules;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    [TestFixture]
    public class SeatCounterTests
    {
        [Test]
        public void BookedFigureCountsBookedAndAttendedOnly()
        {
            var statuses = new[] { "BOOKED", "ATTENDED", "CANCELLED", "WAITLISTED", "BOOKED" };

            Assert.AreEqual(3, SeatCounter.CountBooked(statuses));
        }
    }
}
