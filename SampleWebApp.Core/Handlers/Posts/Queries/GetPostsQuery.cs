using System.Collections.Generic;
using SampleWebApp.Core.Common.Queries;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostsQuery : BaseQuery<IEnumerable<SimplePostDto>>
    {
        public int? BlogId { get; set; }
        public int Page { get; set; } = 1;
        public int PageSize { get; set; } = 10;
    }
}
