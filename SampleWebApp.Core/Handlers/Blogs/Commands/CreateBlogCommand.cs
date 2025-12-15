using SampleWebApp.Core.Common.Commands;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Blogs.Commands
{
    public class CreateBlogCommand : BaseCommand<CreateResult>
    {
        public BlogDto Blog { get; set; }
    }
}
