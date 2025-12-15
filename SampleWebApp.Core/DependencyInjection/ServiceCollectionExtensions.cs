using FluentValidation;
using MediatR;
using Microsoft.Extensions.DependencyInjection;
using SampleWebApp.Core.Adapters;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Mapping;
using SampleWebApp.Core.Services;
using SampleWebApp.Core.Validators;

namespace SampleWebApp.Core.DependencyInjection
{
    public static class ServiceCollectionExtensions
    {
        public static IServiceCollection AddCoreServices(this IServiceCollection services)
        {
            services.AddMediatR(typeof(ServiceCollectionExtensions).Assembly);

            services.AddAutoMapper(typeof(MappingProfile).Assembly);

            services.AddValidatorsFromAssembly(typeof(ServiceCollectionExtensions).Assembly);

            services.AddScoped<IPostService, PostService>();
            services.AddScoped<ITagService, TagService>();
            services.AddScoped<IBlogService, BlogService>();

            services.AddScoped<GenericServicesAdapter>();

            services.AddScoped<IValidator<PostDto>, PostValidator>();
            services.AddScoped<IValidator<TagDto>, TagValidator>();
            services.AddScoped<IValidator<BlogDto>, BlogValidator>();

            return services;
        }

        public static IServiceCollection AddDbContextAdapter<TAdapter>(this IServiceCollection services)
            where TAdapter : class, IDbContextAdapter
        {
            services.AddScoped<IDbContextAdapter, TAdapter>();
            return services;
        }
    }
}
