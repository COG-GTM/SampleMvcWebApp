using System.Web.Mvc;
using AssessmentDelivery.Services.Results;
using AssessmentDelivery.Web.Models;

namespace AssessmentDelivery.Web.Controllers
{
    public class ResultsController : Controller
    {
        private readonly IResultsService _results;

        public ResultsController(IResultsService results)
        {
            _results = results;
        }

        // GET /Results?candidateNumber=
        public ActionResult Index(string candidateNumber)
        {
            var model = new ResultsViewModel { CandidateNumber = candidateNumber };
            if (!string.IsNullOrWhiteSpace(candidateNumber))
            {
                model.Candidate = _results.FindCandidate(candidateNumber);
                if (model.Candidate == null)
                    ModelState.AddModelError("", "Candidate number " + candidateNumber + " was not found.");
                else
                    model.Results = _results.GetResultsForCandidate(candidateNumber);
            }
            return View(model);
        }
    }
}
