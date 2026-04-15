#region licence
// The MIT License (MIT)
// 
// Filename: PostsController.cs
// Date Created: 2014/06/18
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
using System.Threading;
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
    /// In this case we are using normal, non-async commands.
    /// </summary>
    public class PostsController : Controller
    {
        private readonly SampleWebAppDb _db;

        public PostsController(SampleWebAppDb db)
        {
            _db = db;
        }

        public IActionResult Index(int? id)
        {
            var query = _db.Posts
                .Include(p => p.Blogger)
                .Include(p => p.Tags)
                .AsQueryable();

            if (id != null && id != 0)
            {
                query = query.Where(x => x.BlogId == id);
                TempData["message"] = "Filtered list";
            }

            var list = query.Select(p => new SimplePostDto
            {
                PostId = p.PostId,
                BlogId = p.BlogId,
                BloggerName = p.Blogger.Name,
                Title = p.Title,
                LastUpdated = p.LastUpdated,
                TagNamesList = p.Tags.Select(t => t.Name).ToList()
            }).ToList();

            return View(list);
        }

        public IActionResult Details(int id)
        {
            var post = _db.Posts
                .Include(p => p.Blogger)
                .Include(p => p.Tags)
                .FirstOrDefault(p => p.PostId == id);
            if (post == null) return NotFound();

            var dto = MapToDetailDto(post);
            return View(dto);
        }

        public IActionResult Edit(int id)
        {
            var post = _db.Posts
                .Include(p => p.Blogger)
                .Include(p => p.Tags)
                .FirstOrDefault(p => p.PostId == id);
            if (post == null) return NotFound();

            var dto = MapToDetailDto(post);
            SetupDropDownsAndMultiSelect(dto);
            return View(dto);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Edit(DetailPostDto dto)
        {
            if (!ModelState.IsValid)
            {
                SetupDropDownsAndMultiSelect(dto);
                return View(dto);
            }

            var post = _db.Posts
                .Include(p => p.Tags)
                .FirstOrDefault(p => p.PostId == dto.PostId);
            if (post == null) return NotFound();

            post.Title = dto.Title;
            post.Content = dto.Content;
            post.BlogId = GetBlogIdFromDropDown(dto.Bloggers);

            // Update tags
            var selectedTagIds = dto.UserChosenTags.GetFinalSelectionAsInts();
            post.Tags = _db.Tags.Where(t => selectedTagIds.Contains(t.TagId)).ToList();

            _db.SaveChanges();
            TempData["message"] = "Successfully updated Post.";
            return RedirectToAction("Index");
        }

        public IActionResult Create()
        {
            var dto = new DetailPostDto();
            SetupDropDownsAndMultiSelect(dto);
            return View(dto);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Create(DetailPostDto dto)
        {
            if (!ModelState.IsValid)
            {
                SetupDropDownsAndMultiSelect(dto);
                return View(dto);
            }

            var post = new Post
            {
                Title = dto.Title,
                Content = dto.Content,
                BlogId = GetBlogIdFromDropDown(dto.Bloggers)
            };

            var selectedTagIds = dto.UserChosenTags.GetFinalSelectionAsInts();
            post.Tags = _db.Tags.Where(t => selectedTagIds.Contains(t.TagId)).ToList();

            _db.Posts.Add(post);
            _db.SaveChanges();
            TempData["message"] = "Successfully created Post.";
            return RedirectToAction("Index");
        }

        public IActionResult Delete(int id)
        {
            var post = _db.Posts.Find(id);
            if (post != null)
            {
                _db.Posts.Remove(post);
                _db.SaveChanges();
                TempData["message"] = "Successfully deleted Post.";
            }
            else
            {
                TempData["errorMessage"] = "Could not find the Post to delete.";
            }
            return RedirectToAction("Index");
        }

        public IActionResult NumPosts()
        {
            var numPosts = _db.Posts.Count();
            return View((object)string.Format("The total number of Posts is {0}", numPosts));
        }

        public IActionResult CodeView()
        {
            return View();
        }

        public IActionResult Delay()
        {
            Thread.Sleep(500);
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

        private DetailPostDto MapToDetailDto(Post post)
        {
            return new DetailPostDto
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

        private void SetupDropDownsAndMultiSelect(DetailPostDto dto)
        {
            dto.Bloggers.SetupDropDownListContent(
                _db.Blogs.ToList().Select(x => new KeyValuePair<string, string>(x.Name, x.BlogId.ToString("D"))),
                "--- choose blogger ---");
            if (dto.PostId != 0)
                dto.Bloggers.SetSelectedValue(dto.BlogId.ToString("D"));

            var preselectedTags = dto.Tags != null && dto.Tags.Any()
                ? dto.Tags.Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)).ToList()
                : new List<KeyValuePair<string, int>>();
            dto.UserChosenTags.SetupMultiSelectList(
                _db.Tags.ToList().Select(x => new KeyValuePair<string, int>(x.Name, x.TagId)),
                preselectedTags);
        }

        private int GetBlogIdFromDropDown(DropDownListType bloggers)
        {
            var blogId = bloggers.SelectedValueAsInt;
            return blogId ?? 0;
        }
    }
}
