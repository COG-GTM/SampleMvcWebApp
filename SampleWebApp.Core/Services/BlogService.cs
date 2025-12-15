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
        private readonly IBlogRepository _repository;

        public BlogService(IMapper mapper, IBlogRepository repository)
        {
            _mapper = mapper;
            _repository = repository;
        }

        public async Task<IEnumerable<BlogDto>> GetAllAsync()
        {
            var blogs = await _repository.GetAllBlogsAsync();
            return _mapper.Map<IEnumerable<BlogDto>>(blogs);
        }

        public async Task<IEnumerable<BlogDto>> GetFilteredAsync(Expression<Func<BlogDto, bool>> filter)
        {
            var blogs = await _repository.GetAllBlogsAsync();
            var dtos = _mapper.Map<IEnumerable<BlogDto>>(blogs);
            return dtos.AsQueryable().Where(filter);
        }

        public async Task<PagedResult<BlogDto>> GetPagedAsync(int page, int pageSize)
        {
            var (blogs, totalCount) = await _repository.GetPagedBlogsAsync(page, pageSize);
            var dtos = _mapper.Map<IEnumerable<BlogDto>>(blogs);
            return new PagedResult<BlogDto>(dtos, totalCount, page, pageSize);
        }

        public async Task<BlogDto> GetByIdAsync(int id)
        {
            var blog = await _repository.GetBlogByIdAsync(id);
            return _mapper.Map<BlogDto>(blog);
        }

        public Task<BlogDto> GetBySlugAsync(string slug)
        {
            throw new NotImplementedException("Blogs do not have slugs");
        }

        public async Task<CreateResult> CreateAsync(BlogDto dto)
        {
            try
            {
                var id = await _repository.CreateBlogAsync(dto);
                return CreateResult.Success($"Blog '{dto.Name}' created successfully", id);
            }
            catch (Exception ex)
            {
                return CreateResult.Failure(ex.Message);
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
                await _repository.UpdateBlogAsync(dto);
                return UpdateResult.Success($"Blog '{dto.Name}' updated successfully");
            }
            catch (Exception ex)
            {
                return UpdateResult.Failure(ex.Message);
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
                await _repository.DeleteBlogAsync(id);
                return DeleteResult.Success("Blog deleted successfully");
            }
            catch (Exception ex)
            {
                return DeleteResult.Failure(ex.Message);
            }
        }
    }

    public interface IBlogRepository
    {
        Task<IEnumerable<object>> GetAllBlogsAsync();
        Task<(IEnumerable<object> blogs, int totalCount)> GetPagedBlogsAsync(int page, int pageSize);
        Task<object> GetBlogByIdAsync(int id);
        Task<int> CreateBlogAsync(BlogDto dto);
        Task UpdateBlogAsync(BlogDto dto);
        Task DeleteBlogAsync(int id);
    }
}
