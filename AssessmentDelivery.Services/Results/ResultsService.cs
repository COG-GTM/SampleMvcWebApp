using System.Collections.Generic;
using System.Data.Entity;
using System.Linq;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Data.DataClasses.Entities;

namespace AssessmentDelivery.Services.Results
{
    public class ResultsService : IResultsService
    {
        private readonly AssessmentDeliveryDb _db;

        public ResultsService(AssessmentDeliveryDb db)
        {
            _db = db;
        }

        public Candidate FindCandidate(string candidateNumber)
        {
            if (string.IsNullOrWhiteSpace(candidateNumber)) return null;
            var number = candidateNumber.Trim().ToUpper();
            return _db.Candidates.AsNoTracking().SingleOrDefault(c => c.CandidateNumber == number);
        }

        public IList<CandidateResultItem> GetResultsForCandidate(string candidateNumber)
        {
            var number = (candidateNumber ?? "").Trim().ToUpper();
            return _db.Results
                .Where(r => r.Candidate.CandidateNumber == number)
                .OrderByDescending(r => r.Session.StartsAt)
                .Select(r => new CandidateResultItem
                {
                    ResultId = r.ResultId,
                    ExamCode = r.Exam.ExamCode,
                    ExamTitle = r.Exam.Title,
                    SessionDate = r.Session.StartsAt,
                    CenterName = r.Session.TestCenter.Name,
                    Score = r.Score,
                    PassingScore = r.Exam.PassingScore,
                    Passed = r.Passed,
                    ScoredAt = r.ScoredAt
                }).ToList();
        }
    }
}
