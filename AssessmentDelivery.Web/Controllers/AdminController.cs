using System;
using System.Net;
using System.Web.Mvc;
using AssessmentDelivery.Business.Rosters;
using AssessmentDelivery.Services.Admin;
using AssessmentDelivery.Services.Common;
using AssessmentDelivery.Web.Models;

namespace AssessmentDelivery.Web.Controllers
{
    public class AdminController : Controller
    {
        private readonly IAdminService _admin;

        public AdminController(IAdminService admin)
        {
            _admin = admin;
        }

        public ActionResult Index()
        {
            return RedirectToAction("Centers");
        }

        public ActionResult Centers()
        {
            return View(_admin.ListCenters());
        }

        public ActionResult EditCenter(int id)
        {
            var c = _admin.GetCenter(id);
            if (c == null) return HttpNotFound();
            return View(new EditCenterViewModel { TestCenterId = c.TestCenterId, CenterCode = c.CenterCode, Name = c.Name, IsActive = c.IsActive });
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public ActionResult EditCenter(EditCenterViewModel model)
        {
            if (!ModelState.IsValid) return View(model);
            try
            {
                _admin.UpdateCenter(model.TestCenterId, model.Name, model.IsActive);
            }
            catch (PortalException ex)
            {
                ModelState.AddModelError("", ex.Message);
                return View(model);
            }
            TempData["message"] = "Centre saved";
            return RedirectToAction("Centers");
        }

        public ActionResult Sessions(int? centerId)
        {
            if (!centerId.HasValue) return RedirectToAction("Centers");
            var center = _admin.GetCenter(centerId.Value);
            if (center == null) return HttpNotFound();
            return View(new SessionsViewModel { Center = center, Sessions = _admin.ListSessions(center.TestCenterId) });
        }

        /// <summary>
        /// GET /Admin/Sessions/Roster/{id}?format=csv - proctor check-in roster, read-only
        /// </summary>
        [HttpGet]
        public ActionResult Roster(int id, string format)
        {
            if (!string.IsNullOrEmpty(format) && !string.Equals(format, "csv", StringComparison.OrdinalIgnoreCase))
                return new HttpStatusCodeResult(HttpStatusCode.BadRequest, "Unsupported roster format");

            var roster = _admin.GetSessionRoster(id);
            if (roster == null) return HttpNotFound();

            var fileName = RosterCsvBuilder.BuildFileName(roster.CenterCode, roster.ExamCode, DateTime.Now);
            return File(RosterCsvBuilder.Build(roster.Lines), "text/csv", fileName);
        }
    }
}
