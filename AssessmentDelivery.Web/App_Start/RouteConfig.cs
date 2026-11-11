using System.Web.Mvc;
using System.Web.Routing;

namespace AssessmentDelivery.Web
{
    public class RouteConfig
    {
        public static void RegisterRoutes(RouteCollection routes)
        {
            routes.IgnoreRoute("{resource}.axd/{*pathInfo}");

            //capacity change history for a centre (MBA-3828)
            routes.MapRoute(
                name: "AdminCapacityAudit",
                url: "Admin/Centers/{id}/CapacityAudit",
                defaults: new { controller = "Admin", action = "CapacityAudit" },
                constraints: new { id = @"\d+" }
            );

            //exam-day roster download for proctors (CSV)
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
