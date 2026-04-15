#region licence
// The MIT License (MIT)
// 
// Filename: TagsController.cs
// Date Created: 2014/06/30
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
using ServiceLayer.TagServices;

namespace SampleWebApp.Controllers
{
    /// <summary>
    /// This is an example of a Controller using EF Core database commands directly to the data class.
    /// In this case we are using normal, non-async commands.
    /// </summary>
    public class TagsController : Controller
    {
        private readonly SampleWebAppDb _db;

        public TagsController(SampleWebAppDb db)
        {
            _db = db;
        }

        public IActionResult Index()
        {
            var list = _db.Tags.Select(t => new TagListDto
            {
                TagId = t.TagId,
                Name = t.Name,
                Slug = t.Slug,
                PostsCount = t.Posts.Count
            }).ToList();
            return View(list);
        }

        public IActionResult Details(int id)
        {
            var tag = _db.Tags.Find(id);
            if (tag == null) return NotFound();
            return View(tag);
        }

        public IActionResult Edit(int id)
        {
            var tag = _db.Tags.Find(id);
            if (tag == null) return NotFound();
            return View(tag);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Edit(Tag tag)
        {
            if (!ModelState.IsValid)
                return View(tag);

            var existingTag = _db.Tags.Find(tag.TagId);
            if (existingTag == null) return NotFound();

            existingTag.Name = tag.Name;
            existingTag.Slug = tag.Slug;
            _db.SaveChanges();
            TempData["message"] = "Successfully updated Tag.";
            return RedirectToAction("Index");
        }

        public IActionResult Create()
        {
            return View(new Tag());
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Create(Tag tag)
        {
            if (!ModelState.IsValid)
                return View(tag);

            _db.Tags.Add(tag);
            _db.SaveChanges();
            TempData["message"] = "Successfully created Tag.";
            return RedirectToAction("Index");
        }

        public IActionResult Delete(int id)
        {
            var tag = _db.Tags.Find(id);
            if (tag != null)
            {
                _db.Tags.Remove(tag);
                _db.SaveChanges();
                TempData["message"] = "Successfully deleted Tag.";
            }
            else
            {
                TempData["errorMessage"] = "Could not find the Tag to delete.";
            }
            return RedirectToAction("Index");
        }

        public IActionResult CodeView()
        {
            return View();
        }
    }
}
