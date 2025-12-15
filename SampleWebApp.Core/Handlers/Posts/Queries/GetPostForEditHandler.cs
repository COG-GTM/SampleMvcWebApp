using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostForEditHandler : IRequestHandler<GetPostForEditQuery, PostDto>
    {
        private readonly IPostService _postService;

        public GetPostForEditHandler(IPostService postService)
        {
            _postService = postService;
        }

        public async Task<PostDto> Handle(GetPostForEditQuery request, CancellationToken cancellationToken)
        {
            return await _postService.GetUpdateDtoAsync(request.Id);
        }
    }
}
