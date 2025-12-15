using System.Collections.Generic;
using SampleWebApp.Core.Common.Queries;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Blogs.Queries
{
    public class GetBlogsQuery : BaseQuery<IEnumerable<BlogDto>>
    {
        public int Page { get; set; } = 1;
        public int PageSize { get; set; } = 10;
    }
}
