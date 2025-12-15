using System;
using System.Collections.Generic;
using System.Linq.Expressions;
using System.Threading.Tasks;
using SampleWebApp.Core.Common;
using SampleWebApp.Core.Common.Results;

namespace SampleWebApp.Core.Services
{
    public interface IListService<TDto> where TDto : BaseDto
    {
        Task<IEnumerable<TDto>> GetAllAsync();
        Task<IEnumerable<TDto>> GetFilteredAsync(Expression<Func<TDto, bool>> filter);
        Task<PagedResult<TDto>> GetPagedAsync(int page, int pageSize);
    }
}
