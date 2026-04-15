#region licence
// The MIT License (MIT)
// 
// Filename: PostsAsyncController.cs
// Date Created: 2014/06/17
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
using DataLayer.Startup;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using ServiceLayer.PostServices;
using ServiceLayer.UiClasses;

namespace SampleWebApp.Controllers
{
    /// <summary>
    /// This is an example of a Controller using EF Core database commands with a DTO.
    /// In this case we are using async commands.
    /// </summary>
    public class PostsAsyncController : Controller
    {
        private readonly SampleWebAppDb _db;

        public PostsAsyncController(SampleWebAppDb db)
        {
            _db = db;
        }

        public async Task<IActionResult> Index()
        {
            var list = await _db.Posts
                .Include(p => p.Blogger)
                .Include(p => p.Tags)
                .Select(p => new SimplePostDtoAsync
                {
                    PostId = p.PostId,
                    BloggerName = p.Blogger.Name,
                    Title = p.Title,
                    LastUpdated = p.LastUpdated,
                    TagNamesList = p.Tags.Select(t => t.Name).ToList()
                }).ToListAsync();

            return View(list);
        }

        public async Task<IActionResult> Details(int id)
        {
            var post = await _db.Posts
                .Include(p => p.Blogger)
                .Include(p => p.Tags)
                .FirstOrDefaultAsync(p => p.PostId == id);
            if (post == null) return NotFound();

            var dto = MapToDetailDto(post);
            return View(dto);
        }

        public async Task<IActionResult> Edit(int id)
        {
            var post = await _db.Posts
                .Include(p => p.Blogger)
                .Include(p => p.Tags)
                .FirstOrDefaultAsync(p => p.PostId == id);
            if (post == null) return NotFound();

            var dto = MapToDetailDto(post);
            await SetupDropDownsAndMultiSelectAsync(dto);
            return View(dto);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Edit(DetailPostDtoAsync dto)
        {
            if (!ModelState.IsValid)
            {
                await SetupDropDownsAndMultiSelectAsync(dto);
                return View(dto);
            }

            var post = await _db.Posts
                .Include(p => p.Tags)
                .FirstOrDefaultAsync(p => p.PostId == dto.PostId);
            if (post == null) return NotFound();

            post.Title = dto.Title;
            post.Content = dto.Content;
            post.BlogId = GetBlogIdFromDropDown(dto.Bloggers);

            var selectedTagIds = dto.UserChosenTags.GetFinalSelectionAsInts();
            post.Tags = await _db.Tags.Where(t => selectedTagIds.Contains(t.TagId)).ToListAsync();

            await _db.SaveChangesAsync();
            TempData["message"] = "Successfully updated Post.";
            return RedirectToAction("Index");
        }

        public async Task<IActionResult> Create()
        {
            var dto = new DetailPostDtoAsync();
            await SetupDropDownsAndMultiSelectAsync(dto);
            return View(dto);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Create(DetailPostDtoAsync dto)
        {
            if (!ModelState.IsValid)
            {
                await SetupDropDownsAndMultiSelectAsync(dto);
                return View(dto);
            }

            var post = new Post
            {
                Title = dto.Title,
                Content = dto.Content,
                BlogId = GetBlogIdFromDropDown(dto.Bloggers)
            };

            var selectedTagIds = dto.UserChosenTags.GetFinalSelectionAsInts();
            post.Tags = await _db.Tags.Where(t => selectedTagIds.Contains(t.TagId)).ToListAsync();

            _db.Posts.Add(post);
            await _db.SaveChangesAsync();
            TempData["message"] = "Successfully created Post.";
            return RedirectToAction("Index");
        }

        public async Task<IActionResult> Delete(int id)
        {
            var post = await _db.Posts.FindAsync(id);
            if (post != null)
            {
                _db.Posts.Remove(post);
                await _db.SaveChangesAsync();
                TempData["message"] = "Successfully deleted Post.";
            }
            else
            {
                TempData["errorMessage"] = "Could not find the Post to delete.";
            }
            return RedirectToAction("Index");
        }

        public async Task<IActionResult> NumPosts()
        {
            var numPosts = await _db.Posts.CountAsync();
            return View((object)string.Format("The total number of Posts is {0}", numPosts));
        }

        public IActionResult CodeView()
        {
            return View();
        }

        public async Task<IActionResult> Delay()
        {
            await Task.Delay(500);
            return View(500);
        }

        public IActionResult Reset()
        {
            DataLayerInitialise.ResetBlogs(_db, TestDataSelection.Medium);
            TempData["message"] = "Successfully reset the blogs data";
            return RedirectToAction("Index");
        }

        //---------------------------------------------------
        //private helpers

        private DetailPostDtoAsync MapToDetailDto(Post post)
        {
            return new DetailPostDtoAsync
            {
                PostId = post.PostId,
                Title = post.Title,
                Content = post.Content,
                BloggerName = post.Blogger?.Name,
                BlogId = post.BlogId,
                LastUpdated = post.LastUpdated,
                Tags = post.Tags?.ToList() ?? new List<Tag>()
            };
        }

        private async Task SetupDropDownsAndMultiSelectAsync(DetailPostDtoAsync dto)
        {
            var bloggers = await _db.Blogs.ToListAsync();
            dto.Bloggers.SetupDropDownListContent(
                bloggers.Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                "--- choose blogger ---");
            if (dto.PostId != 0)
                dto.Bloggers.SetSelectedValue(dto.BlogId.ToString("D"));

            var preselectedTags = dto.Tags != null && dto.Tags.Any()
                ? dto.Tags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)).ToList()
                : new List<KeyValuePair<string, int>>();
            var allTags = await _db.Tags.ToListAsync();
            dto.UserChosenTags.SetupMultiSelectList(
                allTags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)),
                preselectedTags);
        }

        private int GetBlogIdFromDropDown(DropDownListType bloggers)
        {
            var blogId = bloggers.SelectedValueAsInt;
            return blogId ?? 0;
        }
    }
}
