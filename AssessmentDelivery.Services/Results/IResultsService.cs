using System.Collections.Generic;

namespace AssessmentDelivery.Services.Results
{
    public interface IResultsService
    {
        IList<CandidateResultItem> GetResultsForCandidate(string candidateNumber);
    }
}
