using System.Threading.Tasks;
using SampleWebApp.Core.Common;

namespace SampleWebApp.Core.Services
{
    public interface IDetailService<TDto> where TDto : BaseDto
    {
        Task<TDto> GetByIdAsync(int id);
        Task<TDto> GetBySlugAsync(string slug);
    }
}
