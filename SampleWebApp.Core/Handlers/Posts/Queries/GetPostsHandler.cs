using AutoMapper;
using MediatR;
using Microsoft.EntityFrameworkCore;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostsHandler : IRequestHandler<GetPostsQuery, IEnumerable<SimplePostDto>>
    {
        private readonly DbContext _context;
        private readonly IMapper _mapper;

        public GetPostsHandler(DbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<IEnumerable<SimplePostDto>> Handle(GetPostsQuery request, CancellationToken cancellationToken)
        {
            var query = _context.Set<Entities.Post>()
                .Include(p => p.Blogger)
                .Include(p => p.Tags)
                .AsQueryable();

            if (request.BlogId.HasValue)
            {
                query = query.Where(p => p.BlogId == request.BlogId.Value);
            }

            var posts = await query
                .OrderByDescending(p => p.LastUpdated)
                .ToListAsync(cancellationToken);

            return _mapper.Map<IEnumerable<SimplePostDto>>(posts);
        }
    }
}
