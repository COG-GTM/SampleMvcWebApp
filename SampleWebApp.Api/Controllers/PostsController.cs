using MediatR;
using Microsoft.AspNetCore.Mvc;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Handlers.Posts.Queries;

namespace SampleWebApp.Api.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class PostsController : ControllerBase
    {
        private readonly IMediator _mediator;

        public PostsController(IMediator mediator)
        {
            _mediator = mediator;
        }

        [HttpGet]
        public async Task<ActionResult<IEnumerable<SimplePostDto>>> GetPosts([FromQuery] int? blogId)
        {
            var posts = await _mediator.Send(new GetPostsQuery { BlogId = blogId });
            return Ok(posts);
        }

        [HttpGet("{id}")]
        public async Task<ActionResult<DetailPostDto>> GetPost(int id)
        {
            var post = await _mediator.Send(new GetPostByIdQuery { PostId = id });
            if (post == null)
            {
                return NotFound();
            }
            return Ok(post);
        }
    }
}
