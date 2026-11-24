#region licence
// The MIT License (MIT)
// 
// Filename: PostServiceAsync.cs
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
using System.Threading.Tasks;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using GenericServices.Configuration;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace ServiceLayer.PostServices
{
    /// <summary>
    /// Async version of <see cref="PostService"/>
    /// </summary>
    public class PostServiceAsync : IPostServiceAsync
    {
        private readonly SampleWebAppDb _db;
        private readonly IGenericServicesConfig _config;

        public PostServiceAsync(SampleWebAppDb db, IGenericServicesConfig config)
        {
            _db = db;
            _config = config;
        }

        public async Task<DetailPostDtoAsync> GetDetailAsync(int postId)
        {
            return await _db.Posts.AsNoTracking()
                .Where(x => x.PostId == postId)
                .Select(x => new DetailPostDtoAsync
                {
                    PostId = x.PostId,
                    Title = x.Title,
                    Content = x.Content,
                    BloggerName = x.Blogger.Name,
                    LastUpdated = x.LastUpdated,
                    BlogId = x.BlogId,
                    Tags = x.Tags.ToList()
                })
                .SingleOrDefaultAsync();
        }

        public async Task<DetailPostDtoAsync> GetForEditAsync(int postId)
        {
            var dto = await GetDetailAsync(postId);
            return dto == null ? null : await ResetDtoAsync(dto);
        }

        public Task<DetailPostDtoAsync> GetNewForCreateAsync()
        {
            return ResetDtoAsync(new DetailPostDtoAsync());
        }

        public async Task<DetailPostDtoAsync> ResetDtoAsync(DetailPostDtoAsync dto)
        {
            dto.Bloggers ??= new();
            dto.UserChosenTags ??= new();
            PostServiceHelpers.SetupLists(dto.Bloggers, dto.UserChosenTags, dto.PostId, dto.BlogId, dto.Tags,
                await _db.Blogs.AsNoTracking().OrderBy(x => x.BlogId).ToListAsync(),
                await _db.Tags.AsNoTracking().OrderBy(x => x.TagId).ToListAsync());
            return dto;
        }

        public async Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto)
        {
            var status = new StatusGenericHandler();
            var tags = await SetupRestOfDtoAsync(dto, status);
            if (!status.IsValid)
                return status;

            var post = new Post
            {
                Title = dto.Title,
                Content = dto.Content,
                BlogId = dto.BlogId,
                Tags = tags
            };
            _db.Posts.Add(post);
            status.Message = "Successfully created the Post";
            return CheckSave(status, await _db.SaveChangesWithValidationAsync(_config));
        }

        public async Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto)
        {
            var status = new StatusGenericHandler();
            var post = await _db.Posts.Include(x => x.Tags).SingleOrDefaultAsync(x => x.PostId == dto.PostId);
            if (post == null)
                return status.AddError("Could not find the post you were editing. Did another user delete it?");

            var tags = await SetupRestOfDtoAsync(dto, status);
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
            _db.Entry(post).Property(x => x.LastUpdated).IsModified = true;
            status.Message = "Successfully updated the Post";
            return CheckSave(status, await _db.SaveChangesWithValidationAsync(_config));
        }

        //---------------------------------------------------
        //private helpers

        private IStatusGeneric CheckSave(StatusGenericHandler status, IStatusGeneric saveStatus)
        {
            status.CombineStatuses(saveStatus);
            if (!status.IsValid)
                _db.ChangeTracker.Clear();
            return status;
        }

        private async Task<List<Tag>> SetupRestOfDtoAsync(DetailPostDtoAsync dto, StatusGenericHandler status)
        {
            var blogId = dto.Bloggers?.SelectedValueAsInt;
            if (blogId == null)
                status.AddError(PostServiceHelpers.BloggerNotSelected, "Bloggers");
            else if (!await _db.Blogs.AnyAsync(x => x.BlogId == blogId))
                status.AddError(PostServiceHelpers.BloggerNotFound, "Bloggers");
            else
                dto.BlogId = (int)blogId;

            var requiredTagIds = dto.UserChosenTags?.GetFinalSelectionAsInts() ?? [];
            if (!requiredTagIds.Any())
            {
                status.AddError(PostServiceHelpers.NoTagsSelected, "UserChosenTags");
                return null;
            }

            var tags = await _db.Tags.Where(x => requiredTagIds.Contains(x.TagId)).ToListAsync();
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
