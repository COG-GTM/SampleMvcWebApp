using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AssessmentDelivery.Data.DataClasses.Entities
{
    [Table("TestCenter")]
    public class TestCenter
    {
        public int TestCenterId { get; set; }

        [Required, StringLength(10)]
        public string CenterCode { get; set; }

        [Required, StringLength(100)]
        [Display(Name = "Centre name")]
        public string Name { get; set; }

        [Required, StringLength(60)]
        public string City { get; set; }

        [Required, StringLength(20)]
        public string Region { get; set; }

        [Required, StringLength(40)]
        public string TimeZone { get; set; }

        [Display(Name = "Active")]
        public bool IsActive { get; set; }

        public DateTime CreatedOn { get; set; }

        public virtual ICollection<Room> Rooms { get; set; }
        public virtual ICollection<ExamSession> Sessions { get; set; }
    }
}
