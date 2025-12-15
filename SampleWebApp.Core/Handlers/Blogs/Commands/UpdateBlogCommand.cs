using SampleWebApp.Core.Common.Commands;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Blogs.Commands
{
    public class UpdateBlogCommand : BaseCommand<UpdateResult>
    {
        public BlogDto Blog { get; set; }

        public UpdateBlogCommand() { }

        public UpdateBlogCommand(BlogDto blog)
        {
            Blog = blog;
        }
    }
}
