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

        public async Task<IQueryable<SimplePostDto>> GetAllPostsAsync()
        {
            var query = new GetPostsQuery();
            var result = await _mediator.Send(query);
            return result.AsQueryable();
        }

        public async Task<IQueryable<SimplePostDto>> GetPostsByBlogIdAsync(int blogId)
        {
            var query = new GetPostsQuery { BlogId = blogId };
            var result = await _mediator.Send(query);
            return result.AsQueryable();
        }

        public async Task<ISuccessOrErrors<PostDto>> GetPostDetailAsync(int id)
        {
            try
            {
                var query = new GetPostByIdQuery(id);
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

        public async Task<ISuccessOrErrors<PostDto>> GetPostForEditAsync(int id)
        {
            try
            {
                var query = new GetPostForEditQuery(id);
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

        public async Task<PostDto> GetPostForCreateAsync()
        {
            var query = new GetPostForCreateQuery();
            return await _mediator.Send(query);
        }

        public async Task<ISuccessOrErrors> CreatePostAsync(PostDto dto)
        {
            try
            {
                var command = new CreatePostCommand(dto);
                var result = await _mediator.Send(command);

                if (result.IsValid)
                {
                    return SuccessOrErrors.Success(result.SuccessMessage);
                }

                var errors = SuccessOrErrors.FailSingleError(result.Errors.FirstOrDefault()?.ErrorMessage ?? "Unknown error");
                return errors;
            }
            catch (Exception ex)
            {
                return SuccessOrErrors.FailSingleError(ex.Message);
            }
        }

        public async Task<ISuccessOrErrors> UpdatePostAsync(PostDto dto)
        {
            try
            {
                var command = new UpdatePostCommand(dto);
                var result = await _mediator.Send(command);

                if (result.IsValid)
                {
                    return SuccessOrErrors.Success(result.SuccessMessage);
                }

                var errors = SuccessOrErrors.FailSingleError(result.Errors.FirstOrDefault()?.ErrorMessage ?? "Unknown error");
                return errors;
            }
            catch (Exception ex)
            {
                return SuccessOrErrors.FailSingleError(ex.Message);
            }
        }

        public async Task<ISuccessOrErrors> DeletePostAsync(int id)
        {
            try
            {
                var command = new DeletePostCommand(id);
                var result = await _mediator.Send(command);

                if (result.IsValid)
                {
                    return SuccessOrErrors.Success(result.SuccessMessage);
                }

                var errors = SuccessOrErrors.FailSingleError(result.Errors.FirstOrDefault()?.ErrorMessage ?? "Unknown error");
                return errors;
            }
            catch (Exception ex)
            {
                return SuccessOrErrors.FailSingleError(ex.Message);
            }
        }
    }
}
