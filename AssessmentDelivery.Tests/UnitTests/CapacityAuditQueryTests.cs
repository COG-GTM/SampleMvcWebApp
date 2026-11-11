using System;
using AssessmentDelivery.Business.Audit;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    [TestFixture]
    public class CapacityAuditQueryTests
    {
        private static readonly DateTime Now = new DateTime(2026, 11, 11, 14, 30, 0);

        [Test]
        public void DefaultsToLast30DaysOpenEnded()
        {
            var q = CapacityAuditQuery.Create(null, null, 1, Now);
            Assert.AreEqual(new DateTime(2026, 10, 12), q.From);
            Assert.IsNull(q.To);
            Assert.AreEqual(new DateTime(2026, 10, 12), q.QueryFrom);
            Assert.IsNull(q.QueryTo);
            Assert.AreEqual(1, q.Page);
        }

        [Test]
        public void ToIsInclusiveWholeDay()
        {
            var q = CapacityAuditQuery.Create(new DateTime(2026, 11, 1, 9, 0, 0), new DateTime(2026, 11, 5, 8, 0, 0), 2, Now);
            Assert.AreEqual(new DateTime(2026, 11, 1), q.QueryFrom);
            Assert.AreEqual(new DateTime(2026, 11, 5), q.To);
            Assert.AreEqual(new DateTime(2026, 11, 6), q.QueryTo);
            Assert.AreEqual(2, q.Page);
        }

        [Test]
        public void OnlyToGiven_FromIs30DaysBeforeTo()
        {
            var q = CapacityAuditQuery.Create(null, new DateTime(2026, 6, 30), 1, Now);
            Assert.AreEqual(new DateTime(2026, 5, 31), q.From);
        }

        [Test]
        public void SwapsReversedRange()
        {
            var q = CapacityAuditQuery.Create(new DateTime(2026, 11, 9), new DateTime(2026, 11, 2), 1, Now);
            Assert.AreEqual(new DateTime(2026, 11, 2), q.From);
            Assert.AreEqual(new DateTime(2026, 11, 9), q.To);
        }

        [TestCase(0, 1)]
        [TestCase(-5, 1)]
        [TestCase(3, 3)]
        public void ClampsPage(int page, int expected)
        {
            Assert.AreEqual(expected, CapacityAuditQuery.Create(null, null, page, Now).Page);
        }

        [TestCase(0, 1)]
        [TestCase(1, 1)]
        [TestCase(50, 1)]
        [TestCase(51, 2)]
        [TestCase(100, 2)]
        [TestCase(101, 3)]
        public void PagesOf50(int totalRows, int pages)
        {
            Assert.AreEqual(50, CapacityAuditQuery.PageSize);
            Assert.AreEqual(pages, CapacityAuditQuery.PageCount(totalRows));
        }
    }
}
