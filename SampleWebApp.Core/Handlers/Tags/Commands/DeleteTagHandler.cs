using System.Threading;
using System.Threading.Tasks;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.Services;

namespace SampleWebApp.Core.Handlers.Tags.Commands
{
    public class DeleteTagHandler : IRequestHandler<DeleteTagCommand, DeleteResult>
    {
        private readonly ITagService _tagService;

        public DeleteTagHandler(ITagService tagService)
        {
            _tagService = tagService;
        }

        public async Task<DeleteResult> Handle(DeleteTagCommand request, CancellationToken cancellationToken)
        {
            return await _tagService.DeleteAsync(request.Id);
        }
    }
}
