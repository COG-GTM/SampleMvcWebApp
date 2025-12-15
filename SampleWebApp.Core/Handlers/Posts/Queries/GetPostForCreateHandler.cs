using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostForCreateHandler : IRequestHandler<GetPostForCreateQuery, PostDto>
    {
        private readonly IPostService _postService;

        public GetPostForCreateHandler(IPostService postService)
        {
            _postService = postService;
        }

        public async Task<PostDto> Handle(GetPostForCreateQuery request, CancellationToken cancellationToken)
        {
            return await _postService.GetCreateDtoAsync();
        }
    }
}
