using System;

namespace AssessmentDelivery.Services.Results
{
    public class CandidateResultItem
    {
        public int ResultId { get; set; }
        public string ExamCode { get; set; }
        public string ExamTitle { get; set; }
        public DateTime SessionDate { get; set; }
        public string CenterName { get; set; }
        public int Score { get; set; }
        public int PassingScore { get; set; }
        public bool Passed { get; set; }
        public DateTime ScoredAt { get; set; }
    }
}
