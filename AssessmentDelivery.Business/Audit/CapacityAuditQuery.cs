using System;

namespace AssessmentDelivery.Business.Audit
{
    /// <summary>
    /// Filter/paging rules of the capacity audit page. Dates are whole days and inclusive: no "from" means
    /// 30 days before today, no "to" means open-ended (up to now); the query upper bound is the day after "to".
    /// </summary>
    public sealed class CapacityAuditQuery
    {
        public const int PageSize = 50;
        public const int DefaultDays = 30;

        public DateTime From { get; private set; }
        public DateTime? To { get; private set; }
        public int Page { get; private set; }

        /// <summary>Inclusive lower bound passed to usp_GetCapacityAudit / usp_GetCapacitySyncRuns.</summary>
        public DateTime QueryFrom { get { return From; } }
        /// <summary>Exclusive upper bound, null = no upper bound.</summary>
        public DateTime? QueryTo { get { return To.HasValue ? To.Value.AddDays(1) : (DateTime?)null; } }

        public static CapacityAuditQuery Create(DateTime? from, DateTime? to, int page, DateTime now)
        {
            var toDay = to.HasValue ? to.Value.Date : (DateTime?)null;
            var fromDay = from.HasValue ? from.Value.Date : (toDay ?? now.Date).AddDays(-DefaultDays);
            if (toDay.HasValue && fromDay > toDay.Value)
            {
                var swap = fromDay;
                fromDay = toDay.Value;
                toDay = swap;
            }
            return new CapacityAuditQuery { From = fromDay, To = toDay, Page = page < 1 ? 1 : page };
        }

        public static int PageCount(int totalRows)
        {
            return totalRows <= 0 ? 1 : (totalRows + PageSize - 1) / PageSize;
        }
    }
}
