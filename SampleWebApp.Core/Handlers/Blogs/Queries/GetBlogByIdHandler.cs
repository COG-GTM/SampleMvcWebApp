using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Blogs.Queries
{
    public class GetBlogByIdHandler : IRequestHandler<GetBlogByIdQuery, BlogDto>
    {
        private readonly IBlogService _blogService;

        public GetBlogByIdHandler(IBlogService blogService)
        {
            _blogService = blogService;
        }

        public async Task<BlogDto> Handle(GetBlogByIdQuery request, CancellationToken cancellationToken)
        {
            return await _blogService.GetByIdAsync(request.Id);
        }
    }
}
