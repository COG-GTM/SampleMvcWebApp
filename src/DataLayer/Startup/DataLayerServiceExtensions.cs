using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace DataLayer.Startup;

/// <summary>
/// Registers the data layer's EF Core services with the application's DI container.
/// </summary>
public static class DataLayerServiceExtensions
{
    public static IServiceCollection AddDataLayer(this IServiceCollection services, IConfiguration configuration)
    {
        var connectionString = configuration.GetConnectionString(DataClasses.SampleWebAppDbContext.ConnectionStringName);

        if (string.IsNullOrWhiteSpace(connectionString))
        {
            throw new InvalidOperationException(
                $"Connection string '{DataClasses.SampleWebAppDbContext.ConnectionStringName}' was not found.");
        }

        services.AddDbContext<DataClasses.SampleWebAppDbContext>(options =>
            options.UseSqlServer(connectionString));

        return services;
    }
}
