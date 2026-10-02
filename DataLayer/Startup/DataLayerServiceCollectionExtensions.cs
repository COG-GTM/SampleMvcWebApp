using DataLayer.DataClasses;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace DataLayer.Startup
{
    public static class DataLayerServiceCollectionExtensions
    {
        /// <summary>
        /// Registers SampleWebAppDb as scoped, so all db classes in a request/scope are tracked together.
        /// </summary>
        /// <param name="services"></param>
        /// <param name="connectionString"></param>
        /// <param name="isAzure">true if running on azure: turns on the SQL retry execution strategy</param>
        public static IServiceCollection AddDataLayer(this IServiceCollection services, string connectionString, bool isAzure = false)
        {
            services.AddDbContext<SampleWebAppDb>(options =>
                options.UseSqlServer(connectionString, sqlOptions =>
                {
                    if (isAzure)
                        sqlOptions.EnableRetryOnFailure();
                }));
            return services;
        }
    }
}
