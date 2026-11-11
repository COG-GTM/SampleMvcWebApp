using System;

namespace AssessmentDelivery.Business.Rules
{
    /// <summary>
    /// One scored attempt at an exam (a Result row plus the date of the session it was sat in).
    /// </summary>
    public class PriorAttempt
    {
        public DateTime SessionDate { get; set; }
        public bool Passed { get; set; }
        public int Score { get; set; }
    }
}
