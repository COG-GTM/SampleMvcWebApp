using System.Linq;
using System.Threading.Tasks;
using GenericLibsBase.Core;

namespace GenericServices
{
    //-------------------------------------------------------------------
    // Synchronous service contracts

    public interface IListService
    {
        IQueryable<T> GetAll<T>() where T : class;
    }

    public interface IDetailService
    {
        ISuccessOrErrors<T> GetDetail<T>(params object[] keys) where T : class;
    }

    public interface IUpdateSetupService
    {
        ISuccessOrErrors<T> GetOriginal<T>(params object[] keys) where T : class;
    }

    public interface IUpdateService
    {
        ISuccessOrErrors Update<T>(T item) where T : class;
        T ResetDto<T>(T dto) where T : class;
    }

    public interface ICreateSetupService
    {
        T GetDto<T>() where T : class;
    }

    public interface ICreateService
    {
        ISuccessOrErrors Create<T>(T item) where T : class;
        T ResetDto<T>(T dto) where T : class;
    }

    public interface IDeleteService
    {
        ISuccessOrErrors Delete<T>(params object[] keys) where T : class;
    }

    //-------------------------------------------------------------------
    // Asynchronous service contracts

    public interface IDetailServiceAsync
    {
        Task<ISuccessOrErrors<T>> GetDetailAsync<T>(params object[] keys) where T : class;
    }

    public interface IUpdateSetupServiceAsync
    {
        Task<ISuccessOrErrors<T>> GetOriginalAsync<T>(params object[] keys) where T : class;
    }

    public interface IUpdateServiceAsync
    {
        Task<ISuccessOrErrors> UpdateAsync<T>(T item) where T : class;
        Task<T> ResetDtoAsync<T>(T dto) where T : class;
    }

    public interface ICreateSetupServiceAsync
    {
        Task<T> GetDtoAsync<T>() where T : class;
    }

    public interface ICreateServiceAsync
    {
        Task<ISuccessOrErrors> CreateAsync<T>(T item) where T : class;
        Task<T> ResetDtoAsync<T>(T dto) where T : class;
    }

    public interface IDeleteServiceAsync
    {
        Task<ISuccessOrErrors> DeleteAsync<T>(params object[] keys) where T : class;
    }
}
