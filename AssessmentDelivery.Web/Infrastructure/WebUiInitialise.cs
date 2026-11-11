using System;
using System.Configuration;
using System.Diagnostics;
using System.Web;
using System.Web.Mvc;
using Autofac.Integration.Mvc;
using AssessmentDelivery.Services.Startup;

namespace AssessmentDelivery.Web.Infrastructure
{
    public enum HostTypes { NotSet, LocalHost, OnPrem, Azure };

    public static class WebUiInitialise
    {
        public const string DatabaseConnectionStringName = "AssessmentDelivery";

        /// <summary>
        /// Where we are running (set from appSettings "Portal.HostType")
        /// </summary>
        public static HostTypes HostType { get; private set; }

        /// <summary>
        /// This should be called at Startup
        /// </summary>
        public static void InitialiseThis(HttpApplication application)
        {
            HostType = DecodeHostType(ConfigurationManager.AppSettings["Portal.HostType"]);
            Trace.TraceInformation("Assessment Delivery Portal starting, host type {0}", HostType);

            //This runs the ServiceLayer initialise, whose job it is to initialise any of the lower layers
            //NOTE: This MUST come before the setup of the DI
            ServiceLayerInitialise.InitialiseThis(HostType == HostTypes.Azure);

            //This sets up the Autofac container for all levels in the program
            var container = AutofacDi.SetupDependency(typeof(WebUiInitialise).Assembly);

            //// Set the dependency resolver for MVC.
            DependencyResolver.SetResolver(new AutofacDependencyResolver(container));
        }

        private static HostTypes DecodeHostType(string hostTypeString)
        {
            HostTypes hostType;
            Enum.TryParse(hostTypeString, true, out hostType);
            return hostType;
        }
    }
}
