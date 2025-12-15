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
        private readonly IDbContextAdapter _dbContext;

        public PostService(IDbContextAdapter dbContext, IMapper mapper)
        {
            _dbContext = dbContext;
            _mapper = mapper;
        }

        public async Task<IEnumerable<PostDto>> GetAllAsync()
        {
            var posts = await _dbContext.GetPostsWithIncludesAsync();
            return _mapper.Map<IEnumerable<PostDto>>(posts);
        }

        public async Task<IEnumerable<PostDto>> GetFilteredAsync(Expression<Func<PostDto, bool>> filter)
        {
            var allPosts = await GetAllAsync();
            return allPosts.AsQueryable().Where(filter);
        }

        public async Task<PagedResult<PostDto>> GetPagedAsync(int page, int pageSize)
        {
            var allPosts = await GetAllAsync();
            var postsList = allPosts.ToList();
            var pagedPosts = postsList.Skip((page - 1) * pageSize).Take(pageSize);
            return new PagedResult<PostDto>(pagedPosts, postsList.Count, page, pageSize);
        }

        public async Task<PostDto> GetByIdAsync(int id)
        {
            var post = await _dbContext.GetPostByIdAsync(id);
            if (post == null)
                return null;

            var dto = _mapper.Map<PostDto>(post);
            await SetupSecondaryDataAsync(dto);
            return dto;
        }

        public async Task<PostDto> GetBySlugAsync(string slug)
        {
            return await Task.FromResult<PostDto>(null);
        }

        public async Task<CreateResult> CreateAsync(PostDto dto)
        {
            try
            {
                var tagIds = dto.UserChosenTags?.GetFinalSelectionAsInts() ?? new int[0];
                if (!tagIds.Any())
                    return CreateResult.Fail("You must select at least one tag for the post.");

                var blogId = dto.Bloggers?.SelectedValueAsInt;
                if (!blogId.HasValue)
                    return CreateResult.Fail("The blogger was not selected. You must do that before the post can be saved.");

                dto.BlogId = blogId.Value;

                var postId = await _dbContext.CreatePostAsync(dto.Title, dto.Content, dto.BlogId, tagIds);
                return CreateResult.Success($"Post '{dto.Title}' created successfully", postId);
            }
            catch (Exception ex)
            {
                return CreateResult.Fail(ex.Message);
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
                var tagIds = dto.UserChosenTags?.GetFinalSelectionAsInts() ?? new int[0];
                if (!tagIds.Any())
                    return UpdateResult.Fail("You must select at least one tag for the post.");

                var blogId = dto.Bloggers?.SelectedValueAsInt;
                if (!blogId.HasValue)
                    return UpdateResult.Fail("The blogger was not selected. You must do that before the post can be saved.");

                dto.BlogId = blogId.Value;

                await _dbContext.UpdatePostAsync(dto.PostId, dto.Title, dto.Content, dto.BlogId, tagIds);
                return UpdateResult.Success($"Post '{dto.Title}' updated successfully");
            }
            catch (Exception ex)
            {
                return UpdateResult.Fail(ex.Message);
            }
        }

        public async Task<PostDto> GetUpdateDtoAsync(int id)
        {
            return await GetByIdAsync(id);
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
                await _dbContext.DeletePostAsync(id);
                return DeleteResult.Success("Post deleted successfully");
            }
            catch (Exception ex)
            {
                return DeleteResult.Fail(ex.Message);
            }
        }

        public async Task<IEnumerable<PostDto>> GetByBlogIdAsync(int blogId)
        {
            var posts = await _dbContext.GetPostsByBlogIdAsync(blogId);
            return _mapper.Map<IEnumerable<PostDto>>(posts);
        }

        public async Task<IEnumerable<SimplePostDto>> GetAllSimpleAsync()
        {
            var posts = await _dbContext.GetPostsWithIncludesAsync();
            return _mapper.Map<IEnumerable<SimplePostDto>>(posts);
        }

        public async Task<IEnumerable<SimplePostDto>> GetSimpleByBlogIdAsync(int blogId)
        {
            var posts = await _dbContext.GetPostsByBlogIdAsync(blogId);
            return _mapper.Map<IEnumerable<SimplePostDto>>(posts);
        }

        private async Task SetupSecondaryDataAsync(PostDto dto)
        {
            var blogs = await _dbContext.GetAllBlogsAsync();
            var tags = await _dbContext.GetAllTagsAsync();

            dto.Bloggers.SetupDropDownListContent(
                blogs.Select(b => new KeyValuePair<string, string>(b.Name, b.BlogId.ToString("D"))),
                "--- choose blogger ---");

            if (dto.PostId != 0)
                dto.Bloggers.SetSelectedValue(dto.BlogId.ToString("D"));

            var preselectedTags = dto.PostId == 0
                ? new List<KeyValuePair<string, int>>()
                : dto.Tags?.Select(t => new KeyValuePair<string, int>(t.Name, t.TagId)).ToList()
                  ?? new List<KeyValuePair<string, int>>();

            dto.UserChosenTags.SetupMultiSelectList(
                tags.Select(t => new KeyValuePair<string, int>(t.Name, t.TagId)),
                preselectedTags);
        }
    }
}
