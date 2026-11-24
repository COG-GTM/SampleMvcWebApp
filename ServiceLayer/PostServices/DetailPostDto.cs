#region licence
// The MIT License (MIT)
// 
// Filename: DetailPostDto.cs
// Date Created: 2014/08/16
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
using System.Linq;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public class DetailPostDto : DetailPostDtoBase, ILinkToEntity<Post>
    {
        /// <summary>
        /// This sets up the dropdownlist for the possible bloggers and the MultiSelectList of tags
        /// </summary>
        public DetailPostDto SetupSecondaryData(SampleWebAppDb db)
        {
            FillSecondaryData(db.Blogs.ToList(), db.Tags.ToList());
            return this;
        }

        /// <summary>
        /// This sets the BlogId and Tags from the user's selections. On update it loads the post's current tags
        /// so that the GenericServices update replaces them.
        /// </summary>
        public IStatusGeneric SetupRestOfDto(SampleWebAppDb db)
        {
            var blogId = Bloggers.SelectedValueAsInt;
            var blogger = blogId == null ? null : db.Blogs.Find((int)blogId);
            var requiredTagIds = UserChosenTags.GetFinalSelectionAsInts();
            var newTagsForPost = db.Tags.Where(x => requiredTagIds.Contains(x.TagId)).ToList();

            var status = CheckSelections(blogger, requiredTagIds, newTagsForPost.Count);
            if (status.IsValid)
            {
                if (PostId != 0)
                    PostWithTags(db, PostId).SingleOrDefault();
                Tags = newTagsForPost;
            }
            return status;
        }
    }
}
