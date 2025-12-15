using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Tags.Commands
{
    public class UpdateTagHandler : IRequestHandler<UpdateTagCommand, UpdateResult>
    {
        private readonly ITagService _tagService;

        public UpdateTagHandler(ITagService tagService)
        {
            _tagService = tagService;
        }

        public async Task<UpdateResult> Handle(UpdateTagCommand request, CancellationToken cancellationToken)
        {
            return await _tagService.UpdateAsync(request.Tag);
        }
    }
}
