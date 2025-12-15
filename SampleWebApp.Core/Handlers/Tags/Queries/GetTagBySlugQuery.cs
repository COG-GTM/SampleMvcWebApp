using SampleWebApp.Core.Common.Queries;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Tags.Queries
{
    public class GetTagBySlugQuery : BaseQuery<TagDto>
    {
        public string Slug { get; set; }
    }
}
