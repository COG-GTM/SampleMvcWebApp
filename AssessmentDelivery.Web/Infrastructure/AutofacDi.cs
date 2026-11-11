using System.Reflection;
using Autofac;
using Autofac.Integration.Mvc;
using AssessmentDelivery.Services.Startup;

namespace AssessmentDelivery.Web.Infrastructure
{
    internal static class AutofacDi
    {
        private static IContainer _container;

        internal static IContainer SetupDependency(Assembly webAssembly)
        {
            var builder = new ContainerBuilder();

            //register the service layer, which then registers all other dependencies in the rest of the system
            builder.RegisterModule(new ServiceLayerModule());
            builder.RegisterControllers(webAssembly);

            _container = builder.Build();
            return _container;
        }
    }
}
