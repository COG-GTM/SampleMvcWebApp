using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AssessmentDelivery.Data.DataClasses.Entities
{
    [Table("Candidate")]
    public class Candidate
    {
        public int CandidateId { get; set; }

        [Required, StringLength(12)]
        public string CandidateNumber { get; set; }

        [Required, StringLength(60)]
        public string FirstName { get; set; }

        [Required, StringLength(60)]
        public string LastName { get; set; }

        [Required, StringLength(200)]
        public string Email { get; set; }

        public DateTime? DateOfBirth { get; set; }

        [StringLength(10)]
        public string DefaultAccommodationCode { get; set; }

        public DateTime CreatedOn { get; set; }

        public virtual ICollection<Booking> Bookings { get; set; }

        [NotMapped]
        public string FullName
        {
            get { return FirstName + " " + LastName; }
        }
    }
}
