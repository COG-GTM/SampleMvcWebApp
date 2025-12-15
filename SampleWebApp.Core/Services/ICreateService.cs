using System.Threading.Tasks;
using SampleWebApp.Core.Common;
using SampleWebApp.Core.Common.Results;

namespace SampleWebApp.Core.Services
{
    public interface ICreateService<TDto> where TDto : BaseDto
    {
        Task<CreateResult> CreateAsync(TDto dto);
        Task<TDto> GetCreateDtoAsync();
    }
}
