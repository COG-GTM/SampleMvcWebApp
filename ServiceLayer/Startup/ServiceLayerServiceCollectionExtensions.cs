using BizLayer.Startup;
using DataLayer.DataClasses;
using DataLayer.Startup;
using GenericServices.Configuration;
using GenericServices.Setup;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.PostServices;
using ServiceLayer.TagServices;
using StatusGeneric;

namespace ServiceLayer.Startup
{
    public static class ServiceLayerServiceCollectionExtensions
    {
        public static IServiceCollection AddServiceLayer(this IServiceCollection services, string connectionString,
            bool isAzure = false)
        {
            services.AddDataLayer(connectionString, isAzure);
            services.AddBizLayer();

            var config = new GenericServicesConfig
            {
                DtoAccessValidateOnSave = true,
                DirectAccessValidateOnSave = true,
                BeforeSaveChanges = context => context is SampleWebAppDb db
                    ? db.CheckTagSlugsUnique()
                    : new StatusGenericHandler()
            };
            services.GenericServicesSimpleSetup<SampleWebAppDb>(config, typeof(TagListDto).Assembly);

            services.AddScoped<IDetailPostService, DetailPostService>();
            services.AddScoped<IDetailPostServiceAsync, DetailPostServiceAsync>();
            return services;
        }
    }
}
