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
using System.Linq;
using System.Threading;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using GenericServices;
using Microsoft.AspNetCore.Mvc;
using SampleWebApp.Infrastructure;
using ServiceLayer.PostServices;

namespace SampleWebApp.Controllers
{
    public class PostsController : Controller
    {
        private readonly ICrudServices _service;
        private readonly SampleWebAppDb _db;

        public PostsController(ICrudServices service, SampleWebAppDb db)
        {
            _service = service;
            _db = db;
        }

        /// <summary>
        /// This is an example of a Controller using GenericServices database commands directly.
        /// in this case we are using a DTO in the list
        /// </summary>
        /// <param name="id">if not null or zero then it filters the list by BlogId</param>
        public IActionResult Index(int? id)
        {
            var filtered = id != null && id != 0;
            var query = filtered
                ? _service.ReadManyNoTracked<SimplePostDto>().Where(x => x.BlogId == id)
                : _service.ReadManyNoTracked<SimplePostDto>();
            if (filtered)
                TempData["message"] = "Filtered list";

            return View(query.ToList());
        }

        public IActionResult Details(int id)
        {
            var dto = _service.ReadSingle<DetailPostDto>(id);
            return dto == null ? NotFound() : View(dto);
        }

        public IActionResult Edit(int id)
        {
            var dto = _service.ReadSingle<DetailPostDto>(id);
            return dto == null ? NotFound() : View(dto.SetupSecondaryData(_db));
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Edit(DetailPostDto dto)
        {
            if (!ModelState.IsValid)
                //model errors so return immediately
                return View(dto.SetupSecondaryData(_db));

            var status = dto.SetupRestOfDto(_db);
            if (status.IsValid)
            {
                _service.UpdateAndSave(dto);
                if (_service.IsValid)
                {
                    TempData["message"] = _service.Message;
                    return RedirectToAction("Index");
                }
                status = _service;
            }

            //else errors, so copy the errors over to the ModelState and return to view
            status.CopyErrorsToModelState(ModelState, dto);
            _db.ChangeTracker.Clear();
            return View(dto.SetupSecondaryData(_db));
        }

        public IActionResult Create()
        {
            return View(new DetailPostDto().SetupSecondaryData(_db));
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Create(DetailPostDto dto)
        {
            if (!ModelState.IsValid)
                //model errors so return immediately
                return View(dto.SetupSecondaryData(_db));

            var status = dto.SetupRestOfDto(_db);
            if (status.IsValid)
            {
                _service.CreateAndSave(dto);
                if (_service.IsValid)
                {
                    TempData["message"] = _service.Message;
                    return RedirectToAction("Index");
                }
                status = _service;
            }

            //else errors, so copy the errors over to the ModelState and return to view
            status.CopyErrorsToModelState(ModelState, dto);
            _db.ChangeTracker.Clear();
            return View(dto.SetupSecondaryData(_db));
        }

        public IActionResult Delete(int id)
        {
            _service.DeleteAndSave<Post>(id);
            if (_service.IsValid)
                TempData["message"] = _service.Message;
            else
                TempData["errorMessage"] = _service.ErrorsAsHtml();
            return RedirectToAction("Index");
        }

        //-----------------------------------------------------
        //Code used in https://www.simple-talk.com/dotnet/asp.net/the-.net-4.5-asyncawait-commands-in-promise-and-practice/

        public IActionResult NumPosts()
        {
            return View((object)GetNumPosts(_db));
        }

        public static string GetNumPosts(SampleWebAppDb db)
        {
            var numPosts = db.Posts.Count();
            return string.Format("The total number of Posts is {0}", numPosts);
        }

        //--------------------------------------------

        public IActionResult CodeView()
        {
            return View();
        }

        public IActionResult Delay()
        {
            Thread.Sleep(500);
            return View(500);
        }

        //--------------------------------------------

        public IActionResult Reset()
        {
            DataLayerInitialise.ResetBlogs(_db, TestDataSelection.Medium);
            TempData["message"] = "Successfully reset the blogs data";
            return RedirectToAction("Index");
        }
    }
}
