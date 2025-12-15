using AutoMapper;
using MediatR;
using Microsoft.EntityFrameworkCore;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Tags.Queries
{
    public class GetTagsHandler : IRequestHandler<GetTagsQuery, IEnumerable<TagDto>>
    {
        private readonly DbContext _context;
        private readonly IMapper _mapper;

        public GetTagsHandler(DbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<IEnumerable<TagDto>> Handle(GetTagsQuery request, CancellationToken cancellationToken)
        {
            var tags = await _context.Set<Entities.Tag>()
                .Include(t => t.Posts)
                .OrderBy(t => t.Name)
                .ToListAsync(cancellationToken);

            return _mapper.Map<IEnumerable<TagDto>>(tags);
        }
    }
}
