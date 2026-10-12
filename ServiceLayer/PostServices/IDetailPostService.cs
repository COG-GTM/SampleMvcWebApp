using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public interface IDetailPostService
    {
        IStatusGeneric Status { get; }
        DetailPostDto GetDetail(int postId);
        DetailPostDto GetNew();
        DetailPostDto GetForEdit(int postId);
        DetailPostDto ResetDto(DetailPostDto dto);
        IStatusGeneric Create(DetailPostDto dto);
        IStatusGeneric Update(DetailPostDto dto);
    }
}
