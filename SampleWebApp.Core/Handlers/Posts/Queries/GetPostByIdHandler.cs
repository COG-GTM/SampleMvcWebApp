using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostByIdHandler : IRequestHandler<GetPostByIdQuery, PostDto>
    {
        private readonly IPostService _postService;

        public GetPostByIdHandler(IPostService postService)
        {
            _postService = postService;
        }

        public async Task<PostDto> Handle(GetPostByIdQuery request, CancellationToken cancellationToken)
        {
            return await _postService.GetByIdAsync(request.Id);
        }
    }
}
