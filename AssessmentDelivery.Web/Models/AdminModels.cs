using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using AssessmentDelivery.Data.DataClasses.Entities;
using AssessmentDelivery.Services.Admin;

namespace AssessmentDelivery.Web.Models
{
    public class SessionsViewModel
    {
        public TestCenter Center { get; set; }
        public IList<AdminSessionRow> Sessions { get; set; }
    }

    public class CapacityViewModel
    {
        public AdminSessionRow Session { get; set; }
        public int CenterId { get; set; }
        public string CenterName { get; set; }

        [Required, Range(0, 500)]
        [Display(Name = "New capacity")]
        public int NewCapacity { get; set; }
    }

    public class CapacityAuditViewModel
    {
        public TestCenter Center { get; set; }
        public DateTime From { get; set; }
        public DateTime To { get; set; }
        public int? SessionId { get; set; }
        public int Page { get; set; }
        public int PageCount { get; set; }
        public int TotalRows { get; set; }
        public IList<CapacityAuditRow> Rows { get; set; }
        public IList<AdminSessionRow> Sessions { get; set; }
        public IList<CapacitySyncRunRow> SyncRuns { get; set; }
    }

    public class EditCenterViewModel
    {
        public int TestCenterId { get; set; }
        public string CenterCode { get; set; }

        [Required, StringLength(100)]
        public string Name { get; set; }

        [Display(Name = "Active")]
        public bool IsActive { get; set; }
    }
}
