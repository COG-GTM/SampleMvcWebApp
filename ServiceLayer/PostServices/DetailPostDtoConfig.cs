using DataLayer.DataClasses.Concrete;
using GenericServices.Configuration;

namespace ServiceLayer.PostServices
{
    public class DetailPostDtoConfig : PerDtoConfig<DetailPostDto, Post>
    {
        public override System.Action<AutoMapper.IMappingExpression<Post, DetailPostDto>> AlterReadMapping =>
            cfg => cfg.ForMember(d => d.Bloggers, o => o.Ignore()).ForMember(d => d.UserChosenTags, o => o.Ignore());
    }

    public class DetailPostDtoAsyncConfig : PerDtoConfig<DetailPostDtoAsync, Post>
    {
        public override System.Action<AutoMapper.IMappingExpression<Post, DetailPostDtoAsync>> AlterReadMapping =>
            cfg => cfg.ForMember(d => d.Bloggers, o => o.Ignore()).ForMember(d => d.UserChosenTags, o => o.Ignore());
    }
}
