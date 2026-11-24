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
using System.Linq;
using System.Threading.Tasks;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using Microsoft.EntityFrameworkCore;
using DataLayer.Startup;
using GenericServices;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Logging;
using SampleWebApp.Infrastructure;
using ServiceLayer.PostServices;

namespace SampleWebApp.Controllers
{
    public class PostsAsyncController : Controller
    {
        private readonly ICrudServicesAsync _service;
        private readonly SampleWebAppDb _db;
        private readonly ILogger<PostsAsyncController> _logger;

        public PostsAsyncController(ICrudServicesAsync service, SampleWebAppDb db, ILogger<PostsAsyncController> logger)
        {
            _service = service;
            _db = db;
            _logger = logger;
        }

        /// <summary>
        /// This is an example of a Controller using GenericServices database commands directly.
        /// in this case we are using a DTO in the list
        /// </summary>
        /// <param name="id">if not null or zero then it filters the list by BlogId</param>
        public async Task<IActionResult> Index(int? id)
        {
            var filtered = id != null && id != 0;
            var query = filtered
                ? _service.ReadManyNoTracked<SimplePostDtoAsync>().Where(x => x.BlogId == id)
                : _service.ReadManyNoTracked<SimplePostDtoAsync>();
            if (filtered)
                TempData["message"] = "Filtered list";

            return View(await query.ToListAsync());
        }

        public async Task<IActionResult> Details(int id)
        {
            var dto = await _service.ReadSingleAsync<DetailPostDtoAsync>(id);
            return dto == null ? NotFound() : View(dto);
        }

        public async Task<IActionResult> Edit(int id)
        {
            var dto = await _service.ReadSingleAsync<DetailPostDtoAsync>(id);
            return dto == null ? NotFound() : View(await dto.SetupSecondaryDataAsync(_db));
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Edit(DetailPostDtoAsync dto)
        {
            if (!ModelState.IsValid)
                //model errors so return immediately
                return View(await dto.SetupSecondaryDataAsync(_db));

            var status = await dto.SetupRestOfDtoAsync(_db);
            if (status.IsValid)
            {
                await _service.UpdateAndSaveAsync(dto);
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
            return View(await dto.SetupSecondaryDataAsync(_db));
        }

        public async Task<IActionResult> Create()
        {
            return View(await new DetailPostDtoAsync().SetupSecondaryDataAsync(_db));
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Create(DetailPostDtoAsync dto)
        {
            if (!ModelState.IsValid)
                //model errors so return immediately
                return View(await dto.SetupSecondaryDataAsync(_db));

            var status = await dto.SetupRestOfDtoAsync(_db);
            if (status.IsValid)
            {
                await _service.CreateAndSaveAsync(dto);
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
            return View(await dto.SetupSecondaryDataAsync(_db));
        }

        public async Task<IActionResult> Delete(int id)
        {
            await _service.DeleteAndSaveAsync<Post>(id);
            if (_service.IsValid)
                TempData["message"] = _service.Message;
            else
                TempData["errorMessage"] = _service.ErrorsAsHtml();
            return RedirectToAction("Index");
        }

        //-----------------------------------------------------
        //Code used in https://www.simple-talk.com/dotnet/asp.net/the-.net-4.5-asyncawait-commands-in-promise-and-practice/

        public async Task<IActionResult> NumPosts()
        {
            return View((object)await GetNumPostsAsync(_db));
        }

        private static async Task<string> GetNumPostsAsync(SampleWebAppDb db)
        {
            var numPosts = await db.Posts.CountAsync();
            return string.Format("The total number of Posts is {0}", numPosts);
        }

        //--------------------------------------------

        public IActionResult CodeView()
        {
            return View();
        }

        public async Task<IActionResult> Delay()
        {
            await Task.Delay(500);
            return View(500);
        }

        //--------------------------------------------

        public async Task<IActionResult> Reset()
        {
            await Task.Run(() => DataLayerInitialise.ResetBlogs(_db, TestDataSelection.Medium, _logger));
            TempData["message"] = "Successfully reset the blogs data";
            return RedirectToAction("Index");
        }
    }
}
