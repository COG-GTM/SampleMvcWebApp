using System.Linq;
using System.Threading.Tasks;
using GenericLibsBase.Core;
using GenericServices.Core;

namespace GenericServices
{
    public interface IListService
    {
        IQueryable<TDto> GetAll<TDto>() where TDto : EfGenericDtoBase, new();
    }

    public interface IDetailService
    {
        ISuccessOrErrors<T> GetDetail<T>(int id) where T : class, new();
    }

    public interface IDetailServiceAsync
    {
        Task<ISuccessOrErrors<T>> GetDetailAsync<T>(int id) where T : class, new();
    }

    public interface ICreateSetupService
    {
        TDto GetDto<TDto>() where TDto : EfGenericDtoBase, new();
    }

    public interface ICreateSetupServiceAsync
    {
        Task<TDto> GetDtoAsync<TDto>() where TDto : EfGenericDtoBase, new();
    }

    public interface ICreateService
    {
        ISuccessOrErrors Create<T>(T item) where T : class;
        T ResetDto<T>(T dto) where T : class;
    }

    public interface ICreateServiceAsync
    {
        Task<ISuccessOrErrors> CreateAsync<T>(T item) where T : class;
        Task<T> ResetDtoAsync<T>(T dto) where T : class;
    }

    public interface IUpdateSetupService
    {
        ISuccessOrErrors<T> GetOriginal<T>(int id) where T : class, new();
    }

    public interface IUpdateSetupServiceAsync
    {
        Task<ISuccessOrErrors<T>> GetOriginalAsync<T>(int id) where T : class, new();
    }

    public interface IUpdateService
    {
        ISuccessOrErrors Update<T>(T item) where T : class;
        T ResetDto<T>(T dto) where T : class;
    }

    public interface IUpdateServiceAsync
    {
        Task<ISuccessOrErrors> UpdateAsync<T>(T item) where T : class;
        Task<T> ResetDtoAsync<T>(T dto) where T : class;
    }

    public interface IDeleteService
    {
        ISuccessOrErrors Delete<T>(int id) where T : class;
    }

    public interface IDeleteServiceAsync
    {
        Task<ISuccessOrErrors> DeleteAsync<T>(int id) where T : class;
    }
}
