using MediatR;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostsQuery : IRequest<IEnumerable<SimplePostDto>>
    {
        public int? BlogId { get; set; }
    }
}
