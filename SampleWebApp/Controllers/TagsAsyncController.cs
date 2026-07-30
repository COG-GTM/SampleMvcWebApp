#region licence
// The MIT License (MIT)
// 
// Filename: TagsAsyncController.cs
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
using System.Threading.Tasks;
using DataLayer.DataClasses.Concrete;
using GenericServices;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SampleWebApp.Infrastructure;
using ServiceLayer.TagServices;

namespace SampleWebApp.Controllers
{
    /// <summary>
    /// This is an example of a Controller using EfCore.GenericServices database commands directly to the data class (other that List, which needs a DTO)
    /// In this case we are using async commands
    /// </summary>
    public class TagsAsyncController : Controller
    {
        // GET: TagsAsync
        public async Task<IActionResult> Index([FromServices] ICrudServicesAsync service)
        {
            //ReadManyNoTracked returns an IQueryable, so it stays sync and is enumerated asynchronously
            return View(await service.ReadManyNoTracked<TagListDto>().ToListAsync());
        }

        public async Task<IActionResult> Details(int id, [FromServices] ICrudServicesAsync service)
        {
            var tag = await service.ReadSingleAsync<Tag>(id);
            if (tag == null)
                return NotFound();

            return View(tag);
        }


        public async Task<IActionResult> Edit(int id, [FromServices] ICrudServicesAsync service)
        {
            var tag = await service.ReadSingleAsync<Tag>(id);
            if (tag == null)
                return NotFound();

            return View(tag);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Edit(Tag tag, [FromServices] ICrudServicesAsync service)
        {
            if (!ModelState.IsValid)
                //model errors so return immediately
                return View(tag);

            await service.UpdateAndSaveAsync(tag);
            if (service.IsValid)
            {
                TempData["message"] = service.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            service.CopyErrorsToModelState(ModelState, tag);
            return View(tag);
        }

        public IActionResult Create()
        {
            return View(new Tag());
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Create(Tag tag, [FromServices] ICrudServicesAsync service)
        {
            if (!ModelState.IsValid)
                //model errors so return immediately
                return View(tag);

            await service.CreateAndSaveAsync(tag);
            if (service.IsValid)
            {
                TempData["message"] = service.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            service.CopyErrorsToModelState(ModelState, tag);
            return View(tag);
        }

        public async Task<IActionResult> Delete(int id, [FromServices] ICrudServicesAsync service)
        {

            await service.DeleteAndSaveAsync<Tag>(id);
            if (service.IsValid)
                TempData["message"] = service.Message;
            else
                //else errors, so send back an error message
                TempData["errorMessage"] = service.ErrorsAsHtml();

            return RedirectToAction("Index");
        }

        //--------------------------------------------

        public IActionResult CodeView()
        {
            return View();
        }

    }
}
