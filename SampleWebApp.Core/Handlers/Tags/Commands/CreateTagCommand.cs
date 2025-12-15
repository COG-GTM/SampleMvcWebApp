using SampleWebApp.Core.Common.Commands;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Tags.Commands
{
    public class CreateTagCommand : BaseCommand<CreateResult>
    {
        public TagDto Tag { get; set; }

        public CreateTagCommand() { }

        public CreateTagCommand(TagDto tag)
        {
            Tag = tag;
        }
    }
}
