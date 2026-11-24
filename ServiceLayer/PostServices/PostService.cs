#region licence
// The MIT License (MIT)
// 
// Filename: PostService.cs
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
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    /// <summary>
    /// Handles the parts of the post CRUD that need the blogger dropdown and the tags multi-select list
    /// </summary>
    public class PostService : IPostService
    {
        private readonly SampleWebAppDb _db;
        private readonly ICrudServices _crud;

        public PostService(SampleWebAppDb db, ICrudServices crud)
        {
            _db = db;
            _crud = crud;
        }

        public DetailPostDto GetDetail(int postId)
        {
            return _db.Posts.AsNoTracking()
                .Where(x => x.PostId == postId)
                .Select(x => new DetailPostDto
                {
                    PostId = x.PostId,
                    Title = x.Title,
                    Content = x.Content,
                    BloggerName = x.Blogger.Name,
                    LastUpdated = x.LastUpdated,
                    BlogId = x.BlogId,
                    Tags = x.Tags.ToList()
                })
                .SingleOrDefault();
        }

        public DetailPostDto GetForEdit(int postId)
        {
            var dto = GetDetail(postId);
            return dto == null ? null : ResetDto(dto);
        }

        public DetailPostDto GetNewForCreate()
        {
            return ResetDto(new DetailPostDto());
        }

        public DetailPostDto ResetDto(DetailPostDto dto)
        {
            dto.Bloggers ??= new();
            dto.UserChosenTags ??= new();
            PostServiceHelpers.SetupLists(dto.Bloggers, dto.UserChosenTags, dto.PostId, dto.BlogId, dto.Tags,
                _db.Blogs.AsNoTracking().OrderBy(x => x.BlogId).ToList(),
                _db.Tags.AsNoTracking().OrderBy(x => x.TagId).ToList());
            return dto;
        }

        public IStatusGeneric Create(DetailPostDto dto)
        {
            var status = new StatusGenericHandler();
            var tags = SetupRestOfDto(dto, status);
            if (!status.IsValid)
                return status;

            var post = new Post
            {
                Title = dto.Title,
                Content = dto.Content,
                BlogId = dto.BlogId,
                Tags = tags
            };
            return SaveAndCheck(status, () => _crud.CreateAndSave(post));
        }

        public IStatusGeneric Update(DetailPostDto dto)
        {
            var status = new StatusGenericHandler();
            var post = _db.Posts.Include(x => x.Tags).SingleOrDefault(x => x.PostId == dto.PostId);
            if (post == null)
                return status.AddError("Could not find the post you were editing. Did another user delete it?");

            var tags = SetupRestOfDto(dto, status);
            if (!status.IsValid)
            {
                _db.ChangeTracker.Clear();
                return status;
            }

            post.Title = dto.Title;
            post.Content = dto.Content;
            post.BlogId = dto.BlogId;
            post.Tags.Clear();
            foreach (var tag in tags)
                post.Tags.Add(tag);
            return SaveAndCheck(status, () => _crud.UpdateAndSave(post));
        }

        //---------------------------------------------------
        //private helpers

        private IStatusGeneric SaveAndCheck(StatusGenericHandler status, System.Action save)
        {
            save();
            status.CombineStatuses(_crud);
            if (!status.IsValid)
                _db.ChangeTracker.Clear();
            return status;
        }

        private List<Tag> SetupRestOfDto(DetailPostDto dto, StatusGenericHandler status)
        {
            var blogId = dto.Bloggers?.SelectedValueAsInt;
            if (blogId == null)
                status.AddError(PostServiceHelpers.BloggerNotSelected, "Bloggers");
            else if (!_db.Blogs.Any(x => x.BlogId == blogId))
                status.AddError(PostServiceHelpers.BloggerNotFound, "Bloggers");
            else
                dto.BlogId = (int)blogId;

            var requiredTagIds = dto.UserChosenTags?.GetFinalSelectionAsInts() ?? [];
            if (!requiredTagIds.Any())
            {
                status.AddError(PostServiceHelpers.NoTagsSelected, "UserChosenTags");
                return null;
            }

            var tags = _db.Tags.Where(x => requiredTagIds.Contains(x.TagId)).ToList();
            if (tags.Count != requiredTagIds.Distinct().Count())
            {
                status.AddError(PostServiceHelpers.TagNotFound, "UserChosenTags");
                return null;
            }

            dto.Tags = tags;
            return tags;
        }
    }
}
