using SampleWebApp.Core.Common.Commands;
using SampleWebApp.Core.Common.Results;

namespace SampleWebApp.Core.Handlers.Blogs.Commands
{
    public class DeleteBlogCommand : BaseCommand<DeleteResult>
    {
        public int Id { get; set; }

        public DeleteBlogCommand() { }

        public DeleteBlogCommand(int id)
        {
            Id = id;
        }
    }
}
