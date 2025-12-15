using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using AutoMapper;
using MediatR;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.DTOs;
using SampleWebApp.Core.Handlers.Posts.Commands;
using SampleWebApp.Core.Handlers.Posts.Queries;

namespace SampleWebApp.Core.Adapters
{
    public class GenericServicesAdapter
    {
        private readonly IMediator _mediator;
        private readonly IMapper _mapper;

        public GenericServicesAdapter(IMediator mediator, IMapper mapper)
        {
            _mediator = mediator;
            _mapper = mapper;
        }

        public async Task<IQueryable<SimplePostDto>> GetAllPosts()
        {
            var query = new GetPostsQuery();
            var result = await _mediator.Send(query);
            return result.AsQueryable();
        }

        public async Task<IQueryable<SimplePostDto>> GetPostsByBlogId(int blogId)
        {
            var query = new GetPostsQuery { BlogId = blogId };
            var result = await _mediator.Send(query);
            return result.AsQueryable();
        }

        public async Task<ISuccessOrErrors<PostDto>> GetPostDetail(int id)
        {
            try
            {
                var query = new GetPostByIdQuery { Id = id };
                var result = await _mediator.Send(query);

                if (result == null)
                {
                    return SuccessOrErrors<PostDto>.FailSingleError("Post not found");
                }

                return SuccessOrErrors<PostDto>.Success(result);
            }
            catch (Exception ex)
            {
                return SuccessOrErrors<PostDto>.FailSingleError(ex.Message);
            }
        }

        public async Task<ISuccessOrErrors<PostDto>> CreatePost(PostDto dto)
        {
            try
            {
                var command = new CreatePostCommand { Post = dto };
                var result = await _mediator.Send(command);

                if (!result.IsValid)
                {
                    return SuccessOrErrors<PostDto>.FailSingleError(string.Join(", ", result.Errors));
                }

                dto.PostId = result.CreatedId;
                return SuccessOrErrors<PostDto>.Success(dto, result.SuccessMessage);
            }
            catch (Exception ex)
            {
                return SuccessOrErrors<PostDto>.FailSingleError(ex.Message);
            }
        }

        public async Task<ISuccessOrErrors> UpdatePost(PostDto dto)
        {
            try
            {
                var command = new UpdatePostCommand { Post = dto };
                var result = await _mediator.Send(command);

                if (!result.IsValid)
                {
                    return SuccessOrErrors.FailSingleError(string.Join(", ", result.Errors));
                }

                return SuccessOrErrors.Success(result.SuccessMessage);
            }
            catch (Exception ex)
            {
                return SuccessOrErrors.FailSingleError(ex.Message);
            }
        }

        public async Task<ISuccessOrErrors> DeletePost(int id)
        {
            try
            {
                var command = new DeletePostCommand { Id = id };
                var result = await _mediator.Send(command);

                if (!result.IsValid)
                {
                    return SuccessOrErrors.FailSingleError(string.Join(", ", result.Errors));
                }

                return SuccessOrErrors.Success(result.SuccessMessage);
            }
            catch (Exception ex)
            {
                return SuccessOrErrors.FailSingleError(ex.Message);
            }
        }

        public async Task<PostDto> GetCreateDto()
        {
            return await Task.FromResult(new PostDto());
        }

        public async Task<PostDto> ResetDto(PostDto dto)
        {
            return await Task.FromResult(dto);
        }
    }
}
