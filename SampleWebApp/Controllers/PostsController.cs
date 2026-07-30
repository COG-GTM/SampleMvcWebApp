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
    /// <summary>
    /// This is an example of a Controller using EfCore.GenericServices database commands with a DTO.
    /// In this case we are using normal, non-async commands
    /// </summary>
    public class PostsController : Controller
    {
        /// <summary>
        /// Note that is Index is different in that it has an optional id to filter the list on.
        /// </summary>
        /// <param name="id"></param>
        /// <param name="service"></param>
        /// <returns></returns>
        public IActionResult Index(int? id, [FromServices] ICrudServices service)
        {
            var filtered = id != null && id != 0;
            var query = filtered
                ? service.ReadManyNoTracked<SimplePostDto>().Where(x => x.BlogId == id)
                : service.ReadManyNoTracked<SimplePostDto>();
            if (filtered)
                TempData["message"] = "Filtered list";

            return View(query.ToList());
        }

        public IActionResult Details(int id, [FromServices] ICrudServices service)
        {
            var dto = service.ReadSingle<DetailPostDto>(id);
            if (dto == null)
                return NotFound();

            return View(dto);
        }

        public IActionResult Edit(int id, [FromServices] IPostDtoService postDtoService)
        {
            //this goes through IPostDtoService so that the bloggers dropdown and the tags multi-select are filled in
            var dto = postDtoService.GetDtoForUpdate(id);
            if (dto == null)
                return NotFound();

            return View(dto);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Edit(DetailPostDto dto, [FromServices] IPostDtoService postDtoService)
        {
            if (!ModelState.IsValid)
            {
                //model errors so return immediately, but the dropdown/multi-select content must be refilled first
                postDtoService.ResetSecondaryData(dto);
                return View(dto);
            }

            var status = postDtoService.Update(dto);
            if (status.IsValid)
            {
                TempData["message"] = status.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            status.CopyErrorsToModelState(ModelState, dto);
            postDtoService.ResetSecondaryData(dto);
            return View(dto);
        }

        public IActionResult Create([FromServices] IPostDtoService postDtoService)
        {
            var dto = postDtoService.GetDtoForCreate();
            return View(dto);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public IActionResult Create(DetailPostDto dto, [FromServices] IPostDtoService postDtoService)
        {
            if (!ModelState.IsValid)
            {
                //model errors so return immediately, but the dropdown/multi-select content must be refilled first
                postDtoService.ResetSecondaryData(dto);
                return View(dto);
            }

            var status = postDtoService.Create(dto);
            if (status.IsValid)
            {
                TempData["message"] = status.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            status.CopyErrorsToModelState(ModelState, dto);
            postDtoService.ResetSecondaryData(dto);
            return View(dto);
        }

        public IActionResult Delete(int id, [FromServices] ICrudServices service)
        {

            service.DeleteAndSave<Post>(id);
            if (service.IsValid)
                TempData["message"] = service.Message;
            else
                //else errors, so send back an error message
                TempData["errorMessage"] = service.ErrorsAsHtml();
            
            return RedirectToAction("Index");
        }

        //-----------------------------------------------------
        //Code used in https://www.simple-talk.com/dotnet/.net-framework/the-.net-4.5-asyncawait-commands-in-promise-and-practice/

        public IActionResult NumPosts([FromServices] SampleWebAppDb db)
        {
            //The cast to object is to stop the View using the string as a view name
            return View((object)GetNumPosts(db));
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

        public IActionResult Reset([FromServices] SampleWebAppDb db)
        {
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Medium);
            TempData["message"] = "Successfully reset the blogs data";
            return RedirectToAction("Index");
        }
    }
}
