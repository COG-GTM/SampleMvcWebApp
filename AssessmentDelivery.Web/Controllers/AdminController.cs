using System;
using System.Web.Mvc;
using AssessmentDelivery.Business.Rules;
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

        public ActionResult Capacity(int id)
        {
            var model = BuildCapacityModel(id);
            if (model == null) return HttpNotFound();
            model.NewCapacity = model.Session.Capacity;
            return View(model);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public ActionResult Capacity(int id, int? newCapacity)
        {
            var model = BuildCapacityModel(id);
            if (model == null) return HttpNotFound();

            if (!newCapacity.HasValue)
            {
                ModelState.AddModelError("NewCapacity", "Enter the new capacity");
                return View(model);
            }
            try
            {
                _admin.ChangeCapacity(id, newCapacity.Value, "portal-admin");
            }
            catch (PortalException ex)
            {
                model.NewCapacity = newCapacity.Value;
                ModelState.AddModelError("", ex.Message);
                return View(model);
            }

            TempData["message"] = "Capacity updated";
            return RedirectToAction("Capacity", new { id });
        }

        public ActionResult CapacityAudit(int id, DateTime? from, DateTime? to, int? sessionId, int? page)
        {
            var center = _admin.GetCenter(id);
            if (center == null) return HttpNotFound();

            var range = CapacityAuditRange.Create(DateTime.Today, from, to, page);
            var audit = _admin.GetCapacityAudit(id, range.From, range.ToExclusive, sessionId, range.Page, CapacityAuditRange.PageSize);
            var syncRuns = _admin.ListCapacitySyncRuns(range.From, range.ToExclusive, CapacityAuditRange.PageSize + 1);
            var syncTruncated = syncRuns.Count > CapacityAuditRange.PageSize;
            if (syncTruncated) syncRuns.RemoveAt(syncRuns.Count - 1);
            return View(new CapacityAuditViewModel
            {
                Center = center,
                From = range.From,
                To = range.To,
                SessionId = sessionId,
                Page = range.Page,
                PageCount = CapacityAuditRange.PageCount(audit.TotalRows),
                TotalRows = audit.TotalRows,
                Rows = audit.Rows,
                Sessions = _admin.ListSessions(id),
                SyncRuns = syncRuns,
                SyncRunsTruncated = syncTruncated
            });
        }

        private CapacityViewModel BuildCapacityModel(int sessionId)
        {
            var row = _admin.GetSessionRow(sessionId);
            if (row == null) return null;
            return new CapacityViewModel
            {
                Session = row,
                CenterId = row.TestCenterId,
                CenterName = row.CenterName
            };
        }
    }
}
