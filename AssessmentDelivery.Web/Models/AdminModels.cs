using System;
using System.Collections.Generic;
using System.Web.Mvc;
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

    public class CapacityAuditViewModel
    {
        public TestCenter Center { get; set; }
        public DateTime From { get; set; }
        /// <summary>Inclusive last day; null = up to now.</summary>
        public DateTime? To { get; set; }
        public int? SessionId { get; set; }
        public int Page { get; set; }
        public int PageSize { get; set; }
        public int TotalRows { get; set; }
        public int PageCount { get; set; }
        public IList<CapacityAuditRow> Rows { get; set; }
        public IList<CapacitySyncRunRow> SyncRuns { get; set; }
        public IList<SelectListItem> Sessions { get; set; }

        public object RouteValues(int page)
        {
            return new
            {
                id = Center.TestCenterId,
                from = From.ToString("yyyy-MM-dd"),
                to = To.HasValue ? To.Value.ToString("yyyy-MM-dd") : null,
                sessionId = SessionId,
                page
            };
        }
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
