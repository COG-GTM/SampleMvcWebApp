using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace AssessmentDelivery.Data.DataClasses.Entities
{
    [Table("AuditLog")]
    public class AuditLog
    {
        [Key]
        public long AuditId { get; set; }

        [Required, StringLength(40)]
        public string EntityName { get; set; }

        public int EntityId { get; set; }

        [Required, StringLength(30)]
        public string Action { get; set; }

        [StringLength(1000)]
        public string Detail { get; set; }

        [Required, StringLength(60)]
        public string Actor { get; set; }

        public DateTime LoggedAt { get; set; }
    }
}
