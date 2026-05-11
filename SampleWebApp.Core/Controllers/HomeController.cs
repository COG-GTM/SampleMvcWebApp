using Microsoft.AspNetCore.Mvc;
using SampleWebApp.Core.Models;

namespace SampleWebApp.Core.Controllers
{
    public class HomeController : Controller
    {
        public IActionResult Index()
        {
            return View();
        }

        public IActionResult About()
        {
            ViewBag.Message = "Your application description page.";

            return View();
        }

        public IActionResult Contact()
        {
            return View();
        }

        public IActionResult Internals()
        {
            return View(new InternalsInfo());
        }

        public IActionResult CodeView()
        {
            return View();
        }
    }
}
