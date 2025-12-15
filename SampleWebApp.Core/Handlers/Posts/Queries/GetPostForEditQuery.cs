using SampleWebApp.Core.Common.Queries;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostForEditQuery : BaseQuery<PostDto>
    {
        public int Id { get; set; }

        public GetPostForEditQuery() { }

        public GetPostForEditQuery(int id)
        {
            Id = id;
        }
    }
}
