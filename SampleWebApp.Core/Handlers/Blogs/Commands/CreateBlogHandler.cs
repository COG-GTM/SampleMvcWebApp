using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Blogs.Commands
{
    public class CreateBlogHandler : IRequestHandler<CreateBlogCommand, CreateResult>
    {
        private readonly IBlogService _blogService;

        public CreateBlogHandler(IBlogService blogService)
        {
            _blogService = blogService;
        }

        public async Task<CreateResult> Handle(CreateBlogCommand request, CancellationToken cancellationToken)
        {
            return await _blogService.CreateAsync(request.Blog);
        }
    }
}
