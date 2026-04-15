using DataLayer.Startup;
using Microsoft.Extensions.DependencyInjection;

namespace ServiceLayer.Startup
{
    public static class ServiceLayerServiceCollectionExtensions
    {
        public static IServiceCollection AddServiceLayer(this IServiceCollection services, string connectionString)
        {
            // Register data layer services
            services.AddDataLayer(connectionString);

            return services;
        }
    }
}
