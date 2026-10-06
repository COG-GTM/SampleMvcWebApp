using DataLayer.DataClasses;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace DataLayer.Startup
{
    public static class DataLayerServiceCollectionExtensions
    {
        public static IServiceCollection AddDataLayer(this IServiceCollection services, string connectionString,
            bool isAzure = false)
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
