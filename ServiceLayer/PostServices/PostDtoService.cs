#region licence
// The MIT License (MIT)
// 
// Filename: PostDtoService.cs
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
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    public class PostDtoService : IPostDtoService
    {
        internal const string NoBloggerSelected = "The blogger was not selected. You must do that before the post can be saved.";
        internal const string BloggerNotFound = "Could not find the blogger you selected. Did another user delete it?";
        internal const string NoTagsSelected = "You must select at least one tag for the post.";
        internal const string TagNotFound = "Could not find one of the tags. Did another user delete it?";
        internal const string PostNotFound = "Could not find the post you asked for. Did another user delete it?";

        private readonly SampleWebAppDb _db;

        public PostDtoService(SampleWebAppDb db)
        {
            _db = db;
        }

        public DetailPostDto GetDtoForCreate()
        {
            var dto = new DetailPostDto();
            SetupSecondaryData(dto);
            return dto;
        }

        public DetailPostDto GetDtoForUpdate(int postId)
        {
            var post = LoadPost(postId);
            if (post == null)
                return null;

            var dto = new DetailPostDto
            {
                PostId = post.PostId,
                Title = post.Title,
                Content = post.Content,
                BlogId = post.BlogId,
                BloggerName = post.Blogger == null ? null : post.Blogger.Name,
                LastUpdated = post.LastUpdated,
                Tags = post.Tags.ToList()
            };
            SetupSecondaryData(dto);
            return dto;
        }

        public void ResetSecondaryData(DetailPostDto dto)
        {
            if (dto.PostId != 0 && dto.Tags == null)
                //the Tags aren't sent back by the browser, so reload them to get the preselected tags right
                dto.Tags = _db.Posts.Where(x => x.PostId == dto.PostId).SelectMany(x => x.Tags).ToList();

            SetupSecondaryData(dto);
        }

        public IStatusGeneric Create(DetailPostDto dto)
        {
            var status = SetupRestOfDto(dto);
            if (!status.IsValid)
                return status;

            var post = new Post();
            CopyDtoToPost(dto, post);
            _db.Posts.Add(post);

            var saveStatus = _db.SaveChangesWithValidation();
            if (saveStatus.IsValid)
                dto.PostId = post.PostId;
            return saveStatus;
        }

        public IStatusGeneric Update(DetailPostDto dto)
        {
            var post = LoadPost(dto.PostId);
            if (post == null)
            {
                var notFound = new StatusGenericHandler();
                notFound.AddError(PostNotFound);
                return notFound;
            }

            var status = SetupRestOfDto(dto);
            if (!status.IsValid)
                return status;

            CopyDtoToPost(dto, post);
            return _db.SaveChangesWithValidation();
        }

        //---------------------------------------------------
        //private helpers

        private Post LoadPost(int postId)
        {
            return _db.Posts
                .Include(x => x.Blogger)
                .Include(x => x.Tags)
                .SingleOrDefault(x => x.PostId == postId);
        }

        private static void CopyDtoToPost(DetailPostDto dto, Post post)
        {
            post.Title = dto.Title;
            post.Content = dto.Content;
            post.BlogId = dto.BlogId;
            post.Tags = dto.Tags;
        }

        /// <summary>
        /// This sets up the dropdownlist for the possible bloggers and the MultiSelectList of tags
        /// </summary>
        private void SetupSecondaryData(DetailPostDto dto)
        {
            dto.Bloggers.SetupDropDownListContent(
                _db.Blogs
                    .ToList()
                    .Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                "--- choose blogger ---");
            if (dto.PostId != 0)
                //there is an entry, so set the selected value to that
                dto.Bloggers.SetSelectedValue(dto.BlogId.ToString("D"));

            var preselectedTags = dto.PostId == 0 || dto.Tags == null
                ? new List<KeyValuePair<string, int>>()     //Create, so no tags selected yet
                : dto.Tags
                    .Select(x => new KeyValuePair<string, int>(x.Name, x.TagId))
                    .ToList();
            dto.UserChosenTags.SetupMultiSelectList(
                _db.Tags.ToList().Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)), preselectedTags);
        }

        private IStatusGeneric SetupRestOfDto(DetailPostDto dto)
        {
            var status = new StatusGenericHandler();

            //now we sort out the blogger
            var errMsg = SetBloggerIdFromDropDownList(dto);
            if (errMsg != null)
                status.AddError(errMsg, "Bloggers");

            //now we sort out the tags
            errMsg = ChangeTagsBasedOnMultiSelectList(dto);
            if (errMsg != null)
                status.AddError(errMsg, "UserChosenTags");

            return status;
        }

        private string SetBloggerIdFromDropDownList(DetailPostDto dto)
        {
            var blogId = dto.Bloggers.SelectedValueAsInt;
            if (blogId == null)
                return NoBloggerSelected;

            var blogger = _db.Blogs.Find((int)blogId);
            if (blogger == null)
                return BloggerNotFound;

            dto.BlogId = (int)blogId;
            return null;
        }

        private string ChangeTagsBasedOnMultiSelectList(DetailPostDto dto)
        {
            var requiredTagIds = dto.UserChosenTags.GetFinalSelectionAsInts();
            if (!requiredTagIds.Any())
                return NoTagsSelected;

            var newTagsForPost = _db.Tags.Where(x => requiredTagIds.Contains(x.TagId)).ToList();
            if (newTagsForPost.Count != requiredTagIds.Distinct().Count())
                return TagNotFound;

            dto.Tags = newTagsForPost;
            return null;
        }
    }
}
