using System.Web.Mvc;

namespace AssessmentDelivery.Web.Controllers
{
    public class HomeController : Controller
    {
        public ActionResult Index()
        {
            //ops asked for the portal to open on the centre list (2014)
            return RedirectToAction("Centers", "Admin");
        }
    }
}
