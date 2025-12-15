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
        private readonly ITagRepository _repository;

        public TagService(IMapper mapper, ITagRepository repository)
        {
            _mapper = mapper;
            _repository = repository;
        }

        public async Task<IEnumerable<TagDto>> GetAllAsync()
        {
            var tags = await _repository.GetAllTagsAsync();
            return _mapper.Map<IEnumerable<TagDto>>(tags);
        }

        public async Task<IEnumerable<TagDto>> GetFilteredAsync(Expression<Func<TagDto, bool>> filter)
        {
            var tags = await _repository.GetAllTagsAsync();
            var dtos = _mapper.Map<IEnumerable<TagDto>>(tags);
            return dtos.AsQueryable().Where(filter);
        }

        public async Task<PagedResult<TagDto>> GetPagedAsync(int page, int pageSize)
        {
            var (tags, totalCount) = await _repository.GetPagedTagsAsync(page, pageSize);
            var dtos = _mapper.Map<IEnumerable<TagDto>>(tags);
            return new PagedResult<TagDto>(dtos, totalCount, page, pageSize);
        }

        public async Task<TagDto> GetByIdAsync(int id)
        {
            var tag = await _repository.GetTagByIdAsync(id);
            return _mapper.Map<TagDto>(tag);
        }

        public async Task<TagDto> GetBySlugAsync(string slug)
        {
            var tag = await _repository.GetTagBySlugAsync(slug);
            return _mapper.Map<TagDto>(tag);
        }

        public async Task<CreateResult> CreateAsync(TagDto dto)
        {
            try
            {
                var id = await _repository.CreateTagAsync(dto);
                return CreateResult.Success($"Tag '{dto.Name}' created successfully", id);
            }
            catch (Exception ex)
            {
                return CreateResult.Failure(ex.Message);
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
                await _repository.UpdateTagAsync(dto);
                return UpdateResult.Success($"Tag '{dto.Name}' updated successfully");
            }
            catch (Exception ex)
            {
                return UpdateResult.Failure(ex.Message);
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
                await _repository.DeleteTagAsync(id);
                return DeleteResult.Success("Tag deleted successfully");
            }
            catch (Exception ex)
            {
                return DeleteResult.Failure(ex.Message);
            }
        }
    }

    public interface ITagRepository
    {
        Task<IEnumerable<object>> GetAllTagsAsync();
        Task<(IEnumerable<object> tags, int totalCount)> GetPagedTagsAsync(int page, int pageSize);
        Task<object> GetTagByIdAsync(int id);
        Task<object> GetTagBySlugAsync(string slug);
        Task<int> CreateTagAsync(TagDto dto);
        Task UpdateTagAsync(TagDto dto);
        Task DeleteTagAsync(int id);
    }
}
