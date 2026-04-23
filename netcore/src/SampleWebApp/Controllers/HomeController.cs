using Microsoft.AspNetCore.Mvc;
using SampleWebApp.Models;

namespace SampleWebApp.Controllers;

/// <summary>
/// .NET 6 migration of the original MVC5 <c>HomeController</c>. Action
/// signatures and view names are preserved so existing routes continue to work.
/// </summary>
public class HomeController : Controller
{
    private readonly IInternalsInfoProvider _internalsInfoProvider;

    public HomeController(IInternalsInfoProvider internalsInfoProvider)
    {
        _internalsInfoProvider = internalsInfoProvider
            ?? throw new ArgumentNullException(nameof(internalsInfoProvider));
    }

    public IActionResult Index() => View();

    public IActionResult About()
    {
        ViewBag.Message = "Your application description page.";
        return View();
    }

    public IActionResult Contact() => View();

    public IActionResult Internals() => View(new InternalsInfo(_internalsInfoProvider));

    public IActionResult CodeView() => View();
}
