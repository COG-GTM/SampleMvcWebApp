using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AssessmentDelivery.Data.DataClasses.Entities
{
    [Table("Waitlist")]
    public class WaitlistEntry
    {
        [Key]
        public int WaitlistId { get; set; }
        public int SessionId { get; set; }
        public int CandidateId { get; set; }
        public int BookingId { get; set; }
        public int Position { get; set; }
        public DateTime AddedAt { get; set; }

        public virtual ExamSession Session { get; set; }
    }
}
