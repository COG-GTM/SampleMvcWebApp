using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Blogs.Commands
{
    public class UpdateBlogHandler : IRequestHandler<UpdateBlogCommand, UpdateResult>
    {
        private readonly IBlogService _blogService;

        public UpdateBlogHandler(IBlogService blogService)
        {
            _blogService = blogService;
        }

        public async Task<UpdateResult> Handle(UpdateBlogCommand request, CancellationToken cancellationToken)
        {
            return await _blogService.UpdateAsync(request.Blog);
        }
    }
}
