using AutoMapper;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Mapping
{
    public class MappingProfile : Profile
    {
        public MappingProfile()
        {
            CreateMap<PostEntity, PostDto>()
                .ForMember(dest => dest.Id, opt => opt.MapFrom(src => src.PostId))
                .ForMember(dest => dest.PostId, opt => opt.MapFrom(src => src.PostId))
                .ForMember(dest => dest.Title, opt => opt.MapFrom(src => src.Title))
                .ForMember(dest => dest.Content, opt => opt.MapFrom(src => src.Content))
                .ForMember(dest => dest.BlogId, opt => opt.MapFrom(src => src.BlogId))
                .ForMember(dest => dest.BloggerName, opt => opt.MapFrom(src => src.BloggerName))
                .ForMember(dest => dest.LastUpdated, opt => opt.MapFrom(src => src.LastUpdated))
                .ForMember(dest => dest.Tags, opt => opt.MapFrom(src => src.Tags))
                .ForMember(dest => dest.Bloggers, opt => opt.Ignore())
                .ForMember(dest => dest.UserChosenTags, opt => opt.Ignore());

            CreateMap<PostEntity, SimplePostDto>()
                .ForMember(dest => dest.Id, opt => opt.MapFrom(src => src.PostId))
                .ForMember(dest => dest.PostId, opt => opt.MapFrom(src => src.PostId))
                .ForMember(dest => dest.Title, opt => opt.MapFrom(src => src.Title))
                .ForMember(dest => dest.BlogId, opt => opt.MapFrom(src => src.BlogId))
                .ForMember(dest => dest.BloggerName, opt => opt.MapFrom(src => src.BloggerName))
                .ForMember(dest => dest.LastUpdated, opt => opt.MapFrom(src => src.LastUpdated))
                .ForMember(dest => dest.Tags, opt => opt.MapFrom(src => src.Tags));

            CreateMap<TagEntity, TagDto>()
                .ForMember(dest => dest.Id, opt => opt.MapFrom(src => src.TagId))
                .ForMember(dest => dest.TagId, opt => opt.MapFrom(src => src.TagId))
                .ForMember(dest => dest.Name, opt => opt.MapFrom(src => src.Name))
                .ForMember(dest => dest.Slug, opt => opt.MapFrom(src => src.Slug))
                .ForMember(dest => dest.PostsCount, opt => opt.MapFrom(src => src.PostsCount));

            CreateMap<BlogEntity, BlogDto>()
                .ForMember(dest => dest.Id, opt => opt.MapFrom(src => src.BlogId))
                .ForMember(dest => dest.BlogId, opt => opt.MapFrom(src => src.BlogId))
                .ForMember(dest => dest.Name, opt => opt.MapFrom(src => src.Name))
                .ForMember(dest => dest.EmailAddress, opt => opt.MapFrom(src => src.EmailAddress))
                .ForMember(dest => dest.PostsCount, opt => opt.MapFrom(src => src.PostsCount));

            CreateMap<PostDto, PostEntity>()
                .ForMember(dest => dest.PostId, opt => opt.MapFrom(src => src.PostId))
                .ForMember(dest => dest.Title, opt => opt.MapFrom(src => src.Title))
                .ForMember(dest => dest.Content, opt => opt.MapFrom(src => src.Content))
                .ForMember(dest => dest.BlogId, opt => opt.MapFrom(src => src.BlogId));

            CreateMap<TagDto, TagEntity>()
                .ForMember(dest => dest.TagId, opt => opt.MapFrom(src => src.TagId))
                .ForMember(dest => dest.Name, opt => opt.MapFrom(src => src.Name))
                .ForMember(dest => dest.Slug, opt => opt.MapFrom(src => src.Slug));

            CreateMap<BlogDto, BlogEntity>()
                .ForMember(dest => dest.BlogId, opt => opt.MapFrom(src => src.BlogId))
                .ForMember(dest => dest.Name, opt => opt.MapFrom(src => src.Name))
                .ForMember(dest => dest.EmailAddress, opt => opt.MapFrom(src => src.EmailAddress));
        }
    }
}
