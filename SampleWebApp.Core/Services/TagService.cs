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
    public class TagService : ITagService
    {
        private readonly IMapper _mapper;
        private readonly IDbContextAdapter _dbContext;

        public TagService(IDbContextAdapter dbContext, IMapper mapper)
        {
            _dbContext = dbContext;
            _mapper = mapper;
        }

        public async Task<IEnumerable<TagDto>> GetAllAsync()
        {
            var tags = await _dbContext.GetAllTagsAsync();
            return _mapper.Map<IEnumerable<TagDto>>(tags);
        }

        public async Task<IEnumerable<TagDto>> GetFilteredAsync(Expression<Func<TagDto, bool>> filter)
        {
            var allTags = await GetAllAsync();
            return allTags.AsQueryable().Where(filter);
        }

        public async Task<PagedResult<TagDto>> GetPagedAsync(int page, int pageSize)
        {
            var allTags = await GetAllAsync();
            var tagsList = allTags.ToList();
            var pagedTags = tagsList.Skip((page - 1) * pageSize).Take(pageSize);
            return new PagedResult<TagDto>(pagedTags, tagsList.Count, page, pageSize);
        }

        public async Task<TagDto> GetByIdAsync(int id)
        {
            var tag = await _dbContext.GetTagByIdAsync(id);
            return _mapper.Map<TagDto>(tag);
        }

        Task<TagDto> IDetailService<TagDto>.GetBySlugAsync(string slug)
        {
            return GetBySlugAsync(slug);
        }

        public async Task<TagDto> GetBySlugAsync(string slug)
        {
            var tag = await _dbContext.GetTagBySlugAsync(slug);
            return _mapper.Map<TagDto>(tag);
        }

        public async Task<CreateResult> CreateAsync(TagDto dto)
        {
            try
            {
                var tagId = await _dbContext.CreateTagAsync(dto.Name, dto.Slug);
                return CreateResult.Success($"Tag '{dto.Name}' created successfully", tagId);
            }
            catch (Exception ex)
            {
                return CreateResult.Fail(ex.Message);
            }
        }

        public async Task<TagDto> GetCreateDtoAsync()
        {
            return await Task.FromResult(new TagDto());
        }

        public async Task<UpdateResult> UpdateAsync(TagDto dto)
        {
            try
            {
                await _dbContext.UpdateTagAsync(dto.TagId, dto.Name, dto.Slug);
                return UpdateResult.Success($"Tag '{dto.Name}' updated successfully");
            }
            catch (Exception ex)
            {
                return UpdateResult.Fail(ex.Message);
            }
        }

        public async Task<TagDto> GetUpdateDtoAsync(int id)
        {
            return await GetByIdAsync(id);
        }

        public async Task<TagDto> ResetDtoAsync(TagDto dto)
        {
            return await Task.FromResult(dto);
        }

        public async Task<DeleteResult> DeleteAsync(int id)
        {
            try
            {
                await _dbContext.DeleteTagAsync(id);
                return DeleteResult.Success("Tag deleted successfully");
            }
            catch (Exception ex)
            {
                return DeleteResult.Fail(ex.Message);
            }
        }
    }
}
