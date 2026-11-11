using System;
using AssessmentDelivery.Business.Rules;

namespace AssessmentDelivery.Services.Eligibility
{
    public interface IEligibilityService
    {
        RetakeDecision Check(string candidateNumber, string examCode, DateTime date);
    }
}
