using SampleWebApp.Core.Common.Commands;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Posts.Commands
{
    public class UpdatePostCommand : BaseCommand<UpdateResult>
    {
        public PostDto Post { get; set; }

        public UpdatePostCommand() { }

        public UpdatePostCommand(PostDto post)
        {
            Post = post;
        }
    }
}
