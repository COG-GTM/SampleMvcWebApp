using Autofac;
using AssessmentDelivery.Business.Startup;
using AssessmentDelivery.Data.Startup;
using AssessmentDelivery.Services.Scheduling;

namespace AssessmentDelivery.Services.Startup
{
    public class ServiceLayerModule : Module
    {
        /// <summary>
        /// This registers all items in service layer and below
        /// </summary>
        protected override void Load(ContainerBuilder builder)
        {
            //Now register the lower layers
            builder.RegisterModule(new DataLayerModule());
            builder.RegisterModule(new BizLayerModule());

            //---------------------------
            //Register service layer: autowire all
            builder.RegisterAssemblyTypes(GetType().Assembly).AsImplementedInterfaces();

            //one client per app: base URL from the Scheduling.BaseUrl appSetting
            builder.Register(c => new SchedulingClient()).As<ISchedulingClient>().SingleInstance();
        }
    }
}
