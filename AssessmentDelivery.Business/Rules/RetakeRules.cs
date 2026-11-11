using System;
using System.Collections.Generic;
using System.Linq;

namespace AssessmentDelivery.Business.Rules
{
    /// <summary>
    /// Retake policy (exam owner agreement v3, 2012):
    ///  - a candidate who has passed may not sit the same exam again
    ///  - after a fail the candidate must wait Exam.RetakeWaitDays from the date of the last attempt
    /// </summary>
    public class RetakeRules : IRetakeRules
    {
        public RetakeDecision Check(IEnumerable<PriorAttempt> attempts, int retakeWaitDays, DateTime requestedDate)
        {
            var list = (attempts ?? Enumerable.Empty<PriorAttempt>()).ToList();

            if (list.Count == 0)
                return RetakeDecision.Allowed("No previous attempts", null);

            if (list.Any(a => a.Passed))
                return RetakeDecision.Blocked("Candidate has already passed this exam", null);

            var lastAttempt = list.Max(a => a.SessionDate).Date;
            var earliest = lastAttempt.AddDays(retakeWaitDays);

            if (requestedDate.Date < earliest)
                return RetakeDecision.Blocked(
                    string.Format("Retake wait period applies until {0:dd MMM yyyy}", earliest), earliest);

            return RetakeDecision.Allowed("Retake wait period has elapsed", earliest);
        }
    }
}
