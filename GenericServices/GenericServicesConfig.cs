using System.Linq;
using System.Reflection;
using AutoMapper;
using GenericServices.Internal;
using GenericServices.Services;
using Microsoft.Extensions.DependencyInjection;

namespace GenericServices
{
    /// <summary>
    /// Configuration entry point for the GenericServices EF Core compatibility layer.
    /// Builds the AutoMapper configuration from the DTOs and registers the service
    /// implementations in the ASP.NET Core DI container.
    /// </summary>
    public static class GenericServicesConfig
    {
        /// <summary>
        /// The AutoMapper instance used by the DTO base classes for in-memory mapping.
        /// Set by <see cref="BuildMapper"/> / <see cref="AddGenericServices"/>.
        /// </summary>
        public static IMapper Mapper { get; set; }

        /// <summary>
        /// Scans the given assemblies for DTOs, builds the AutoMapper configuration and
        /// stores it in <see cref="Mapper"/>.
        /// </summary>
        public static IMapper BuildMapper(params Assembly[] dtoAssemblies)
        {
            var dtoTypes = dtoAssemblies
                .SelectMany(a => a.GetTypes())
                .Where(t => !t.IsAbstract && DtoReflection.IsDto(t))
                .ToList();

            var config = new MapperConfiguration(cfg => DtoMappingConfig.AddMappings(cfg, dtoTypes));
            var mapper = config.CreateMapper();
            Mapper = mapper;
            return mapper;
        }

        /// <summary>
        /// Registers the AutoMapper instance and all GenericServices service implementations.
        /// The application must separately register its DbContext as
        /// <see cref="IGenericServicesDbContext"/> (scoped).
        /// </summary>
        public static IServiceCollection AddGenericServices(this IServiceCollection services, params Assembly[] dtoAssemblies)
        {
            var mapper = BuildMapper(dtoAssemblies);
            services.AddSingleton(mapper);

            services.AddScoped<IListService, ListService>();
            services.AddScoped<IDetailService, DetailService>();
            services.AddScoped<IUpdateSetupService, UpdateSetupService>();
            services.AddScoped<IUpdateService, UpdateService>();
            services.AddScoped<ICreateSetupService, CreateSetupService>();
            services.AddScoped<ICreateService, CreateService>();
            services.AddScoped<IDeleteService, DeleteService>();

            services.AddScoped<IDetailServiceAsync, DetailServiceAsync>();
            services.AddScoped<IUpdateSetupServiceAsync, UpdateSetupServiceAsync>();
            services.AddScoped<IUpdateServiceAsync, UpdateServiceAsync>();
            services.AddScoped<ICreateSetupServiceAsync, CreateSetupServiceAsync>();
            services.AddScoped<ICreateServiceAsync, CreateServiceAsync>();
            services.AddScoped<IDeleteServiceAsync, DeleteServiceAsync>();

            return services;
        }
    }
}
