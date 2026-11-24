#region licence
// The MIT License (MIT)
// 
// Filename: DetailPostDtoAsync.cs
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
using System.Threading.Tasks;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public class DetailPostDtoAsync : DetailPostDtoBase, ILinkToEntity<Post>
    {
        /// <summary>
        /// This sets up the dropdownlist for the possible bloggers and the MultiSelectList of tags
        /// </summary>
        public async Task<DetailPostDtoAsync> SetupSecondaryDataAsync(SampleWebAppDb db)
        {
            FillSecondaryData(await db.Blogs.ToListAsync(), await db.Tags.ToListAsync());
            return this;
        }

        /// <summary>
        /// This sets the BlogId and Tags from the user's selections. On update it loads the post's current tags
        /// so that the GenericServices update replaces them.
        /// </summary>
        public async Task<IStatusGeneric> SetupRestOfDtoAsync(SampleWebAppDb db)
        {
            var blogId = Bloggers.SelectedValueAsInt;
            var blogger = blogId == null ? null : await db.Blogs.FindAsync((int)blogId);
            var requiredTagIds = UserChosenTags.GetFinalSelectionAsInts();
            var newTagsForPost = await db.Tags.Where(x => requiredTagIds.Contains(x.TagId)).ToListAsync();

            var status = CheckSelections(blogger, requiredTagIds, newTagsForPost.Count);
            if (status.IsValid)
            {
                if (PostId != 0)
                    await PostWithTags(db, PostId).SingleOrDefaultAsync();
                Tags = newTagsForPost;
            }
            return status;
        }
    }
}
