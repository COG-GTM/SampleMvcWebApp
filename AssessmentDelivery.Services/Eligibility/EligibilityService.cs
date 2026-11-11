using System;
using System.Collections.Generic;
using System.Data.Entity;
using System.Linq;
using AssessmentDelivery.Business.Rules;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Data.DataClasses.Entities;
using AssessmentDelivery.Services.Common;

namespace AssessmentDelivery.Services.Eligibility
{
    public class EligibilityService : IEligibilityService
    {
        private readonly AssessmentDeliveryDb _db;
        private readonly IRetakeRules _rules;

        public EligibilityService(AssessmentDeliveryDb db, IRetakeRules rules)
        {
            _db = db;
            _rules = rules;
        }

        public IList<Exam> ListPublishedExams()
        {
            return _db.Exams.AsNoTracking().Where(e => e.IsPublished).OrderBy(e => e.ExamCode).ToList();
        }

        public RetakeDecision Check(string candidateNumber, string examCode, DateTime date)
        {
            var number = (candidateNumber ?? "").Trim().ToUpper();
            var code = (examCode ?? "").Trim().ToUpper();

            var candidate = _db.Candidates.SingleOrDefault(c => c.CandidateNumber == number);
            if (candidate == null)
                throw new PortalException("Candidate number " + candidateNumber + " was not found.");
            var exam = _db.Exams.SingleOrDefault(e => e.ExamCode == code);
            if (exam == null)
                throw new PortalException("Exam " + examCode + " was not found.");

            var attempts = _db.Results
                .Where(r => r.CandidateId == candidate.CandidateId && r.ExamId == exam.ExamId)
                .Select(r => new PriorAttempt { SessionDate = r.Session.StartsAt, Passed = r.Passed, Score = r.Score })
                .ToList();

            return _rules.Check(attempts, exam.RetakeWaitDays, date);
        }
    }
}
