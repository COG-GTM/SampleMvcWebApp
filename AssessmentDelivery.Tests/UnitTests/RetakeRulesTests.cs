using System;
using System.Collections.Generic;
using AssessmentDelivery.Business.Rules;
using NUnit.Framework;

namespace AssessmentDelivery.Tests.UnitTests
{
    [TestFixture]
    public class RetakeRulesTests
    {
        private readonly RetakeRules _rules = new RetakeRules();

        [Test]
        public void FirstTimeCandidateIsEligible()
        {
            var decision = _rules.Check(new List<PriorAttempt>(), 30, new DateTime(2016, 3, 1));

            Assert.IsTrue(decision.Eligible);
            Assert.IsNull(decision.EarliestRetakeDate);
        }

        [Test]
        public void PassedResultBlocksRetake()
        {
            var attempts = new List<PriorAttempt>
            {
                new PriorAttempt { SessionDate = new DateTime(2015, 6, 10), Passed = false, Score = 610 },
                new PriorAttempt { SessionDate = new DateTime(2015, 9, 2), Passed = true, Score = 802 }
            };

            var decision = _rules.Check(attempts, 30, new DateTime(2016, 3, 1));

            Assert.IsFalse(decision.Eligible);
            StringAssert.Contains("already passed", decision.Reason);
        }
    }
}
