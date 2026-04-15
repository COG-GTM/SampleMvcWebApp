using DataLayer.DataClasses;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace DataLayer.Startup
{
    public static class DataLayerServiceCollectionExtensions
    {
        public static IServiceCollection AddDataLayer(this IServiceCollection services, string connectionString)
        {
            services.AddDbContext<SampleWebAppDb>(options =>
                options.UseSqlite(connectionString));

            return services;
        }
    }
}
