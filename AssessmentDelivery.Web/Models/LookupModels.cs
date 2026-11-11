using System;
using System.Collections.Generic;
using AssessmentDelivery.Business.Rules;
using AssessmentDelivery.Data.DataClasses.Entities;
using AssessmentDelivery.Services.Results;

namespace AssessmentDelivery.Web.Models
{
    public class EligibilityViewModel
    {
        public string CandidateNumber { get; set; }
        public string ExamCode { get; set; }
        public DateTime? Date { get; set; }
        public IList<Exam> Exams { get; set; }
        public RetakeDecision Decision { get; set; }
    }

    public class ResultsViewModel
    {
        public string CandidateNumber { get; set; }
        public Candidate Candidate { get; set; }
        public IList<CandidateResultItem> Results { get; set; }
    }
}
