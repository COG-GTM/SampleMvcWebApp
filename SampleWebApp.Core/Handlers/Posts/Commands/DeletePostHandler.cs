using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Posts.Commands
{
    public class DeletePostHandler : IRequestHandler<DeletePostCommand, DeleteResult>
    {
        private readonly IPostService _postService;

        public DeletePostHandler(IPostService postService)
        {
            _postService = postService;
        }

        public async Task<DeleteResult> Handle(DeletePostCommand request, CancellationToken cancellationToken)
        {
            return await _postService.DeleteAsync(request.Id);
        }
    }
}
