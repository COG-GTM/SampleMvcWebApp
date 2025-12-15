using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Blogs.Queries
{
    public class GetBlogsHandler : IRequestHandler<GetBlogsQuery, IEnumerable<BlogDto>>
    {
        private readonly IBlogService _blogService;

        public GetBlogsHandler(IBlogService blogService)
        {
            _blogService = blogService;
        }

        public async Task<IEnumerable<BlogDto>> Handle(GetBlogsQuery request, CancellationToken cancellationToken)
        {
            return await _blogService.GetAllAsync();
        }
    }
}
