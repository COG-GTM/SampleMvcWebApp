using System;

namespace AssessmentDelivery.Business.Rules
{
    public class RetakeDecision
    {
        public bool Eligible { get; set; }
        public string Reason { get; set; }
        public DateTime? EarliestRetakeDate { get; set; }

        public static RetakeDecision Allowed(string reason, DateTime? earliest)
        {
            return new RetakeDecision { Eligible = true, Reason = reason, EarliestRetakeDate = earliest };
        }

        public static RetakeDecision Blocked(string reason, DateTime? earliest)
        {
            return new RetakeDecision { Eligible = false, Reason = reason, EarliestRetakeDate = earliest };
        }
    }
}
