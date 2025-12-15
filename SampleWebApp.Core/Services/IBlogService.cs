using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Services
{
    public interface IBlogService :
        IListService<BlogDto>,
        IDetailService<BlogDto>,
        ICreateService<BlogDto>,
        IUpdateService<BlogDto>,
        IDeleteService<BlogDto>
    {
    }
}
