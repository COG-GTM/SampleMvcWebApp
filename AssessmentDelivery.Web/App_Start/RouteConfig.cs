using System.Web.Mvc;
using System.Web.Routing;

namespace AssessmentDelivery.Web
{
    public class RouteConfig
    {
        public static void RegisterRoutes(RouteCollection routes)
        {
            routes.IgnoreRoute("{resource}.axd/{*pathInfo}");

            //centre admin capacity panel - URL agreed with ops (see docs/SYSTEM_OVERVIEW.md)
            routes.MapRoute(
                name: "AdminCapacity",
                url: "Admin/Sessions/Capacity/{id}",
                defaults: new { controller = "Admin", action = "Capacity" }
            );

            //proctor check-in roster download (read-only)
            routes.MapRoute(
                name: "AdminRoster",
                url: "Admin/Sessions/Roster/{id}",
                defaults: new { controller = "Admin", action = "Roster" }
            );

            routes.MapRoute(
                name: "Default",
                url: "{controller}/{action}/{id}",
                defaults: new { controller = "Home", action = "Index", id = UrlParameter.Optional }
            );
        }
    }
}
