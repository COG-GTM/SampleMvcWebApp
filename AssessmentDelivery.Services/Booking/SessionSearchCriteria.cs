using System;

namespace AssessmentDelivery.Services.Booking
{
    public class SessionSearchCriteria
    {
        public string ExamCode { get; set; }
        public string CenterCode { get; set; }
        public DateTime? From { get; set; }
        public DateTime? To { get; set; }
    }
}
