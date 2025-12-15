using SampleWebApp.Core.Common.Commands;
using SampleWebApp.Core.Common.Results;

namespace SampleWebApp.Core.Handlers.Tags.Commands
{
    public class DeleteTagCommand : BaseCommand<DeleteResult>
    {
        public int Id { get; set; }
    }
}
