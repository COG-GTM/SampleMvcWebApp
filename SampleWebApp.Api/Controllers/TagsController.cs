using MediatR;
using Microsoft.AspNetCore.Mvc;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Handlers.Tags.Queries;

namespace SampleWebApp.Api.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class TagsController : ControllerBase
    {
        private readonly IMediator _mediator;

        public TagsController(IMediator mediator)
        {
            _mediator = mediator;
        }

        [HttpGet]
        public async Task<ActionResult<IEnumerable<TagDto>>> GetTags()
        {
            var tags = await _mediator.Send(new GetTagsQuery());
            return Ok(tags);
        }

        [HttpGet("{id}")]
        public async Task<ActionResult<TagDto>> GetTag(int id)
        {
            var tag = await _mediator.Send(new GetTagByIdQuery { TagId = id });
            if (tag == null)
            {
                return NotFound();
            }
            return Ok(tag);
        }
    }
}
