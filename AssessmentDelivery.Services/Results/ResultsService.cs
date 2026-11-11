using System.Collections.Generic;
using System.Linq;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Services.Scheduling;

namespace AssessmentDelivery.Services.Results
{
    /// <summary>
    /// Candidate result history. Result and Exam are local; the session start time comes from
    /// Result.SessionStartsAt and the centre name from scheduling-service, which owns ExamSession.
    /// </summary>
    public class ResultsService : IResultsService
    {
        private readonly AssessmentDeliveryDb _db;
        private readonly ISchedulingClient _scheduling;

        public ResultsService(AssessmentDeliveryDb db, ISchedulingClient scheduling)
        {
            _db = db;
            _scheduling = scheduling;
        }

        public IList<CandidateResultItem> GetResultsForCandidate(string candidateNumber)
        {
            var number = (candidateNumber ?? "").Trim().ToUpper();
            var rows = _db.Results
                .Where(r => r.Candidate.CandidateNumber == number)
                .Select(r => new
                {
                    r.ResultId,
                    r.SessionId,
                    r.SessionStartsAt,
                    r.Exam.ExamCode,
                    r.Exam.Title,
                    r.Exam.PassingScore,
                    r.Score,
                    r.Passed,
                    r.ScoredAt
                }).ToList();

            var sessions = rows.Select(r => r.SessionId).Distinct()
                .ToDictionary(id => id, id => _scheduling.GetSession(id));

            return rows
                .Select(r =>
                {
                    var session = sessions[r.SessionId];
                    return new CandidateResultItem
                    {
                        ResultId = r.ResultId,
                        ExamCode = r.ExamCode,
                        ExamTitle = r.Title,
                        SessionDate = r.SessionStartsAt ?? (session == null ? default(System.DateTime) : session.StartsAt),
                        CenterName = session == null ? null : session.CenterName,
                        Score = r.Score,
                        PassingScore = r.PassingScore,
                        Passed = r.Passed,
                        ScoredAt = r.ScoredAt
                    };
                })
                .OrderByDescending(i => i.SessionDate)
                .ToList();
        }
    }
}
