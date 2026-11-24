#region licence
// The MIT License (MIT)
// 
// Filename: PostServiceHelpers.cs
// Date Created: 2026/11/24
// 
// Copyright (c) 2014 Jon Smith (www.selectiveanalytics.com & www.thereformedprogrammer.net)
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
#endregion
using System.Collections.Generic;
using System.Linq;
using DataLayer.DataClasses.Concrete;
using ServiceLayer.UiClasses;

namespace ServiceLayer.PostServices
{
    internal static class PostServiceHelpers
    {
        public const string BloggerNotSelected = "The blogger was not selected. You must do that before the post can be saved.";
        public const string BloggerNotFound = "Could not find the blogger you selected. Did another user delete it?";
        public const string NoTagsSelected = "You must select at least one tag for the post.";
        public const string TagNotFound = "Could not find one of the tags. Did another user delete it?";

        /// <summary>
        /// This sets up the dropdownlist for the possible bloggers and the MultiSelectList of tags,
        /// keeping any selection the user has already made
        /// </summary>
        public static void SetupLists(DropDownListType bloggers, MultiSelectListType userChosenTags,
            int postId, int blogId, ICollection<Tag> postTags, List<Blog> allBlogs, List<Tag> allTags)
        {
            var userSelectedBlogger = bloggers.SelectedValue;
            bloggers.SetupDropDownListContent(
                allBlogs.Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                "--- choose blogger ---");
            if (!string.IsNullOrEmpty(userSelectedBlogger))
                bloggers.SetSelectedValue(userSelectedBlogger);
            else if (postId != 0)
                bloggers.SetSelectedValue(blogId.ToString("D"));

            var allOptions = allTags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)).ToList();
            List<KeyValuePair<string, int>> preselectedTags;
            if (userChosenTags.FinalSelection != null)
            {
                var chosenIds = userChosenTags.FinalSelection
                    .Select(x => int.TryParse(x, out var id) ? id : (int?)null)
                    .Where(x => x != null)
                    .ToList();
                preselectedTags = chosenIds
                    .SelectMany(id => allOptions.Where(x => x.Value == id))
                    .ToList();
            }
            else if (postId != 0 && postTags != null)
                preselectedTags = postTags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)).ToList();
            else
                preselectedTags = new List<KeyValuePair<string, int>>();

            userChosenTags.SetupMultiSelectList(allOptions, preselectedTags);
        }
    }
}
