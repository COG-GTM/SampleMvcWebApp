using ServiceLayer.UiClasses;

namespace ServiceLayer.PostServices
{
    internal static class PostServiceHelpers
    {
        public const string BloggerNotSelected = "The blogger was not selected. You must do that before the post can be saved.";
        public const string BloggerNotFound = "Could not find the blogger you selected. Did another user delete it?";
        public const string NoTagSelected = "You must select at least one tag for the post.";
        public const string TagNotFound = "Could not find one of the tags. Did another user delete it?";

        public static IEnumerable<int> SelectedTagIds(MultiSelectListType userChosenTags)
        {
            return (userChosenTags?.FinalSelection ?? Array.Empty<string>())
                .Select(x => int.TryParse(x, out var id) ? (int?)id : null)
                .Where(x => x != null)
                .Select(x => (int)x)
                .ToList();
        }

        public static void SetupLists(DropDownListType bloggers, MultiSelectListType userChosenTags,
            IEnumerable<KeyValuePair<string, string>> allBloggers, IEnumerable<KeyValuePair<string, int>> allTags,
            string selectedBlogger, IEnumerable<int> selectedTagIds)
        {
            bloggers.SetupDropDownListContent(allBloggers, "--- choose blogger ---");
            if (selectedBlogger != null)
                bloggers.SetSelectedValue(selectedBlogger);

            var tagsList = allTags.ToList();
            var preselectedTags = selectedTagIds
                .Select(id => tagsList.FirstOrDefault(t => t.Value == id))
                .Where(t => t.Key != null)
                .ToList();
            userChosenTags.SetupMultiSelectList(tagsList, preselectedTags);
        }
    }
}
