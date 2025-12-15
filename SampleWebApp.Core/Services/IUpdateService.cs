using System.Threading.Tasks;
using SampleWebApp.Core.Common;
using SampleWebApp.Core.Common.Results;

namespace SampleWebApp.Core.Services
{
    public interface IUpdateService<TDto> where TDto : BaseDto
    {
        Task<UpdateResult> UpdateAsync(TDto dto);
        Task<TDto> GetUpdateDtoAsync(int id);
        Task<TDto> ResetDtoAsync(TDto dto);
    }
}
