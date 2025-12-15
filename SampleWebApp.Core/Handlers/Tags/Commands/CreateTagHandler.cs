using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Tags.Commands
{
    public class CreateTagHandler : IRequestHandler<CreateTagCommand, CreateResult>
    {
        private readonly ITagService _tagService;

        public CreateTagHandler(ITagService tagService)
        {
            _tagService = tagService;
        }

        public async Task<CreateResult> Handle(CreateTagCommand request, CancellationToken cancellationToken)
        {
            return await _tagService.CreateAsync(request.Tag);
        }
    }
}
