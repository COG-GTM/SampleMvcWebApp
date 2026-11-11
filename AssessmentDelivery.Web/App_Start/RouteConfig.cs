using System.Web.Mvc;
using System.Web.Routing;

namespace AssessmentDelivery.Web
{
    public class RouteConfig
    {
        public static void RegisterRoutes(RouteCollection routes)
        {
            routes.IgnoreRoute("{resource}.axd/{*pathInfo}");

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
