using SampleWebApp.Core.Common.Queries;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Blogs.Queries
{
    public class GetBlogByIdQuery : BaseQuery<BlogDto>
    {
        public int Id { get; set; }

        public GetBlogByIdQuery() { }

        public GetBlogByIdQuery(int id)
        {
            Id = id;
        }
    }
}
