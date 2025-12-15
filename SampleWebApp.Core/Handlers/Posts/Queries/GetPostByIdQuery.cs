using MediatR;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostByIdQuery : IRequest<DetailPostDto?>
    {
        public int PostId { get; set; }
    }
}
