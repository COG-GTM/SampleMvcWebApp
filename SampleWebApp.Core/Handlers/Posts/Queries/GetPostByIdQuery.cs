using SampleWebApp.Core.Common.Queries;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostByIdQuery : BaseQuery<PostDto>
    {
        public int Id { get; set; }

        public GetPostByIdQuery() { }

        public GetPostByIdQuery(int id)
        {
            Id = id;
        }
    }
}
