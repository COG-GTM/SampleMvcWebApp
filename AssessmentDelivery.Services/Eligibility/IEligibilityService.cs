using System;
using System.Collections.Generic;
using AssessmentDelivery.Business.Rules;
using AssessmentDelivery.Data.DataClasses.Entities;

namespace AssessmentDelivery.Services.Eligibility
{
    public interface IEligibilityService
    {
        RetakeDecision Check(string candidateNumber, string examCode, DateTime date);
        IList<Exam> ListExams();
    }
}
