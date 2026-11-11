using System;
using System.Configuration;
using System.Reflection;
using System.Web.Mvc;

namespace AssessmentDelivery.Web.Infrastructure
{
    public static class PortalHtmlExtensions
    {
        /// <summary>
        /// Coloured label for session / booking status codes
        /// </summary>
        public static MvcHtmlString StatusPill(this HtmlHelper html, string status)
        {
            var css = "pill-default";
            switch ((status ?? "").ToUpperInvariant())
            {
                case "OPEN":
                case "BOOKED":
                case "PASS":
                    css = "pill-open"; break;
                case "CLOSED":
                case "WAITLISTED":
                    css = "pill-closed"; break;
                case "DELIVERED":
                case "ATTENDED":
                    css = "pill-delivered"; break;
                case "CANCELLED":
                case "FAIL":
                    css = "pill-cancelled"; break;
            }
            var tag = new TagBuilder("span");
            tag.AddCssClass("pill " + css);
            tag.SetInnerText(status ?? "");
            return MvcHtmlString.Create(tag.ToString());
        }

        public static string PortalVersion(this HtmlHelper html)
        {
            var v = Assembly.GetExecutingAssembly().GetName().Version;
            return string.Format("{0}.{1}.{2} (build {3})", v.Major, v.Minor, v.Build,
                ConfigurationManager.AppSettings["Portal.BuildNumber"] ?? "local");
        }

        public static string EnvironmentName(this HtmlHelper html)
        {
            return ConfigurationManager.AppSettings["Portal.EnvironmentName"] ?? "DEV";
        }
    }
}
