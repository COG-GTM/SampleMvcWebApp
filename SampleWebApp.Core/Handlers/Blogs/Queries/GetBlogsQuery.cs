using MediatR;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Blogs.Queries
{
    public class GetBlogsQuery : IRequest<IEnumerable<BlogDto>>
    {
    }
}
