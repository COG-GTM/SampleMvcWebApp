using StatusGeneric;

namespace ServiceLayer.PostServices
{
    /// <summary>
    /// Async version of <see cref="IDetailPostService"/>, working on DetailPostDtoAsync
    /// </summary>
    public interface IDetailPostServiceAsync
    {
        Task<DetailPostDtoAsync> GetDetailAsync(int postId);

        Task<DetailPostDtoAsync> GetNewAsync();

        Task<DetailPostDtoAsync> GetForEditAsync(int postId);

        Task<DetailPostDtoAsync> ResetDtoAsync(DetailPostDtoAsync dto);

        Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto);

        Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto);

        /// <summary>
        /// The status of the last GetDetailAsync/GetForEditAsync call
        /// </summary>
        IStatusGeneric Status { get; }
    }
}
