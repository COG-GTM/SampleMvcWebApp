using System;
using System.Web.Mvc;
using AssessmentDelivery.Services.Common;
using AssessmentDelivery.Services.Eligibility;
using AssessmentDelivery.Web.Models;

namespace AssessmentDelivery.Web.Controllers
{
    public class EligibilityController : Controller
    {
        private readonly IEligibilityService _eligibility;

        public EligibilityController(IEligibilityService eligibility)
        {
            _eligibility = eligibility;
        }

        // GET /Eligibility?candidateNumber=&examCode=&date=
        public ActionResult Index(string candidateNumber, string examCode, DateTime? date)
        {
            var model = new EligibilityViewModel
            {
                CandidateNumber = candidateNumber,
                ExamCode = examCode,
                Date = date ?? DateTime.Today,
                Exams = _eligibility.ListPublishedExams()
            };

            if (!string.IsNullOrWhiteSpace(candidateNumber) && !string.IsNullOrWhiteSpace(examCode))
            {
                try
                {
                    model.Decision = _eligibility.Check(candidateNumber, examCode, model.Date.Value);
                }
                catch (PortalException ex)
                {
                    ModelState.AddModelError("", ex.Message);
                }
            }
            return View(model);
        }
    }
}
