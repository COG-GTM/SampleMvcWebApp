using System.Collections.Generic;
using System.Threading.Tasks;
using SampleWebApp.Core.DTOs;

namespace SampleWebApp.Core.Services
{
    public interface IPostService :
        IListService<PostDto>,
        IDetailService<PostDto>,
        ICreateService<PostDto>,
        IUpdateService<PostDto>,
        IDeleteService<PostDto>
    {
        Task<IEnumerable<PostDto>> GetByBlogIdAsync(int blogId);
        Task<IEnumerable<SimplePostDto>> GetAllSimpleAsync();
        Task<IEnumerable<SimplePostDto>> GetSimpleByBlogIdAsync(int blogId);
    }
}
