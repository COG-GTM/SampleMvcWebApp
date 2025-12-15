using AutoMapper;
using MediatR;
using Microsoft.EntityFrameworkCore;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Handlers.Posts.Queries
{
    public class GetPostByIdHandler : IRequestHandler<GetPostByIdQuery, DetailPostDto?>
    {
        private readonly DbContext _context;
        private readonly IMapper _mapper;

        public GetPostByIdHandler(DbContext context, IMapper mapper)
        {
            _context = context;
            _mapper = mapper;
        }

        public async Task<DetailPostDto?> Handle(GetPostByIdQuery request, CancellationToken cancellationToken)
        {
            var post = await _context.Set<Entities.Post>()
                .Include(p => p.Blogger)
                .Include(p => p.Tags)
                .FirstOrDefaultAsync(p => p.PostId == request.PostId, cancellationToken);

            return post == null ? null : _mapper.Map<DetailPostDto>(post);
        }
    }
}
