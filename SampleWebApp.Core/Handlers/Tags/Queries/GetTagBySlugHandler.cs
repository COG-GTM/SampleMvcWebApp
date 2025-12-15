using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Tags.Queries
{
    public class GetTagBySlugHandler : IRequestHandler<GetTagBySlugQuery, TagDto>
    {
        private readonly ITagService _tagService;

        public GetTagBySlugHandler(ITagService tagService)
        {
            _tagService = tagService;
        }

        public async Task<TagDto> Handle(GetTagBySlugQuery request, CancellationToken cancellationToken)
        {
            return await _tagService.GetBySlugAsync(request.Slug);
        }
    }
}
