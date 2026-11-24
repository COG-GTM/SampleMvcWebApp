using DataLayer.DataClasses;
using GenericServices.Configuration;
using GenericServices.Setup;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace ServiceLayer.Startup
{
    public static class ServiceLayerServiceCollectionExtensions
    {
        /// <summary>
        /// This registers all items in service layer and below
        /// </summary>
        /// <param name="services"></param>
        /// <param name="connectionString">SQL Server connection string</param>
        /// <param name="isAzure">true if working with an Azure database (turns on the retry execution strategy)</param>
        public static IServiceCollection AddServiceLayer(this IServiceCollection services, string connectionString, bool isAzure = false)
        {
            services.AddDbContext<SampleWebAppDb>(options => options.UseSqlServer(connectionString, sql =>
            {
                if (isAzure)
                    sql.EnableRetryOnFailure();
            }));

            services.GenericServicesSimpleSetup<SampleWebAppDb>(
                new GenericServicesConfig
                {
                    DtoAccessValidateOnSave = true,
                    DirectAccessValidateOnSave = true
                },
                typeof(ServiceLayerServiceCollectionExtensions).Assembly);

            return services;
        }
    }
}
