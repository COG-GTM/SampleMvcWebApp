using DataLayer.DataClasses.Concrete;
using ServiceLayer.UiClasses;

namespace ServiceLayer.PostServices
{
    /// <summary>
    /// The members shared by DetailPostDto and DetailPostDtoAsync that the detail post services work on
    /// </summary>
    public interface IDetailPostDto
    {
        int PostId { get; set; }
        string Title { get; set; }
        string Content { get; set; }
        int BlogId { get; set; }
        ICollection<Tag> Tags { get; set; }
        DropDownListType Bloggers { get; set; }
        MultiSelectListType UserChosenTags { get; set; }
    }
}
