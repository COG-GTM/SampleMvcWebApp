using SampleWebApp.Core.Common.Commands;
using SampleWebApp.Core.Common.Results;

namespace SampleWebApp.Core.Handlers.Posts.Commands
{
    public class DeletePostCommand : BaseCommand<DeleteResult>
    {
        public int Id { get; set; }

        public DeletePostCommand() { }

        public DeletePostCommand(int id)
        {
            Id = id;
        }
    }
}
