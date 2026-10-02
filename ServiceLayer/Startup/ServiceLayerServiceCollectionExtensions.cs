using BizLayer.Startup;
using DataLayer.DataClasses;
using DataLayer.Startup;
using GenericServices.Configuration;
using GenericServices.Setup;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.PostServices;

namespace ServiceLayer.Startup
{
    public static class ServiceLayerServiceCollectionExtensions
    {
        /// <summary>
        /// This registers all items in the service layer and below
        /// </summary>
        public static IServiceCollection AddServiceLayer(this IServiceCollection services, string connectionString, bool isAzure = false)
        {
            services.AddDataLayer(connectionString, isAzure);
            services.AddBizLayer();

            services.GenericServicesSimpleSetup<SampleWebAppDb>(
                new GenericServicesConfig
                {
                    DirectAccessValidateOnSave = true,
                    DtoAccessValidateOnSave = true
                },
                typeof(ServiceLayerServiceCollectionExtensions).Assembly);

            services.AddScoped<IDetailPostService, DetailPostService>();
            services.AddScoped<IDetailPostServiceAsync, DetailPostServiceAsync>();
            return services;
        }
    }
}
