using System;
using System.Collections.Generic;

namespace AssessmentDelivery.Business.Rules
{
    public interface IRetakeRules
    {
        RetakeDecision Check(IEnumerable<PriorAttempt> attempts, int retakeWaitDays, DateTime requestedDate);
    }
}
