using Microsoft.Extensions.DependencyInjection;

namespace ServiceLayer.Startup;

/// <summary>
/// Registers service-layer dependencies with the application's DI container.
/// Concrete services are added in later migration phases.
/// </summary>
public static class ServiceLayerServiceExtensions
{
    public static IServiceCollection AddServiceLayer(this IServiceCollection services)
    {
        return services;
    }
}
