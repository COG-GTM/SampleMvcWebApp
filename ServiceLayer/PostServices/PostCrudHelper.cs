#region licence
// The MIT License (MIT)
// 
// Filename: PostCrudHelper.cs
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
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Threading.Tasks;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using Microsoft.EntityFrameworkCore;
using ServiceLayer.UiClasses;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    /// <summary>
    /// Hand-written service that replaces the EF6-GenericServices "secondary data" hooks
    /// (<c>SetupSecondaryData</c>, <c>CreateDataFromDto</c>, <c>UpdateDataFromDto</c>) that
    /// <c>EfCore.GenericServices</c> does not provide. It:
    /// <list type="bullet">
    ///   <item>populates the Blogger dropdown and Tags multi-select on a Post detail DTO, and</item>
    ///   <item>creates/updates the Post entity from that DTO, resolving the selected blogger and tags.</item>
    /// </list>
    /// Read operations (list/detail projections) are still done through <c>ICrudServices</c> in the web app.
    /// </summary>
    public interface IPostCrudHelper
    {
        /// <summary>Populates <see cref="DetailPostDto.Bloggers"/> and <see cref="DetailPostDto.UserChosenTags"/>.
        /// Call before rendering the Create/Edit views and on redisplay after a validation error.</summary>
        void SetupSecondaryData(DetailPostDto dto);

        /// <summary>Async version of <see cref="SetupSecondaryData"/> for <see cref="DetailPostDtoAsync"/>.</summary>
        Task SetupSecondaryDataAsync(DetailPostDtoAsync dto);

        /// <summary>Creates a new Post from the DTO. Returns a status carrying any validation errors.</summary>
        IStatusGeneric CreatePost(DetailPostDto dto);

        /// <summary>Async version of <see cref="CreatePost"/>.</summary>
        Task<IStatusGeneric> CreatePostAsync(DetailPostDtoAsync dto);

        /// <summary>Updates the Post identified by <c>dto.PostId</c> from the DTO. Returns a status carrying any errors.</summary>
        IStatusGeneric UpdatePost(DetailPostDto dto);

        /// <summary>Async version of <see cref="UpdatePost"/>.</summary>
        Task<IStatusGeneric> UpdatePostAsync(DetailPostDtoAsync dto);
    }

    public class PostCrudHelper : IPostCrudHelper
    {
        private readonly SampleWebAppDb _db;

        public PostCrudHelper(SampleWebAppDb db)
        {
            _db = db;
        }

        //--------------------------------------------------
        //Secondary data (dropdown + multi-select) population

        public void SetupSecondaryData(DetailPostDto dto)
        {
            var blogs = _db.Blogs.ToList();
            var tags = _db.Tags.ToList();
            PopulateSecondaryData(dto.Bloggers, dto.UserChosenTags, blogs, tags,
                dto.PostId, dto.BlogId, dto.Tags);
        }

        public async Task SetupSecondaryDataAsync(DetailPostDtoAsync dto)
        {
            var blogs = await _db.Blogs.ToListAsync();
            var tags = await _db.Tags.ToListAsync();
            PopulateSecondaryData(dto.Bloggers, dto.UserChosenTags, blogs, tags,
                dto.PostId, dto.BlogId, dto.Tags);
        }

        //--------------------------------------------------
        //Create

        public IStatusGeneric CreatePost(DetailPostDto dto)
        {
            var status = BuildAndSave(dto.PostId, dto.Title, dto.Content,
                dto.Bloggers, dto.UserChosenTags, isUpdate: false);
            return status;
        }

        public Task<IStatusGeneric> CreatePostAsync(DetailPostDtoAsync dto)
        {
            return BuildAndSaveAsync(dto.PostId, dto.Title, dto.Content,
                dto.Bloggers, dto.UserChosenTags, isUpdate: false);
        }

        //--------------------------------------------------
        //Update

        public IStatusGeneric UpdatePost(DetailPostDto dto)
        {
            return BuildAndSave(dto.PostId, dto.Title, dto.Content,
                dto.Bloggers, dto.UserChosenTags, isUpdate: true);
        }

        public Task<IStatusGeneric> UpdatePostAsync(DetailPostDtoAsync dto)
        {
            return BuildAndSaveAsync(dto.PostId, dto.Title, dto.Content,
                dto.Bloggers, dto.UserChosenTags, isUpdate: true);
        }

        //--------------------------------------------------
        //private helpers

        private static void PopulateSecondaryData(DropDownListType bloggers, MultiSelectListType userChosenTags,
            IReadOnlyCollection<Blog> allBlogs, IReadOnlyCollection<Tag> allTags,
            int postId, int selectedBlogId, ICollection<Tag> selectedTags)
        {
            bloggers.SetupDropDownListContent(
                allBlogs.Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                "--- choose blogger ---");
            if (postId != 0)
                bloggers.SetSelectedValue(selectedBlogId.ToString("D"));

            var preselectedTags = postId == 0 || selectedTags == null
                ? new List<KeyValuePair<string, int>>()
                : selectedTags
                    .Select(x => new KeyValuePair<string, int>(x.Name, x.TagId))
                    .ToList();
            userChosenTags.SetupMultiSelectList(
                allTags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)), preselectedTags);
        }

        private IStatusGeneric BuildAndSave(int postId, string title, string content,
            DropDownListType bloggers, MultiSelectListType userChosenTags, bool isUpdate)
        {
            var status = new StatusGenericHandler();

            var post = ResolvePost(postId, isUpdate, status);
            if (status.HasErrors)
                return status;

            var blogId = ResolveBlogId(bloggers, status);
            var tags = ResolveTags(userChosenTags, status);
            if (status.HasErrors)
                return status;

            ApplyToPost(post, title, content, blogId.Value, tags, isUpdate);

            status.CombineStatuses(ValidateEntity(post));
            if (status.HasErrors)
                return status;

            _db.SaveChanges();
            status.Message = isUpdate ? "Successfully updated the post." : "Successfully created the post.";
            return status;
        }

        private async Task<IStatusGeneric> BuildAndSaveAsync(int postId, string title, string content,
            DropDownListType bloggers, MultiSelectListType userChosenTags, bool isUpdate)
        {
            var status = new StatusGenericHandler();

            var post = isUpdate
                ? await _db.Posts.Include(p => p.Tags).SingleOrDefaultAsync(p => p.PostId == postId)
                : new Post();
            if (isUpdate && post == null)
            {
                status.AddError("Could not find the post you asked for. Did another user delete it?");
                return status;
            }
            if (!isUpdate)
                _db.Posts.Add(post);

            var blogId = ResolveBlogId(bloggers, status);
            var tags = await ResolveTagsAsync(userChosenTags, status);
            if (status.HasErrors)
                return status;

            ApplyToPost(post, title, content, blogId.Value, tags, isUpdate);

            status.CombineStatuses(ValidateEntity(post));
            if (status.HasErrors)
                return status;

            await _db.SaveChangesAsync();
            status.Message = isUpdate ? "Successfully updated the post." : "Successfully created the post.";
            return status;
        }

        private Post ResolvePost(int postId, bool isUpdate, StatusGenericHandler status)
        {
            if (!isUpdate)
            {
                var newPost = new Post();
                _db.Posts.Add(newPost);
                return newPost;
            }

            var post = _db.Posts.Include(p => p.Tags).SingleOrDefault(p => p.PostId == postId);
            if (post == null)
                status.AddError("Could not find the post you asked for. Did another user delete it?");
            return post;
        }

        private int? ResolveBlogId(DropDownListType bloggers, StatusGenericHandler status)
        {
            var blogId = bloggers.SelectedValueAsInt;
            if (blogId == null)
            {
                status.AddError("The blogger was not selected. You must do that before the post can be saved.", nameof(DetailPostDto.Bloggers));
                return null;
            }
            if (_db.Blogs.Find(blogId.Value) == null)
            {
                status.AddError("Could not find the blogger you selected. Did another user delete it?", nameof(DetailPostDto.Bloggers));
                return null;
            }
            return blogId;
        }

        private List<Tag> ResolveTags(MultiSelectListType userChosenTags, StatusGenericHandler status)
        {
            var requiredTagIds = userChosenTags.GetFinalSelectionAsInts();
            if (!requiredTagIds.Any())
            {
                status.AddError("You must select at least one tag for the post.", nameof(DetailPostDto.UserChosenTags));
                return new List<Tag>();
            }
            var tags = _db.Tags.Where(x => requiredTagIds.Contains(x.TagId)).ToList();
            if (tags.Count != requiredTagIds.Length)
                status.AddError("Could not find one of the tags. Did another user delete it?", nameof(DetailPostDto.UserChosenTags));
            return tags;
        }

        private async Task<List<Tag>> ResolveTagsAsync(MultiSelectListType userChosenTags, StatusGenericHandler status)
        {
            var requiredTagIds = userChosenTags.GetFinalSelectionAsInts();
            if (!requiredTagIds.Any())
            {
                status.AddError("You must select at least one tag for the post.", nameof(DetailPostDtoAsync.UserChosenTags));
                return new List<Tag>();
            }
            var tags = await _db.Tags.Where(x => requiredTagIds.Contains(x.TagId)).ToListAsync();
            if (tags.Count != requiredTagIds.Length)
                status.AddError("Could not find one of the tags. Did another user delete it?", nameof(DetailPostDtoAsync.UserChosenTags));
            return tags;
        }

        private static void ApplyToPost(Post post, string title, string content, int blogId, List<Tag> tags, bool isUpdate)
        {
            post.Title = title;
            post.Content = content;
            post.BlogId = blogId;
            if (post.Tags == null)
                post.Tags = new List<Tag>();
            else
                post.Tags.Clear();
            foreach (var tag in tags)
                post.Tags.Add(tag);
        }

        private static IStatusGeneric ValidateEntity(Post post)
        {
            var status = new StatusGenericHandler();
            var results = new List<ValidationResult>();
            var context = new ValidationContext(post);
            if (!Validator.TryValidateObject(post, context, results, validateAllProperties: true))
                status.AddValidationResults(results);
            return status;
        }
    }
}
