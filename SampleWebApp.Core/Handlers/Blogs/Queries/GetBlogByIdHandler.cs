using AutoMapper;
using MediatR;
using Microsoft.EntityFrameworkCore;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Blogs.Queries
{
    public class GetBlogByIdHandler : IRequestHandler<GetBlogByIdQuery, BlogDto?>
    {
        private readonly DbContext _context;
        private readonly IMapper _mapper;

        public GetBlogByIdHandler(DbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<BlogDto?> Handle(GetBlogByIdQuery request, CancellationToken cancellationToken)
        {
            var blog = await _context.Set<Entities.Blog>()
                .Include(b => b.Posts)
                .FirstOrDefaultAsync(b => b.BlogId == request.BlogId, cancellationToken);

            return blog == null ? null : _mapper.Map<BlogDto>(blog);
        }
    }
}
