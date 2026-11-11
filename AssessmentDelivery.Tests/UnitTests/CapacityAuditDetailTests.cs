using System;
using AssessmentDelivery.Business.Rules;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    [TestFixture]
    public class CapacityAuditDetailTests
    {
        [TestCase("12 -> 9", 12, 9, -3)]
        [TestCase("0 -> 30", 0, 30, 30)]
        [TestCase("-1 -> 5", -1, 5, 6)]
        [TestCase("2147483647 -> 0", 2147483647, 0, -2147483647)]
        public void ParsesUspChangeCapacityDetail(string detail, int oldCap, int newCap, long delta)
        {
            var parsed = CapacityAuditDetail.TryParse(detail);

            Assert.IsNotNull(parsed);
            Assert.AreEqual(oldCap, parsed.OldCapacity);
            Assert.AreEqual(newCap, parsed.NewCapacity);
            Assert.AreEqual(delta, parsed.Delta);
        }

        [Test]
        public void DeltaDoesNotOverflowAtIntExtremes()
        {
            Assert.AreEqual(-4294967295L,
                CapacityAuditDetail.TryParse("2147483647 -> -2147483648").Delta);
        }

        // same cases as section 6 of db/checks/test_usp_GetCapacityAudit.sql
        [TestCase("12 -> 9 ")]
        [TestCase(" 12 -> 9")]
        [TestCase("12->9")]
        [TestCase("12  -> 9")]
        [TestCase("1 -> 2 -> 3")]
        [TestCase("a -> 9")]
        [TestCase("12 -> ")]
        [TestCase(" -> 9")]
        [TestCase("2147483648 -> 1")]
        [TestCase("1.5 -> 2")]
        [TestCase("00000000001 -> 1")]
        [TestCase("12 -> 9\n")]
        [TestCase("+1 -> 2")]
        [TestCase("1 - > 2")]
        [TestCase("capacity reset by DBA")]
        [TestCase("")]
        [TestCase(null)]
        public void ReturnsNullWhenDetailIsNotIntArrowInt(string detail)
        {
            Assert.IsNull(CapacityAuditDetail.TryParse(detail));
        }

        [Test]
        public void ParsesCapacitySyncRunDetail()
        {
            var d = CapacitySyncDetail.Parse("trigger=SCHEDULED status=OK checked=285 updated=112");

            Assert.AreEqual("SCHEDULED", d.Trigger);
            Assert.AreEqual("OK", d.Status);
            Assert.AreEqual(285, d.Checked);
            Assert.AreEqual(112, d.Updated);
        }

        [TestCase(null)]
        [TestCase("")]
        [TestCase("sync failed")]
        [TestCase("trigger=MANUAL status=FAILED checked=x")]
        public void SyncDetailToleratesMissingOrBadFields(string detail)
        {
            var d = CapacitySyncDetail.Parse(detail);

            Assert.IsNull(d.Checked);
            Assert.IsNull(d.Updated);
        }

        [Test]
        public void RangeDefaultsToLast30DaysAndFirstPage()
        {
            var r = CapacityAuditRange.Create(new DateTime(2026, 11, 11, 15, 30, 0), null, null, null);

            Assert.AreEqual(new DateTime(2026, 10, 12), r.From);
            Assert.AreEqual(new DateTime(2026, 11, 11), r.To);
            Assert.AreEqual(new DateTime(2026, 11, 12), r.ToExclusive);
            Assert.AreEqual(1, r.Page);
            Assert.AreEqual(50, CapacityAuditRange.PageSize);
        }

        [Test]
        public void RangeSwapsReversedDatesAndClampsPage()
        {
            var r = CapacityAuditRange.Create(new DateTime(2026, 11, 11), new DateTime(2026, 11, 5, 10, 0, 0), new DateTime(2026, 11, 1), 0);

            Assert.AreEqual(new DateTime(2026, 11, 1), r.From);
            Assert.AreEqual(new DateTime(2026, 11, 5), r.To);
            Assert.AreEqual(1, r.Page);
        }

        [TestCase(0, 1)]
        [TestCase(1, 1)]
        [TestCase(50, 1)]
        [TestCase(51, 2)]
        [TestCase(150, 3)]
        public void PageCountUses50RowPages(int total, int pages)
        {
            Assert.AreEqual(pages, CapacityAuditRange.PageCount(total));
        }
    }
}
