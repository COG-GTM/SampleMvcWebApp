using AutoMapper;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.Extensions.DependencyInjection;
using ServiceLayer.BlogServices;
using ServiceLayer.PostServices;
using ServiceLayer.TagServices;

namespace ServiceLayer.Startup
{
    /// <summary>
    /// AutoMapper profile describing how the database entities project onto the DTOs.
    /// Replaces the mapping that the original GenericServices package built internally.
    /// </summary>
    public class ServiceLayerMappingProfile : Profile
    {
        public ServiceLayerMappingProfile()
        {
            //identity map used when projecting the Tags collection on the post DTOs
            CreateMap<Tag, Tag>()
                .ForMember(d => d.Posts, opt => opt.Ignore());

            CreateMap<Post, SimplePostDto>();
            CreateMap<Post, SimplePostDtoAsync>();

            CreateMap<Post, DetailPostDto>()
                .ForMember(d => d.Bloggers, opt => opt.Ignore())
                .ForMember(d => d.UserChosenTags, opt => opt.Ignore());
            CreateMap<Post, DetailPostDtoAsync>()
                .ForMember(d => d.Bloggers, opt => opt.Ignore())
                .ForMember(d => d.UserChosenTags, opt => opt.Ignore());

            CreateMap<Blog, BlogListDto>();
            CreateMap<Tag, TagListDto>();
        }
    }

    /// <summary>
    /// Registers the service layer (and the data layer DbContext below it) and the GenericServices
    /// implementations with the ASP.NET Core DI container. Replaces the old Autofac ServiceLayerModule.
    /// </summary>
    public static class ServiceLayerConfig
    {
        public static IServiceCollection AddServiceLayer(this IServiceCollection services)
        {
            //AutoMapper
            var mapperConfig = new MapperConfiguration(cfg => cfg.AddProfile<ServiceLayerMappingProfile>());
            mapperConfig.AssertConfigurationIsValid();
            services.AddSingleton(mapperConfig.CreateMapper());

            //expose the DbContext through the GenericServices abstraction
            services.AddScoped<IGenericServicesDbContext>(sp => sp.GetRequiredService<SampleWebAppDb>());

            //GenericServices implementations
            services.AddScoped<IListService, ListService>();
            services.AddScoped<IDetailService, DetailService>();
            services.AddScoped<IDetailServiceAsync, DetailServiceAsync>();
            services.AddScoped<ICreateSetupService, CreateSetupService>();
            services.AddScoped<ICreateSetupServiceAsync, CreateSetupServiceAsync>();
            services.AddScoped<ICreateService, CreateService>();
            services.AddScoped<ICreateServiceAsync, CreateServiceAsync>();
            services.AddScoped<IUpdateSetupService, UpdateSetupService>();
            services.AddScoped<IUpdateSetupServiceAsync, UpdateSetupServiceAsync>();
            services.AddScoped<IUpdateService, UpdateService>();
            services.AddScoped<IUpdateServiceAsync, UpdateServiceAsync>();
            services.AddScoped<IDeleteService, DeleteService>();
            services.AddScoped<IDeleteServiceAsync, DeleteServiceAsync>();

            return services;
        }
    }
}
