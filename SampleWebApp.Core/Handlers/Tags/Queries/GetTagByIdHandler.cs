using AutoMapper;
using MediatR;
using Microsoft.EntityFrameworkCore;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Tags.Queries
{
    public class GetTagByIdHandler : IRequestHandler<GetTagByIdQuery, TagDto?>
    {
        private readonly DbContext _context;
        private readonly IMapper _mapper;

        public GetTagByIdHandler(DbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<TagDto?> Handle(GetTagByIdQuery request, CancellationToken cancellationToken)
        {
            var tag = await _context.Set<Entities.Tag>()
                .Include(t => t.Posts)
                .FirstOrDefaultAsync(t => t.TagId == request.TagId, cancellationToken);

            return tag == null ? null : _mapper.Map<TagDto>(tag);
        }
    }
}
