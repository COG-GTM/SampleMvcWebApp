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
using GenericServices;
using Microsoft.AspNetCore.Mvc;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using SampleWebApp.Infrastructure;
using ServiceLayer.PostServices;


namespace SampleWebApp.Controllers
{
    /// <summary>
    /// This is an example of a Controller using GenericServices database commands with a DTO.
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
        public ActionResult Index(int? id, [FromServices] ICrudServices service)
        {
            var filtered = id != null && id != 0;
            var query = service.ReadManyNoTracked<SimplePostDto>().FilterByBlogId(id);
            if (filtered)
                TempData["message"] = "Filtered list";

            return View(query.ToList());
        }

        public ActionResult Details(int id, [FromServices] IDetailPostService service)
        {
            var dto = service.GetDetail(id);
            if (dto == null)
                return NotFound();
            return View(dto);
        }

        public ActionResult Edit(int id, [FromServices] IDetailPostService service)
        {
            var dto = service.GetForEdit(id);
            if (dto == null)
                return NotFound();
            return View(dto);
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public ActionResult Edit(DetailPostDto dto, [FromServices] IDetailPostService service)
        {
            if (!ModelState.IsValid)
                //model errors so return immediately
                return View(service.ResetDto(dto));

            var response = service.Update(dto);
            if (response.IsValid)
            {
                TempData["message"] = response.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            response.CopyErrorsToModelState(ModelState, dto);
            return View(service.ResetDto(dto));
        }

        public ActionResult Create([FromServices] IDetailPostService service)
        {
            return View(service.GetNew());
        }

        [HttpPost]
        [ValidateAntiForgeryToken]
        public ActionResult Create(DetailPostDto dto, [FromServices] IDetailPostService service)
        {
            if (!ModelState.IsValid)
                //model errors so return immediately
                return View(service.ResetDto(dto));

            var response = service.Create(dto);
            if (response.IsValid)
            {
                TempData["message"] = response.Message;
                return RedirectToAction("Index");
            }

            //else errors, so copy the errors over to the ModelState and return to view
            response.CopyErrorsToModelState(ModelState, dto);
            return View(service.ResetDto(dto));
        }

        public ActionResult Delete(int id, [FromServices] ICrudServices service)
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

        public ActionResult NumPosts([FromServices] SampleWebAppDb db)
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

        public ActionResult CodeView()
        {
            return View();
        }

        public ActionResult Delay()
        {
            Thread.Sleep(500);
            return View(500);
        }

        public ActionResult Reset([FromServices] SampleWebAppDb db)
        {
            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Medium);
            TempData["message"] = "Successfully reset the blogs data";
            return RedirectToAction("Index");
        }
    }
}