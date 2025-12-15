using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostsHandler : IRequestHandler<GetPostsQuery, IEnumerable<SimplePostDto>>
    {
        private readonly IPostService _postService;

        public GetPostsHandler(IPostService postService)
        {
            _postService = postService;
        }

        public async Task<IEnumerable<SimplePostDto>> Handle(GetPostsQuery request, CancellationToken cancellationToken)
        {
            if (request.BlogId.HasValue)
            {
                return await _postService.GetSimpleByBlogIdAsync(request.BlogId.Value);
            }

            return await _postService.GetAllSimpleAsync();
        }
    }
}
