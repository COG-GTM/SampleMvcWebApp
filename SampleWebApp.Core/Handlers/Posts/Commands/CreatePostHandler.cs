using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Posts.Commands
{
    public class CreatePostHandler : IRequestHandler<CreatePostCommand, CreateResult>
    {
        private readonly IPostService _postService;

        public CreatePostHandler(IPostService postService)
        {
            _postService = postService;
        }

        public async Task<CreateResult> Handle(CreatePostCommand request, CancellationToken cancellationToken)
        {
            return await _postService.CreateAsync(request.Post);
        }
    }
}
