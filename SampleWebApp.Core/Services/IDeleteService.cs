using System.Threading.Tasks;
using SampleWebApp.Core.Common;
using SampleWebApp.Core.Common.Results;

namespace SampleWebApp.Core.Services
{
    public interface IDeleteService<TDto> where TDto : BaseDto
    {
        Task<DeleteResult> DeleteAsync(int id);
    }
}
