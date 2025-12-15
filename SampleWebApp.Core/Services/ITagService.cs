using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Services
{
    public interface ITagService :
        IListService<TagDto>,
        IDetailService<TagDto>,
        ICreateService<TagDto>,
        IUpdateService<TagDto>,
        IDeleteService<TagDto>
    {
    }
}
