using System.Runtime.CompilerServices;
using Autofac;
using AssessmentDelivery.Data.DataClasses;

[assembly: InternalsVisibleTo("AssessmentDelivery.Tests")]

namespace AssessmentDelivery.Data.Startup
{
    public class DataLayerModule : Module
    {
        protected override void Load(ContainerBuilder builder)
        {
            //Autowire the classes with interfaces
            builder.RegisterAssemblyTypes(GetType().Assembly).AsImplementedInterfaces();

            //set Entity Framework context to instance per lifetime scope (one context per web request)
            builder.RegisterType<AssessmentDeliveryDb>().AsSelf().InstancePerLifetimeScope();
        }
    }
}
