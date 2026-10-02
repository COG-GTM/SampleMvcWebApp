#region licence
// The MIT License (MIT)
// 
// Filename: ModelStateTester.cs
// Date Created: 2014/06/10
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
using System.ComponentModel.DataAnnotations;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using Microsoft.AspNetCore.Mvc.ModelBinding.Validation;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;

namespace Tests.Helpers
{
    static class ModelStateTester
    {

        public class TestModel : IValidatableObject
        {
            [MinLength(2)]
            [Required]
            public string MyString { get; set; }

            [Range(0,100)]
            public int MyInt { get; set; }

            public bool CreateValidationError { get; set; }

            public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
            {
                if (CreateValidationError)
                    yield return new ValidationResult("This is a top level error caused by CreateValidationError being set.");

                if (MyInt == 50)
                    yield return new ValidationResult("This is a top level error caused by MyInt having value 50.");
            }

            public TestModel()
            {
            }

            public TestModel(string myString, int myInt, bool createValidationError)
            {
                MyString = myString;
                MyInt = myInt;
                CreateValidationError = createValidationError;
            }
        }


        private class TestController : Controller
        {
            public IActionResult ValidDateTestModel(TestModel model)
            {
                // ReSharper disable once Mvc.ViewNotResolved
                return View(model);
            }
        }

        //ASP.NET Core's MVC services, so model validation uses the same validators/metadata as the web app
        private static readonly Lazy<IServiceProvider> MvcServices = new Lazy<IServiceProvider>(() =>
        {
            var services = new ServiceCollection();
            services.AddLogging();
            services.AddControllersWithViews();
            return services.BuildServiceProvider();
        });

        /// <summary>
        /// Runs ASP.NET Core MVC model validation on the model (what happens after model binding of an action parameter)
        /// and returns the ModelState the action would see
        /// </summary>
        public static ModelStateDictionary ReturnModelState(this TestModel model)
        {
            var serviceProvider = MvcServices.Value;
            var httpContext = new DefaultHttpContext { RequestServices = serviceProvider };
            var testController = new TestController
            {
                ControllerContext = new ControllerContext(
                    new ActionContext(httpContext, new RouteData(), new Microsoft.AspNetCore.Mvc.Controllers.ControllerActionDescriptor())),
                ObjectValidator = serviceProvider.GetRequiredService<IObjectModelValidator>(),
                MetadataProvider = serviceProvider.GetRequiredService<IModelMetadataProvider>()
            };

            testController.ModelState.Clear();
            testController.TryValidateModel(model, string.Empty);

            var viewResult = (ViewResult) testController.ValidDateTestModel(model);
            return viewResult.ViewData.ModelState;
        }


    }
}
