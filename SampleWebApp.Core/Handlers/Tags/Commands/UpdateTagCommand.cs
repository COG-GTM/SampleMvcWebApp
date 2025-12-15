using SampleWebApp.Core.Common.Commands;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Tags.Commands
{
    public class UpdateTagCommand : BaseCommand<UpdateResult>
    {
        public TagDto Tag { get; set; }
    }
}
