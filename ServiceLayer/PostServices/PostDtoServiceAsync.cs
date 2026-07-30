#region licence
// The MIT License (MIT)
// 
// Filename: PostDtoServiceAsync.cs
// Date Created: 2014/05/20
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
using System.Threading.Tasks;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public class PostDtoServiceAsync : IPostDtoServiceAsync
    {
        private readonly SampleWebAppDb _db;

        public PostDtoServiceAsync(SampleWebAppDb db)
        {
            _db = db;
        }

        public async Task<DetailPostDtoAsync> GetDtoForCreateAsync()
        {
            var dto = new DetailPostDtoAsync();
            await SetupSecondaryDataAsync(dto).ConfigureAwait(false);
            return dto;
        }

        public async Task<DetailPostDtoAsync> GetDtoForUpdateAsync(int postId)
        {
            var post = await LoadPostAsync(postId).ConfigureAwait(false);
            if (post == null)
                return null;

            var dto = new DetailPostDtoAsync
            {
                PostId = post.PostId,
                Title = post.Title,
                Content = post.Content,
                BlogId = post.BlogId,
                BloggerName = post.Blogger == null ? null : post.Blogger.Name,
                LastUpdated = post.LastUpdated,
                Tags = post.Tags.ToList()
            };
            await SetupSecondaryDataAsync(dto).ConfigureAwait(false);
            return dto;
        }

        public async Task ResetSecondaryDataAsync(DetailPostDtoAsync dto)
        {
            if (dto.PostId != 0 && dto.Tags == null)
                //the Tags aren't sent back by the browser, so reload them to get the preselected tags right
                dto.Tags = await _db.Posts.Where(x => x.PostId == dto.PostId).SelectMany(x => x.Tags)
                    .ToListAsync().ConfigureAwait(false);

            await SetupSecondaryDataAsync(dto).ConfigureAwait(false);
        }

        public async Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto)
        {
            var status = await SetupRestOfDtoAsync(dto).ConfigureAwait(false);
            if (!status.IsValid)
                return status;

            var post = new Post();
            CopyDtoToPost(dto, post);
            _db.Posts.Add(post);

            var saveStatus = await _db.SaveChangesWithValidationAsync().ConfigureAwait(false);
            if (saveStatus.IsValid)
                dto.PostId = post.PostId;
            return saveStatus;
        }

        public async Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto)
        {
            var post = await LoadPostAsync(dto.PostId).ConfigureAwait(false);
            if (post == null)
            {
                var notFound = new StatusGenericHandler();
                notFound.AddError(PostDtoService.PostNotFound);
                return notFound;
            }

            var status = await SetupRestOfDtoAsync(dto).ConfigureAwait(false);
            if (!status.IsValid)
                return status;

            CopyDtoToPost(dto, post);
            return await _db.SaveChangesWithValidationAsync().ConfigureAwait(false);
        }

        //---------------------------------------------------
        //private helpers

        private Task<Post> LoadPostAsync(int postId)
        {
            return _db.Posts
                .Include(x => x.Blogger)
                .Include(x => x.Tags)
                .SingleOrDefaultAsync(x => x.PostId == postId);
        }

        private static void CopyDtoToPost(DetailPostDtoAsync dto, Post post)
        {
            post.Title = dto.Title;
            post.Content = dto.Content;
            post.BlogId = dto.BlogId;
            post.Tags = dto.Tags;
        }

        /// <summary>
        /// This sets up the dropdownlist for the possible bloggers and the MultiSelectList of tags
        /// </summary>
        private async Task SetupSecondaryDataAsync(DetailPostDtoAsync dto)
        {
            var bloggers = await _db.Blogs.ToListAsync().ConfigureAwait(false);

            dto.Bloggers.SetupDropDownListContent(
                bloggers.Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                "--- choose blogger ---");
            if (dto.PostId != 0)
                //there is an entry, so set the selected value to that
                dto.Bloggers.SetSelectedValue(dto.BlogId.ToString("D"));

            var preselectedTags = dto.PostId == 0 || dto.Tags == null
                ? new List<KeyValuePair<string, int>>()     //Create, so no tags selected yet
                : dto.Tags
                    .Select(x => new KeyValuePair<string, int>(x.Name, x.TagId))
                    .ToList();
            var allTags = await _db.Tags.ToListAsync().ConfigureAwait(false);
            dto.UserChosenTags.SetupMultiSelectList(
                allTags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)), preselectedTags);
        }

        private async Task<IStatusGeneric> SetupRestOfDtoAsync(DetailPostDtoAsync dto)
        {
            var status = new StatusGenericHandler();

            //now we sort out the blogger
            var errMsg = await SetBloggerIdFromDropDownListAsync(dto).ConfigureAwait(false);
            if (errMsg != null)
                status.AddError(errMsg, "Bloggers");

            //now we sort out the tags
            errMsg = await ChangeTagsBasedOnMultiSelectListAsync(dto).ConfigureAwait(false);
            if (errMsg != null)
                status.AddError(errMsg, "UserChosenTags");

            return status;
        }

        private async Task<string> SetBloggerIdFromDropDownListAsync(DetailPostDtoAsync dto)
        {
            var blogId = dto.Bloggers.SelectedValueAsInt;
            if (blogId == null)
                return PostDtoService.NoBloggerSelected;

            var blogger = await _db.Blogs.FindAsync((int)blogId).ConfigureAwait(false);
            if (blogger == null)
                return PostDtoService.BloggerNotFound;

            dto.BlogId = (int)blogId;
            return null;
        }

        private async Task<string> ChangeTagsBasedOnMultiSelectListAsync(DetailPostDtoAsync dto)
        {
            var requiredTagIds = dto.UserChosenTags.GetFinalSelectionAsInts();
            if (!requiredTagIds.Any())
                return PostDtoService.NoTagsSelected;

            var newTagsForPost = await _db.Tags.Where(x => requiredTagIds.Contains(x.TagId))
                .ToListAsync().ConfigureAwait(false);
            if (newTagsForPost.Count != requiredTagIds.Distinct().Count())
                return PostDtoService.TagNotFound;

            dto.Tags = newTagsForPost;
            return null;
        }
    }
}
