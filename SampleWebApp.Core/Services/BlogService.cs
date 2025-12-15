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
    public class BlogService : IBlogService
    {
        private readonly IMapper _mapper;
        private readonly IDbContextAdapter _dbContext;

        public BlogService(IDbContextAdapter dbContext, IMapper mapper)
        {
            _dbContext = dbContext;
            _mapper = mapper;
        }

        public async Task<IEnumerable<BlogDto>> GetAllAsync()
        {
            var blogs = await _dbContext.GetAllBlogsAsync();
            return _mapper.Map<IEnumerable<BlogDto>>(blogs);
        }

        public async Task<IEnumerable<BlogDto>> GetFilteredAsync(Expression<Func<BlogDto, bool>> filter)
        {
            var allBlogs = await GetAllAsync();
            return allBlogs.AsQueryable().Where(filter);
        }

        public async Task<PagedResult<BlogDto>> GetPagedAsync(int page, int pageSize)
        {
            var allBlogs = await GetAllAsync();
            var blogsList = allBlogs.ToList();
            var pagedBlogs = blogsList.Skip((page - 1) * pageSize).Take(pageSize);
            return new PagedResult<BlogDto>(pagedBlogs, blogsList.Count, page, pageSize);
        }

        public async Task<BlogDto> GetByIdAsync(int id)
        {
            var blog = await _dbContext.GetBlogByIdAsync(id);
            return _mapper.Map<BlogDto>(blog);
        }

        public async Task<BlogDto> GetBySlugAsync(string slug)
        {
            return await Task.FromResult<BlogDto>(null);
        }

        public async Task<CreateResult> CreateAsync(BlogDto dto)
        {
            try
            {
                var blogId = await _dbContext.CreateBlogAsync(dto.Name, dto.EmailAddress);
                return CreateResult.Success($"Blog '{dto.Name}' created successfully", blogId);
            }
            catch (Exception ex)
            {
                return CreateResult.Fail(ex.Message);
            }
        }

        public async Task<BlogDto> GetCreateDtoAsync()
        {
            return await Task.FromResult(new BlogDto());
        }

        public async Task<UpdateResult> UpdateAsync(BlogDto dto)
        {
            try
            {
                await _dbContext.UpdateBlogAsync(dto.BlogId, dto.Name, dto.EmailAddress);
                return UpdateResult.Success($"Blog '{dto.Name}' updated successfully");
            }
            catch (Exception ex)
            {
                return UpdateResult.Fail(ex.Message);
            }
        }

        public async Task<BlogDto> GetUpdateDtoAsync(int id)
        {
            return await GetByIdAsync(id);
        }

        public async Task<BlogDto> ResetDtoAsync(BlogDto dto)
        {
            return await Task.FromResult(dto);
        }

        public async Task<DeleteResult> DeleteAsync(int id)
        {
            try
            {
                await _dbContext.DeleteBlogAsync(id);
                return DeleteResult.Success("Blog deleted successfully");
            }
            catch (Exception ex)
            {
                return DeleteResult.Fail(ex.Message);
            }
        }
    }
}
