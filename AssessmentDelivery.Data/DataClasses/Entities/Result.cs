using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AssessmentDelivery.Data.DataClasses.Entities
{
    [Table("Result")]
    public class Result
    {
        public int ResultId { get; set; }
        public int BookingId { get; set; }
        public int CandidateId { get; set; }
        public int ExamId { get; set; }
        public int SessionId { get; set; }
        public int Score { get; set; }
        public bool Passed { get; set; }
        public DateTime ScoredAt { get; set; }

        [StringLength(200)]
        public string SourceFile { get; set; }

        public DateTime ReceivedAt { get; set; }

        /// <summary>Copy of the session start time taken at ingest (016_result_session_starts_at.sql).</summary>
        public DateTime? SessionStartsAt { get; set; }

        public virtual Booking Booking { get; set; }
        public virtual Candidate Candidate { get; set; }
        public virtual Exam Exam { get; set; }
        public virtual ExamSession Session { get; set; }
    }
}
