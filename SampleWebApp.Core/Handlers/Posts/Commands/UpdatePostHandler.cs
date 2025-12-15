using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Posts.Commands
{
    public class UpdatePostHandler : IRequestHandler<UpdatePostCommand, UpdateResult>
    {
        private readonly IPostService _postService;

        public UpdatePostHandler(IPostService postService)
        {
            _postService = postService;
        }

        public async Task<UpdateResult> Handle(UpdatePostCommand request, CancellationToken cancellationToken)
        {
            return await _postService.UpdateAsync(request.Post);
        }
    }
}
