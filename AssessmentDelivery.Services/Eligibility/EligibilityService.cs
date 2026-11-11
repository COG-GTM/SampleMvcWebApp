using System;
using System.Collections.Generic;
using System.Configuration;
using System.Globalization;
using System.Linq;
using System.Net;
using System.Text;
using System.Web.Script.Serialization;
using AssessmentDelivery.Business.Rules;
using AssessmentDelivery.Data.DataClasses;
using AssessmentDelivery.Services.Common;

namespace AssessmentDelivery.Services.Eligibility
{
    /// <summary>
    /// Portal retake check, evaluated by scheduling-service with policy PORTAL_CALENDAR_DAYS
    /// (docs/to-be/adr-001-retake-eligibility-and-attendance.md). The calendar-day rule (RetakeRules) moved there
    /// unchanged (HB-07); the portal keeps its Candidate/Exam lookups and error messages.
    /// </summary>
    public class EligibilityService : IEligibilityService
    {
        private readonly AssessmentDeliveryDb _db;
        private readonly string _baseUrl;

        public EligibilityService(AssessmentDeliveryDb db)
        {
            _db = db;
            _baseUrl = (ConfigurationManager.AppSettings["Scheduling.BaseUrl"] ?? "http://localhost:8084/api/v1").TrimEnd('/');
        }

        public RetakeDecision Check(string candidateNumber, string examCode, DateTime date)
        {
            var number = (candidateNumber ?? "").Trim().ToUpper();
            var code = (examCode ?? "").Trim().ToUpper();

            if (!_db.Candidates.Any(c => c.CandidateNumber == number))
                throw new PortalException("Candidate number " + candidateNumber + " was not found.");
            if (!_db.Exams.Any(e => e.ExamCode == code))
                throw new PortalException("Exam " + examCode + " was not found.");

            var url = string.Format("{0}/eligibility/retake?candidateNumber={1}&examCode={2}&date={3:yyyy-MM-dd}&policy=PORTAL_CALENDAR_DAYS",
                _baseUrl, Uri.EscapeDataString(number), Uri.EscapeDataString(code), date);
            string body;
            try
            {
                using (var client = new WebClient { Encoding = Encoding.UTF8 })
                {
                    client.Headers[HttpRequestHeader.Accept] = "application/json";
                    body = client.DownloadString(url);
                }
            }
            catch (WebException ex)
            {
                throw new PortalException("Retake eligibility is temporarily unavailable, please try again later. (" + ex.Message + ")");
            }

            var d = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(body);
            var earliest = d.ContainsKey("earliestRetakeDate") && d["earliestRetakeDate"] != null
                ? DateTime.ParseExact((string)d["earliestRetakeDate"], "yyyy-MM-dd", CultureInfo.InvariantCulture)
                : (DateTime?)null;
            var eligible = d.ContainsKey("eligible") && d["eligible"] is bool && (bool)d["eligible"];
            var reason = d.ContainsKey("reason") ? (string)d["reason"] : null;
            return eligible ? RetakeDecision.Allowed(reason, earliest) : RetakeDecision.Blocked(reason, earliest);
        }
    }
}
