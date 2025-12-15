using MediatR;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Tags.Queries
{
    public class GetTagsQuery : IRequest<IEnumerable<TagDto>>
    {
    }
}
