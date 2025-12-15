using SampleWebApp.Core.Common.Commands;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Posts.Commands
{
    public class CreatePostCommand : BaseCommand<CreateResult>
    {
        public PostDto Post { get; set; }
    }
}
