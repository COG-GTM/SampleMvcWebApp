using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    /// <summary>
    /// The shared code for the sync and async detail post services
    /// </summary>
    public abstract class DetailPostServiceBase<TDto> where TDto : class, IDetailPostDto, new()
    {
        internal const string BloggerPrompt = "--- choose blogger ---";

        protected readonly SampleWebAppDb Db;

        protected DetailPostServiceBase(SampleWebAppDb db)
        {
            Db = db;
            Status = new StatusGenericHandler();
        }

        public IStatusGeneric Status { get; protected set; }

        //---------------------------------------------------
        //setting up the lists

        protected void SetupSecondaryData(TDto dto, List<Blog> blogs, List<Tag> allTags, string selectedBlogValue, IEnumerable<int> selectedTagIds)
        {
            dto.Bloggers ??= new UiClasses.DropDownListType();
            dto.UserChosenTags ??= new UiClasses.MultiSelectListType();

            dto.Bloggers.SetupDropDownListContent(
                blogs.Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                BloggerPrompt);
            if (selectedBlogValue != null)
                dto.Bloggers.SetSelectedValue(selectedBlogValue);

            var allOptions = allTags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)).ToList();
            var selected = selectedTagIds.ToList();
            dto.UserChosenTags.SetupMultiSelectList(
                allOptions,
                selected.Select(id => allOptions.SingleOrDefault(o => o.Value == id))
                    .Where(o => o.Key != null).ToList());
        }

        protected static string SelectedBlogValueForExisting(TDto dto)
        {
            return dto.PostId != 0 ? dto.BlogId.ToString("D") : null;
        }

        protected static IEnumerable<int> SelectedTagIdsForExisting(TDto dto)
        {
            return dto.PostId != 0 && dto.Tags != null
                ? dto.Tags.Select(x => x.TagId)
                : Enumerable.Empty<int>();
        }

        protected static string SelectedBlogValueFromUser(TDto dto)
        {
            return dto.Bloggers?.SelectedValue ?? SelectedBlogValueForExisting(dto);
        }

        protected static IEnumerable<int> SelectedTagIdsFromUser(TDto dto)
        {
            return dto.UserChosenTags?.FinalSelection != null
                ? GetUserTagIdsOrEmpty(dto)
                : SelectedTagIdsForExisting(dto);
        }

        protected IQueryable<Blog> BlogsForList()
        {
            return Db.Blogs.AsNoTracking().OrderBy(x => x.BlogId);
        }

        protected IQueryable<Tag> TagsForList()
        {
            return Db.Tags.AsNoTracking().OrderBy(x => x.TagId);
        }

        //---------------------------------------------------
        //create/update helpers

        /// <summary>
        /// Checks the user's blogger and tag selections, setting BlogId and Tags on the dto.
        /// Returns the selected tags (tracked), or null if there are errors in the status
        /// </summary>
        protected List<Tag> SetupRestOfDto(TDto dto, StatusGenericHandler status, bool bloggerExists, List<Tag> foundTags, int[] requiredTagIds)
        {
            var blogId = dto.Bloggers?.SelectedValueAsInt;
            if (blogId == null)
                status.AddError("The blogger was not selected. You must do that before the post can be saved.", "Bloggers");
            else if (!bloggerExists)
                status.AddError("Could not find the blogger you selected. Did another user delete it?", "Bloggers");
            else
                dto.BlogId = (int)blogId;

            if (requiredTagIds == null || !requiredTagIds.Any())
                status.AddError("You must select at least one tag for the post.", "UserChosenTags");
            else if (foundTags.Count != requiredTagIds.Distinct().Count())
                status.AddError("Could not find one of the tags. Did another user delete it?", "UserChosenTags");
            else
                dto.Tags = foundTags;

            return status.IsValid ? foundTags : null;
        }

        protected static int[] GetUserTagIds(TDto dto)
        {
            return dto.UserChosenTags?.FinalSelection == null
                ? new int[0]
                : dto.UserChosenTags.GetFinalSelectionAsInts();
        }

        protected static void ReplaceTags(Post post, List<Tag> newTags)
        {
            post.Tags ??= new List<Tag>();
            foreach (var tag in post.Tags.Where(t => newTags.All(n => n.TagId != t.TagId)).ToList())
                post.Tags.Remove(tag);
            foreach (var tag in newTags.Where(n => post.Tags.All(t => t.TagId != n.TagId)))
                post.Tags.Add(tag);
        }

        protected static void CopyDtoToPost(TDto dto, Post post)
        {
            post.Title = dto.Title;
            post.Content = dto.Content;
            post.BlogId = dto.BlogId;
        }

        protected static IStatusGeneric PostNotFound(StatusGenericHandler status)
        {
            status.AddError("Sorry, I could not find the Post you were looking for.");
            return status;
        }

        private static IEnumerable<int> GetUserTagIdsOrEmpty(TDto dto)
        {
            try
            {
                return dto.UserChosenTags.GetFinalSelectionAsInts();
            }
            catch (ArgumentException)
            {
                return Enumerable.Empty<int>();
            }
        }
    }
}
