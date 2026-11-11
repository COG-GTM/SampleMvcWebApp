using System.Web.Mvc;
using AssessmentDelivery.Services.Booking;
using AssessmentDelivery.Services.Common;
using AssessmentDelivery.Web.Models;

namespace AssessmentDelivery.Web.Controllers
{
    public class BookingController : Controller
    {
        private readonly IBookingService _booking;

        public BookingController(IBookingService booking)
        {
            _booking = booking;
        }

        // GET /Booking - find open sessions
        public ActionResult Index(FindSessionsViewModel model)
        {
            model = model ?? new FindSessionsViewModel();
            model.Exams = _booking.ListExams();
            model.Centers = _booking.ListActiveCenters();
            model.Searched = Request.QueryString.Count > 0;
            if (model.Searched)
            {
                model.Results = _booking.FindOpenSessions(new SessionSearchCriteria
                {
                    ExamCode = model.ExamCode,
                    CenterCode = model.CenterCode,
                    From = model.From,
                    To = model.To
                });
            }
            return View(model);
        }

        public ActionResult Book(int? sessionId, string candidateNumber)
        {
            if (!sessionId.HasValue) return RedirectToAction("Index");
            var session = _booking.GetSession(sessionId.Value);
            if (session == null) return HttpNotFound();
            return View(new BookViewModel { SessionId = session.SessionId, Session = session, CandidateNumber = candidateNumber });
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public ActionResult Book(BookViewModel model)
        {
            model.Session = _booking.GetSession(model.SessionId);
            if (model.Session == null) return HttpNotFound();
            if (!ModelState.IsValid) return View(model);

            try
            {
                var outcome = _booking.Book(model.SessionId, model.CandidateNumber, model.AccommodationCode);
                TempData["message"] = outcome.Status == "WAITLISTED"
                    ? "Session is full - booking " + outcome.BookingId + " has been placed on the waitlist."
                    : "Booking " + outcome.BookingId + " confirmed.";
            }
            catch (PortalException ex)
            {
                ModelState.AddModelError("", ex.Message);
                return View(model);
            }
            return RedirectToAction("MyBookings", new { candidateNumber = model.CandidateNumber.Trim().ToUpper() });
        }

        public ActionResult MyBookings(string candidateNumber)
        {
            var model = new MyBookingsViewModel { CandidateNumber = candidateNumber };
            if (!string.IsNullOrWhiteSpace(candidateNumber))
            {
                model.Candidate = _booking.FindCandidate(candidateNumber);
                if (model.Candidate == null)
                    ModelState.AddModelError("", "Candidate number " + candidateNumber + " was not found.");
                else
                    model.Bookings = _booking.GetBookingsForCandidate(candidateNumber);
            }
            return View(model);
        }

        public ActionResult Cancel(int id, string candidateNumber)
        {
            var booking = _booking.GetBooking(id);
            if (booking == null) return HttpNotFound();
            return View(new CancelViewModel { Booking = booking, CandidateNumber = candidateNumber });
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public ActionResult Cancel(int id, CancelViewModel model)
        {
            try
            {
                _booking.Cancel(id, model.Reason);
            }
            catch (PortalException ex)
            {
                model.Booking = _booking.GetBooking(id);
                ModelState.AddModelError("", ex.Message);
                return View(model);
            }
            TempData["message"] = "Booking " + id + " cancelled.";
            return RedirectToAction("MyBookings", new { candidateNumber = model.CandidateNumber });
        }

        public ActionResult Reschedule(int id, string candidateNumber)
        {
            var booking = _booking.GetBooking(id);
            if (booking == null) return HttpNotFound();
            return View(new RescheduleViewModel { Booking = booking, CandidateNumber = candidateNumber, Targets = _booking.FindRescheduleTargets(id) });
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public ActionResult Reschedule(int id, RescheduleViewModel model)
        {
            if (!model.NewSessionId.HasValue)
                ModelState.AddModelError("NewSessionId", "Choose the new session");
            else
            {
                try
                {
                    var outcome = _booking.Reschedule(id, model.NewSessionId.Value);
                    TempData["message"] = "Booking moved. New booking " + outcome.BookingId + " (" + outcome.Status + ").";
                    return RedirectToAction("MyBookings", new { candidateNumber = model.CandidateNumber });
                }
                catch (PortalException ex)
                {
                    ModelState.AddModelError("", ex.Message);
                }
            }
            model.Booking = _booking.GetBooking(id);
            model.Targets = _booking.FindRescheduleTargets(id);
            return View(model);
        }
    }
}
