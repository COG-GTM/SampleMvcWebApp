using System;
using System.Linq;
using System.Web.Mvc;
using AssessmentDelivery.Business.Audit;
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

        /// <summary>GET /Admin/Centers/{id}/CapacityAudit?from=&amp;to=&amp;sessionId=&amp;page= (read-only)</summary>
        [HttpGet]
        public ActionResult CapacityAudit(int id, DateTime? from, DateTime? to, int? sessionId, int page = 1)
        {
            var center = _admin.GetCenter(id);
            if (center == null) return HttpNotFound();

            var query = CapacityAuditQuery.Create(from, to, page, DateTime.Now);
            var rows = _admin.GetCapacityAudit(center.TestCenterId, query.QueryFrom, query.QueryTo, sessionId, query.Page);
            var totalRows = rows.Count > 0 ? rows[0].TotalRows : 0;
            return View(new CapacityAuditViewModel
            {
                Center = center,
                From = query.From,
                To = query.To,
                SessionId = sessionId,
                Page = query.Page,
                PageSize = CapacityAuditQuery.PageSize,
                TotalRows = totalRows,
                PageCount = CapacityAuditQuery.PageCount(totalRows),
                Rows = rows,
                SyncRuns = _admin.ListCapacitySyncRuns(query.QueryFrom, query.QueryTo),
                Sessions = _admin.ListSessions(center.TestCenterId)
                    .OrderBy(s => s.StartsAt)
                    .Select(s => new SelectListItem
                    {
                        Value = s.SessionId.ToString(),
                        Text = "#" + s.SessionId + " " + s.ExamCode + " " + s.StartsAt.ToString("dd MMM yyyy HH:mm"),
                        Selected = s.SessionId == sessionId
                    }).ToList()
            });
        }
    }
}
