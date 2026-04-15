#region licence
// The MIT License (MIT)
// 
// Filename: BlogsController.cs
// Date Created: 2014/07/11
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
using Microsoft.AspNetCore.Mvc;
using ServiceLayer.BlogServices;

namespace SampleWebApp.Controllers
{
    /// <summary>
    /// This is an example of a Controller using EF Core database commands directly to the data class.
    /// In this case we are using normal, non-async commands.
    /// </summary>
    public class BlogsController : Controller
    {
        private readonly SampleWebAppDb _db;

        public BlogsController(SampleWebAppDb db)
        {
            _db = db;
        }

        public IActionResult Index()
        {
            var list = _db.Blogs.Select(b => new BlogListDto
            {
                BlogId = b.BlogId,
                Name = b.Name,
                EmailAddress = b.EmailAddress,
                PostsCount = b.Posts.Count
            }).ToList();
            return View(list);
        }

        public IActionResult Edit(int id)
        {
            var blog = _db.Blogs.Find(id);
            if (blog == null) return NotFound();
            return View(blog);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Edit(Blog blog)
        {
            if (!ModelState.IsValid)
                return View(blog);

            var existingBlog = _db.Blogs.Find(blog.BlogId);
            if (existingBlog == null) return NotFound();

            existingBlog.Name = blog.Name;
            existingBlog.EmailAddress = blog.EmailAddress;
            _db.SaveChanges();
            TempData["message"] = "Successfully updated Blog.";
            return RedirectToAction("Index");
        }

        public IActionResult Create()
        {
            return View(new Blog());
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Create(Blog blog)
        {
            if (!ModelState.IsValid)
                return View(blog);

            _db.Blogs.Add(blog);
            _db.SaveChanges();
            TempData["message"] = "Successfully created Blog.";
            return RedirectToAction("Index");
        }

        public IActionResult Delete(int id)
        {
            var blog = _db.Blogs.Find(id);
            if (blog != null)
            {
                _db.Blogs.Remove(blog);
                _db.SaveChanges();
                TempData["message"] = "Successfully deleted Blog.";
            }
            return RedirectToAction("Index");
        }
    }
}
