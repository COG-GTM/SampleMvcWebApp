using MediatR;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Tags.Queries
{
    public class GetTagByIdQuery : IRequest<TagDto?>
    {
        public int TagId { get; set; }
    }
}
