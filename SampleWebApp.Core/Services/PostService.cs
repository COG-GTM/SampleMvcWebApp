using System;
using System.Collections.Generic;
using System.Linq;
using System.Linq.Expressions;
using System.Threading.Tasks;
using AutoMapper;
using SampleWebApp.Core.Common.Results;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Services
{
    public class PostService : IPostService
    {
        private readonly IMapper _mapper;
        private readonly IPostRepository _repository;

        public PostService(IMapper mapper, IPostRepository repository)
        {
            _mapper = mapper;
            _repository = repository;
        }

        public async Task<IEnumerable<PostDto>> GetAllAsync()
        {
            var posts = await _repository.GetAllPostsAsync();
            return _mapper.Map<IEnumerable<PostDto>>(posts);
        }

        public async Task<IEnumerable<PostDto>> GetFilteredAsync(Expression<Func<PostDto, bool>> filter)
        {
            var posts = await _repository.GetAllPostsAsync();
            var dtos = _mapper.Map<IEnumerable<PostDto>>(posts);
            return dtos.AsQueryable().Where(filter);
        }

        public async Task<PagedResult<PostDto>> GetPagedAsync(int page, int pageSize)
        {
            var (posts, totalCount) = await _repository.GetPagedPostsAsync(page, pageSize);
            var dtos = _mapper.Map<IEnumerable<PostDto>>(posts);
            return new PagedResult<PostDto>(dtos, totalCount, page, pageSize);
        }

        public async Task<PostDto> GetByIdAsync(int id)
        {
            var post = await _repository.GetPostByIdAsync(id);
            return _mapper.Map<PostDto>(post);
        }

        public Task<PostDto> GetBySlugAsync(string slug)
        {
            throw new NotImplementedException("Posts do not have slugs");
        }

        public async Task<CreateResult> CreateAsync(PostDto dto)
        {
            try
            {
                var id = await _repository.CreatePostAsync(dto);
                return CreateResult.Success($"Post '{dto.Title}' created successfully", id);
            }
            catch (Exception ex)
            {
                return CreateResult.Failure(ex.Message);
            }
        }

        public async Task<PostDto> GetCreateDtoAsync()
        {
            var dto = new PostDto();
            await SetupSecondaryDataAsync(dto);
            return dto;
        }

        public async Task<UpdateResult> UpdateAsync(PostDto dto)
        {
            try
            {
                await _repository.UpdatePostAsync(dto);
                return UpdateResult.Success($"Post '{dto.Title}' updated successfully");
            }
            catch (Exception ex)
            {
                return UpdateResult.Failure(ex.Message);
            }
        }

        public async Task<PostDto> GetUpdateDtoAsync(int id)
        {
            var dto = await GetByIdAsync(id);
            if (dto != null)
            {
                await SetupSecondaryDataAsync(dto);
            }
            return dto;
        }

        public async Task<PostDto> ResetDtoAsync(PostDto dto)
        {
            await SetupSecondaryDataAsync(dto);
            return dto;
        }

        public async Task<DeleteResult> DeleteAsync(int id)
        {
            try
            {
                await _repository.DeletePostAsync(id);
                return DeleteResult.Success("Post deleted successfully");
            }
            catch (Exception ex)
            {
                return DeleteResult.Failure(ex.Message);
            }
        }

        public async Task<IEnumerable<PostDto>> GetByBlogIdAsync(int blogId)
        {
            var posts = await _repository.GetPostsByBlogIdAsync(blogId);
            return _mapper.Map<IEnumerable<PostDto>>(posts);
        }

        public async Task<IEnumerable<SimplePostDto>> GetAllSimpleAsync()
        {
            var posts = await _repository.GetAllPostsAsync();
            return _mapper.Map<IEnumerable<SimplePostDto>>(posts);
        }

        public async Task<IEnumerable<SimplePostDto>> GetSimpleByBlogIdAsync(int blogId)
        {
            var posts = await _repository.GetPostsByBlogIdAsync(blogId);
            return _mapper.Map<IEnumerable<SimplePostDto>>(posts);
        }

        private async Task SetupSecondaryDataAsync(PostDto dto)
        {
            var blogs = await _repository.GetAllBlogsAsync();
            var tags = await _repository.GetAllTagsAsync();

            dto.Bloggers.SetupDropDownListContent(
                blogs.Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                "--- choose blogger ---");

            if (dto.PostId != 0)
            {
                dto.Bloggers.SetSelectedValue(dto.BlogId.ToString("D"));
            }

            var preselectedTags = dto.PostId == 0
                ? new List<KeyValuePair<string, int>>()
                : dto.Tags?.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)).ToList()
                  ?? new List<KeyValuePair<string, int>>();

            dto.UserChosenTags.SetupMultiSelectList(
                tags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)),
                preselectedTags);
        }
    }

    public interface IPostRepository
    {
        Task<IEnumerable<object>> GetAllPostsAsync();
        Task<(IEnumerable<object> posts, int totalCount)> GetPagedPostsAsync(int page, int pageSize);
        Task<object> GetPostByIdAsync(int id);
        Task<IEnumerable<object>> GetPostsByBlogIdAsync(int blogId);
        Task<int> CreatePostAsync(PostDto dto);
        Task UpdatePostAsync(PostDto dto);
        Task DeletePostAsync(int id);
        Task<IEnumerable<BlogDto>> GetAllBlogsAsync();
        Task<IEnumerable<TagDto>> GetAllTagsAsync();
    }
}
