using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AssessmentDelivery.Data.DataClasses.Entities
{
    [Table("Booking")]
    public class Booking
    {
        public int BookingId { get; set; }
        public int SessionId { get; set; }
        public int CandidateId { get; set; }

        /// <summary>BOOKED, CANCELLED, WAITLISTED, ATTENDED</summary>
        [Required, StringLength(12)]
        public string Status { get; set; }

        [StringLength(10)]
        public string AccommodationCode { get; set; }

        /// <summary>PORTAL, PARTNER, IMPORT</summary>
        [Required, StringLength(10)]
        public string Channel { get; set; }

        [StringLength(40)]
        public string ExternalRef { get; set; }

        public DateTime BookedAt { get; set; }
        public DateTime? CancelledAt { get; set; }

        [StringLength(200)]
        public string CancelReason { get; set; }

        public DateTime LastModified { get; set; }

        public virtual ExamSession Session { get; set; }
        public virtual Candidate Candidate { get; set; }
    }
}
