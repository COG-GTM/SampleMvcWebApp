using AssessmentDelivery.Business.Audit;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    [TestFixture]
    public class CapacityAuditDetailTests
    {
        [TestCase("20 -> 18", 20, 18, -2)]
        [TestCase("18 -> 25", 18, 25, 7)]
        [TestCase("  7->9  ", 7, 9, 2)]
        [TestCase("7 ->\t9", 7, 9, 2)]
        [TestCase("-2 -> 5", -2, 5, 7)]
        [TestCase("5 -> -1", 5, -1, -6)]
        [TestCase("0 -> 0", 0, 0, 0)]
        public void ParsesLegacyPortalFormat(string detail, int oldCapacity, int newCapacity, int delta)
        {
            CapacityAuditDetail d;
            Assert.IsTrue(CapacityAuditDetail.TryParse(detail, out d));
            Assert.AreEqual(oldCapacity, d.OldCapacity);
            Assert.AreEqual(newCapacity, d.NewCapacity);
            Assert.AreEqual(delta, d.Delta);
            Assert.IsNull(d.Actor);
        }

        [Test]
        public void ParsesRelayFormatAndByActor()
        {
            CapacityAuditDetail d;
            Assert.IsTrue(CapacityAuditDetail.TryParse(
                "outbox event=18 CapacityChanged session=42 capacity=18 seats=5 change=20 -> 18 by=admin-ui", out d));
            Assert.AreEqual(20, d.OldCapacity);
            Assert.AreEqual(18, d.NewCapacity);
            Assert.AreEqual(-2, d.Delta);
            Assert.AreEqual("admin-ui", d.Actor);
        }

        [Test]
        public void ParsesRelayFormatWithBumpedBookingsAndWhitespace()
        {
            CapacityAuditDetail d;
            Assert.IsTrue(CapacityAuditDetail.TryParse(
                "outbox event=19 CapacityChanged session=42 capacity=12 seats=0 booking=77/77 WAITLISTED change=14->12  by=jdoe", out d));
            Assert.AreEqual(14, d.OldCapacity);
            Assert.AreEqual(12, d.NewCapacity);
            Assert.AreEqual("jdoe", d.Actor);
        }

        [Test]
        public void RelayDetailWithoutChange_IsNotParseable_ButKeepsActor()
        {
            CapacityAuditDetail d;
            Assert.IsFalse(CapacityAuditDetail.TryParse(
                "outbox event=5 CapacityChanged session=42 capacity=18 seats=5 by=admin-ui", out d));
            Assert.IsNull(d.OldCapacity);
            Assert.IsNull(d.NewCapacity);
            Assert.IsNull(d.Delta);
            Assert.AreEqual("admin-ui", d.Actor);
        }

        [TestCase("garbage")]
        [TestCase("capacity reduced")]
        [TestCase("1 -> 2 -> 3")]
        [TestCase("20 -> ")]
        [TestCase("-> 18")]
        [TestCase("a -> b")]
        [TestCase("- -> 4")]
        [TestCase("20 => 18")]
        [TestCase("99999999999 -> 1")]
        [TestCase("note: 20 -> 18")]
        [TestCase("xchange=20 -> 18")]
        public void GarbageIsNotParseable(string detail)
        {
            CapacityAuditDetail d;
            Assert.IsFalse(CapacityAuditDetail.TryParse(detail, out d));
            Assert.IsNotNull(d);
            Assert.IsFalse(d.HasChange);
            Assert.IsNull(d.Delta);
        }

        [TestCase(null)]
        [TestCase("")]
        [TestCase("   ")]
        public void NullOrEmptyIsNotParseable(string detail)
        {
            CapacityAuditDetail d;
            Assert.IsFalse(CapacityAuditDetail.TryParse(detail, out d));
            Assert.IsNotNull(d);
            Assert.IsNull(d.Actor);
        }

        [Test]
        public void ParsesSyncDetail()
        {
            CapacitySyncDetail d;
            Assert.IsTrue(CapacitySyncDetail.TryParse("trigger=SCHEDULED status=COMPLETED checked=285 updated=3", out d));
            Assert.AreEqual("SCHEDULED", d.Trigger);
            Assert.AreEqual("COMPLETED", d.Status);
            Assert.AreEqual(285, d.Checked);
            Assert.AreEqual(3, d.Updated);
            Assert.IsNull(d.Reason);
        }

        [Test]
        public void ParsesSyncDetailWithFreeTextReason()
        {
            CapacitySyncDetail d;
            Assert.IsTrue(CapacitySyncDetail.TryParse("trigger=MANUAL status=FAILED checked=0 updated=0 reason=connection reset: x=y", out d));
            Assert.AreEqual("MANUAL", d.Trigger);
            Assert.AreEqual("FAILED", d.Status);
            Assert.AreEqual(0, d.Checked);
            Assert.AreEqual(0, d.Updated);
            Assert.AreEqual("connection reset: x=y", d.Reason);
        }

        [TestCase(null)]
        [TestCase("")]
        [TestCase("20 -> 18")]
        [TestCase("trigger=MANUAL status=FAILED checked=many updated=0")]
        [TestCase("status=COMPLETED trigger=MANUAL checked=1 updated=0")]
        public void SyncGarbageIsNotParseable(string detail)
        {
            CapacitySyncDetail d;
            Assert.IsFalse(CapacitySyncDetail.TryParse(detail, out d));
            Assert.IsNull(d);
        }
    }
}
