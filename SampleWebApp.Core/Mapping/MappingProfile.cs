using AutoMapper;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Entities;

namespace SampleWebApp.Core.Mapping
{
    public class MappingProfile : Profile
    {
        public MappingProfile()
        {
            CreateMap<Blog, BlogDto>()
                .ForMember(dest => dest.PostsCount, opt => opt.MapFrom(src => src.Posts.Count));

            CreateMap<Tag, TagDto>()
                .ForMember(dest => dest.PostCount, opt => opt.MapFrom(src => src.Posts.Count));

            CreateMap<Post, SimplePostDto>()
                .ForMember(dest => dest.BloggerName, opt => opt.MapFrom(src => src.Blogger.Name))
                .ForMember(dest => dest.TagNames, opt => opt.MapFrom(src => src.Tags.Select(t => t.Name).ToList()));

            CreateMap<Post, DetailPostDto>()
                .ForMember(dest => dest.BloggerName, opt => opt.MapFrom(src => src.Blogger.Name))
                .ForMember(dest => dest.Tags, opt => opt.MapFrom(src => src.Tags));
        }
    }
}
