using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AssessmentDelivery.Data.DataClasses.Entities
{
    [Table("ExamSession")]
    public class ExamSession
    {
        [Key]
        public int SessionId { get; set; }

        public int ExamId { get; set; }
        public int TestCenterId { get; set; }
        public int RoomId { get; set; }

        /// <summary>centre local time</summary>
        public DateTime StartsAt { get; set; }

        public int Capacity { get; set; }
        public int AccommodationCapacity { get; set; }

        /// <summary>
        /// Denormalised counter, read-only here. Scheduling-service owns seat changes and the
        /// relay projects them into AssessmentDelivery (see db/README.md).
        /// </summary>
        public int SeatsAvailable { get; set; }

        [Required, StringLength(12)]
        public string Status { get; set; }

        public DateTime LastModified { get; set; }

        public virtual Exam Exam { get; set; }
        public virtual TestCenter TestCenter { get; set; }
        public virtual Room Room { get; set; }
        public virtual ICollection<Booking> Bookings { get; set; }
        public virtual ICollection<WaitlistEntry> Waitlist { get; set; }
    }
}
