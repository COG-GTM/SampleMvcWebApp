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
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.AspNetCore.Mvc;
using SampleWebApp.Infrastructure;
using ServiceLayer.BlogServices;

namespace SampleWebApp.Controllers
{
    public class BlogsController : Controller
    {
        private readonly ICrudServices _service;

        public BlogsController(ICrudServices service)
        {
            _service = service;
        }

        public IActionResult Index()
        {
            return View(_service.ReadManyNoTracked<BlogListDto>().ToList());
        }


        public IActionResult Edit(int id)
        {
            var blog = _service.ReadSingle<Blog>(id);
            return blog == null ? NotFound() : View(blog);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Edit(Blog blog)
        {
            if (!ModelState.IsValid)
                //model errors so return immediately
                return View(blog);

            _service.UpdateAndSave(blog);
            if (_service.IsValid)
            {
                TempData["message"] = _service.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            _service.CopyErrorsToModelState(ModelState, blog);
            _service.Context.ChangeTracker.Clear();
            return View(blog);
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
                //model errors so return immediately
                return View(blog);

            _service.CreateAndSave(blog);
            if (_service.IsValid)
            {
                TempData["message"] = _service.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            _service.CopyErrorsToModelState(ModelState, blog);
            _service.Context.ChangeTracker.Clear();
            return View(blog);
        }

        public IActionResult Delete(int id)
        {
            _service.DeleteAndSave<Blog>(id);
            if (_service.IsValid)
                TempData["message"] = _service.Message;
            else
                TempData["errorMessage"] = _service.ErrorsAsHtml();
            return RedirectToAction("Index");
        }

    }
}
