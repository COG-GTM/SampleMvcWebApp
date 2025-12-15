using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Tags.Queries
{
    public class GetTagByIdHandler : IRequestHandler<GetTagByIdQuery, TagDto>
    {
        private readonly ITagService _tagService;

        public GetTagByIdHandler(ITagService tagService)
        {
            _tagService = tagService;
        }

        public async Task<TagDto> Handle(GetTagByIdQuery request, CancellationToken cancellationToken)
        {
            return await _tagService.GetByIdAsync(request.Id);
        }
    }
}
