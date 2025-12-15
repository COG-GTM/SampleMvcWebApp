using AutoMapper;
using MediatR;
using Microsoft.EntityFrameworkCore;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Blogs.Queries
{
    public class GetBlogsHandler : IRequestHandler<GetBlogsQuery, IEnumerable<BlogDto>>
    {
        private readonly DbContext _context;
        private readonly IMapper _mapper;

        public GetBlogsHandler(DbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<IEnumerable<BlogDto>> Handle(GetBlogsQuery request, CancellationToken cancellationToken)
        {
            var blogs = await _context.Set<Entities.Blog>()
                .Include(b => b.Posts)
                .OrderByDescending(b => b.Name)
                .ToListAsync(cancellationToken);

            return _mapper.Map<IEnumerable<BlogDto>>(blogs);
        }
    }
}
