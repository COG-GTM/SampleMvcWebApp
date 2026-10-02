using Microsoft.Extensions.DependencyInjection;

namespace BizLayer.Startup
{
    /// <summary>
    /// This registers the business layer services. The BizLayer currently has no services.
    /// </summary>
    public static class BizLayerServiceCollectionExtensions
    {
        public static IServiceCollection AddBizLayer(this IServiceCollection services)
        {
            return services;
        }
    }
}
