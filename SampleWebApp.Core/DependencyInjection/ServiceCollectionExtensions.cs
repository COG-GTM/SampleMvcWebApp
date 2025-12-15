using System.Reflection;
using FluentValidation;
using MediatR;
using Microsoft.Extensions.DependencyInjection;
using SampleWebApp.Core.Adapters;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.DependencyInjection
{
    public static class ServiceCollectionExtensions
    {
        public static IServiceCollection AddCoreServices(this IServiceCollection services)
        {
            var assembly = typeof(ServiceCollectionExtensions).Assembly;

            services.AddMediatR(assembly);

            services.AddAutoMapper(assembly);

            services.AddValidatorsFromAssembly(assembly);

            services.AddScoped<IPostService, PostService>();
            services.AddScoped<ITagService, TagService>();
            services.AddScoped<IBlogService, BlogService>();

            services.AddScoped<GenericServicesAdapter>();

            return services;
        }

        public static IServiceCollection AddCoreServicesWithRepositories<TPostRepository, TTagRepository, TBlogRepository>(
            this IServiceCollection services)
            where TPostRepository : class, IPostRepository
            where TTagRepository : class, ITagRepository
            where TBlogRepository : class, IBlogRepository
        {
            services.AddCoreServices();

            services.AddScoped<IPostRepository, TPostRepository>();
            services.AddScoped<ITagRepository, TTagRepository>();
            services.AddScoped<IBlogRepository, TBlogRepository>();

            return services;
        }
    }
}
