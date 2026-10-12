using Microsoft.Extensions.DependencyInjection;

namespace BizLayer.Startup
{
    public static class BizLayerServiceCollectionExtensions
    {
        public static IServiceCollection AddBizLayer(this IServiceCollection services)
        {
            return services;
        }
    }
}
