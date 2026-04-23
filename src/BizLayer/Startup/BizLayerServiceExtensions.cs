using Microsoft.Extensions.DependencyInjection;

namespace BizLayer.Startup;

/// <summary>
/// Registers business-layer dependencies with the application's DI container.
/// Concrete business logic is added in later migration phases.
/// </summary>
public static class BizLayerServiceExtensions
{
    public static IServiceCollection AddBizLayer(this IServiceCollection services)
    {
        return services;
    }
}
