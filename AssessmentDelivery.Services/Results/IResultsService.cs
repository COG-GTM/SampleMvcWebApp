using System.Collections.Generic;
using AssessmentDelivery.Data.DataClasses.Entities;

namespace AssessmentDelivery.Services.Results
{
    public interface IResultsService
    {
        Candidate FindCandidate(string candidateNumber);
        IList<CandidateResultItem> GetResultsForCandidate(string candidateNumber);
    }
}
