using StatusGeneric;

namespace ServiceLayer.PostServices
{
    /// <summary>
    /// Handles the DetailPostDto, which needs the Bloggers dropdown and Tags multi-select setting up
    /// and the Post's Blogger and Tags set from the user's selections on create/update.
    /// </summary>
    public interface IDetailPostService
    {
        /// <summary>
        /// Returns the post with the lists set up, or null (with an invalid Status) if not found
        /// </summary>
        DetailPostDto GetDetail(int postId);

        /// <summary>
        /// Returns an empty dto with the lists set up, ready for a create
        /// </summary>
        DetailPostDto GetNew();

        /// <summary>
        /// Returns the post with the lists set up and the current blogger/tags preselected, or null (with an invalid Status) if not found
        /// </summary>
        DetailPostDto GetForEdit(int postId);

        /// <summary>
        /// Re-populates the lists of a dto that has come back from the user (e.g. after a validation error),
        /// keeping the user's selections
        /// </summary>
        DetailPostDto ResetDto(DetailPostDto dto);

        IStatusGeneric Create(DetailPostDto dto);

        IStatusGeneric Update(DetailPostDto dto);

        /// <summary>
        /// The status of the last GetDetail/GetForEdit call
        /// </summary>
        IStatusGeneric Status { get; }
    }
}
