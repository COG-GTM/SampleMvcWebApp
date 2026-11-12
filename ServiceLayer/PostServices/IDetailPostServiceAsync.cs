using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public interface IDetailPostServiceAsync
    {
        IStatusGeneric Status { get; }
        Task<DetailPostDtoAsync> GetDetailAsync(int postId);
        Task<DetailPostDtoAsync> GetNewAsync();
        Task<DetailPostDtoAsync> GetForEditAsync(int postId);
        Task<DetailPostDtoAsync> ResetDtoAsync(DetailPostDtoAsync dto);
        Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto);
        Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto);
    }
}
