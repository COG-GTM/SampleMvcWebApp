using Autofac;

namespace AssessmentDelivery.Business.Startup
{
    public class BizLayerModule : Module
    {
        protected override void Load(ContainerBuilder builder)
        {
            //Register business layer: autowire all
            builder.RegisterAssemblyTypes(GetType().Assembly).AsImplementedInterfaces();
        }
    }
}
