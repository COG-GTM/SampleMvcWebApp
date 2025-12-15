using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Tags.Queries
{
    public class GetTagsHandler : IRequestHandler<GetTagsQuery, IEnumerable<TagDto>>
    {
        private readonly ITagService _tagService;

        public GetTagsHandler(ITagService tagService)
        {
            _tagService = tagService;
        }

        public async Task<IEnumerable<TagDto>> Handle(GetTagsQuery request, CancellationToken cancellationToken)
        {
            return await _tagService.GetAllAsync();
        }
    }
}
