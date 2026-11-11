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

            //capacity change history for a centre (MBA-3828)
            routes.MapRoute(
                name: "AdminCapacityAudit",
                url: "Admin/Centers/{id}/CapacityAudit",
                defaults: new { controller = "Admin", action = "CapacityAudit" },
                constraints: new { id = @"\d+" }
            );

            routes.MapRoute(
                name: "Default",
                url: "{controller}/{action}/{id}",
                defaults: new { controller = "Home", action = "Index", id = UrlParameter.Optional }
            );
        }
    }
}
