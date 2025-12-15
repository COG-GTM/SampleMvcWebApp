using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Blogs.Commands
{
    public class DeleteBlogHandler : IRequestHandler<DeleteBlogCommand, DeleteResult>
    {
        private readonly IBlogService _blogService;

        public DeleteBlogHandler(IBlogService blogService)
        {
            _blogService = blogService;
        }

        public async Task<DeleteResult> Handle(DeleteBlogCommand request, CancellationToken cancellationToken)
        {
            return await _blogService.DeleteAsync(request.Id);
        }
    }
}
