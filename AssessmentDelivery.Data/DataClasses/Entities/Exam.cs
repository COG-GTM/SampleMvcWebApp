using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AssessmentDelivery.Data.DataClasses.Entities
{
    [Table("Exam")]
    public class Exam
    {
        public int ExamId { get; set; }

        [Required, StringLength(20)]
        public string ExamCode { get; set; }

        [Required, StringLength(200)]
        public string Title { get; set; }

        [Required, StringLength(100)]
        public string OwnerName { get; set; }

        public int DurationMinutes { get; set; }

        /// <summary>scaled score 0-1000</summary>
        public int PassingScore { get; set; }

        public int RetakeWaitDays { get; set; }
        public bool IsPublished { get; set; }
        public DateTime CreatedOn { get; set; }
    }
}
