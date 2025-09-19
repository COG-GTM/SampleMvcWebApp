using Microsoft.AspNetCore.Mvc;

namespace SampleWebApp.Core.Controllers;

public class HomeController : Controller
{
    public IActionResult Index()
    {
        return Content("SampleWebApp.Core - .NET 6 Web Application is running!");
    }

    public IActionResult Error()
    {
        return Content("An error occurred.");
    }
}
