using MediatR;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Blogs.Queries
{
    public class GetBlogByIdQuery : IRequest<BlogDto?>
    {
        public int BlogId { get; set; }
    }
}
