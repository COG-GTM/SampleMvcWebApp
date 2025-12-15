using SampleWebApp.Core.Common.Queries;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Tags.Queries
{
    public class GetTagByIdQuery : BaseQuery<TagDto>
    {
        public int Id { get; set; }
    }
}
