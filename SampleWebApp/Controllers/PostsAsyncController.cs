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
using System.Threading.Tasks;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using GenericServices;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using SampleWebApp.Infrastructure;
using ServiceLayer.PostServices;

namespace SampleWebApp.Controllers
{
    public class PostsAsyncController : Controller
    {
        /// <summary>
        /// This is an example of a Controller using EfCore.GenericServices database commands with a DTO.
        /// In this case we are using async commands
        /// </summary>
        public async Task<IActionResult> Index([FromServices] ICrudServicesAsync service)
        {
            //ReadManyNoTracked returns an IQueryable, so it stays sync and is enumerated asynchronously
            return View(await service.ReadManyNoTracked<SimplePostDtoAsync>().ToListAsync());
        }

        public async Task<IActionResult> Details(int id, [FromServices] ICrudServicesAsync service)
        {
            var dto = await service.ReadSingleAsync<DetailPostDtoAsync>(id);
            if (dto == null)
                return NotFound();

            return View(dto);
        }


        public async Task<IActionResult> Edit(int id, [FromServices] IPostDtoServiceAsync postDtoService)
        {
            //this goes through IPostDtoServiceAsync so that the bloggers dropdown and the tags multi-select are filled in
            var dto = await postDtoService.GetDtoForUpdateAsync(id);
            if (dto == null)
                return NotFound();

            return View(dto);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Edit(DetailPostDtoAsync dto, [FromServices] IPostDtoServiceAsync postDtoService)
        {
            if (!ModelState.IsValid)
            {
                //model errors so return immediately, but the dropdown/multi-select content must be refilled first
                await postDtoService.ResetSecondaryDataAsync(dto);
                return View(dto);
            }

            var status = await postDtoService.UpdateAsync(dto);
            if (status.IsValid)
            {
                TempData["message"] = status.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            status.CopyErrorsToModelState(ModelState, dto);
            await postDtoService.ResetSecondaryDataAsync(dto);
            return View(dto);
        }

        public async Task<IActionResult> Create([FromServices] IPostDtoServiceAsync postDtoService)
        {
            var dto = await postDtoService.GetDtoForCreateAsync();
            return View(dto);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public async Task<IActionResult> Create(DetailPostDtoAsync dto, [FromServices] IPostDtoServiceAsync postDtoService)
        {
            if (!ModelState.IsValid)
            {
                //model errors so return immediately, but the dropdown/multi-select content must be refilled first
                await postDtoService.ResetSecondaryDataAsync(dto);
                return View(dto);
            }

            var status = await postDtoService.CreateAsync(dto);
            if (status.IsValid)
            {
                TempData["message"] = status.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            status.CopyErrorsToModelState(ModelState, dto);
            await postDtoService.ResetSecondaryDataAsync(dto);
            return View(dto);
        }

        public async Task<IActionResult> Delete(int id, [FromServices] ICrudServicesAsync service)
        {

            await service.DeleteAndSaveAsync<Post>(id);
            if (service.IsValid)
                TempData["message"] = service.Message;
            else
                //else errors, so send back an error message
                TempData["errorMessage"] = service.ErrorsAsHtml();
           
            return RedirectToAction("Index");
        }

        //-----------------------------------------------------
        //Code used in https://www.simple-talk.com/dotnet/.net-framework/the-.net-4.5-asyncawait-commands-in-promise-and-practice/

        public async Task<IActionResult> NumPosts([FromServices] SampleWebAppDb db)
        {
            return View((object)await GetNumPostsAsync(db));
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

        public IActionResult Reset([FromServices] SampleWebAppDb db)
        {
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Medium);
            TempData["message"] = "Successfully reset the blogs data";
            return RedirectToAction("Index");
        }
    }
}
