using MediatR;
using Microsoft.AspNetCore.Mvc;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Handlers.Blogs.Queries;

namespace SampleWebApp.Api.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class BlogsController : ControllerBase
    {
        private readonly IMediator _mediator;

        public BlogsController(IMediator mediator)
        {
            _mediator = mediator;
        }

        [HttpGet]
        public async Task<ActionResult<IEnumerable<BlogDto>>> GetBlogs()
        {
            var blogs = await _mediator.Send(new GetBlogsQuery());
            return Ok(blogs);
        }

        [HttpGet("{id}")]
        public async Task<ActionResult<BlogDto>> GetBlog(int id)
        {
            var blog = await _mediator.Send(new GetBlogByIdQuery { BlogId = id });
            if (blog == null)
            {
                return NotFound();
            }
            return Ok(blog);
        }
    }
}
