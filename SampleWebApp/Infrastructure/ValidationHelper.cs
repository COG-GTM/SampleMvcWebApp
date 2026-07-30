#region licence
// The MIT License (MIT)
// 
// Filename: ValidationHelper.cs
// Date Created: 2014/05/20
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
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using StatusGeneric;

namespace SampleWebApp.Infrastructure
{
    public static class ValidationHelper
    {
        /// <summary>
        /// This transfers error messages from the status returned by the service layer to the MVC modelState
        /// error dictionary. It looks for errors that have member names corresponding to the properties in the
        /// displayDto. This means that errors assciated with a field on display will show next to the name. 
        /// Other errors will be shown in the ValidationSummary
        /// </summary>
        /// <param name="errorHolder">The interface that holds the errors</param>
        /// <param name="modelState">The MVC modelState to add errors to</param>
        /// <param name="displayDto">This is the Dto that will be used to display the error messages</param>
        public static void CopyErrorsToModelState<T>(this IStatusGeneric errorHolder, ModelStateDictionary modelState, T displayDto)
        {
            if (errorHolder.IsValid) return;

            var namesThatWeShouldInclude = PropertyNamesInDto(displayDto);
            foreach (var error in errorHolder.Errors)
            {
                var memberNames = error.ErrorResult.MemberNames.ToList();
                if (!memberNames.Any())
                    modelState.AddModelError("", error.ErrorResult.ErrorMessage);
                else
                    foreach (var errorKeyName in memberNames)
                        modelState.AddModelError(
                            (namesThatWeShouldInclude.Any(x => x == errorKeyName) ? errorKeyName : ""),
                            error.ErrorResult.ErrorMessage);
            }
        }

        /// <summary>
        /// This copies errors for general display where we are not returning to a page with the fields on them
        /// </summary>
        /// <param name="errorHolder"></param>
        /// <param name="modelState"></param>
        public static void CopyErrorsToModelState(this IStatusGeneric errorHolder, ModelStateDictionary modelState)
        {
            if (errorHolder.IsValid) return;

            foreach (var error in errorHolder.Errors)
                modelState.AddModelError("", error.ErrorResult.ErrorMessage);
        }

        /// <summary>
        /// This turns the errors in a status into the html that the views show via TempData["errorMessage"].
        /// ASP.NET Core's TempData can only round-trip primitives and strings, so it replaces the old
        /// new MvcHtmlString(response.ErrorsAsHtml()) and is rendered with @Html.Raw(...)
        /// </summary>
        public static string ErrorsAsHtml(this IStatusGeneric errorHolder)
        {
            return string.Join("<br/>", errorHolder.Errors.Select(x => x.ErrorResult.ErrorMessage));
        }

        /// <summary>
        /// This returns the ModelState errors as a json array containing objects with the PropertyName and the first error message.
        /// Must only be called if there are model errors.
        /// </summary>
        /// <param name="modelState"></param>
        /// <returns>It returns a JsonResult with one parameter called errorsDict which contains key value pairs.
        /// The key is the name of the property which had the error, or is empty string if global error.
        /// The value is an array of error strings for that property key</returns>
        public static JsonResult ReturnModelErrorsAsJson(this ModelStateDictionary modelState)
        {
            if (modelState.IsValid)
                throw new ArgumentException("You should only call this if there are model errors to return.");

            var dict = new Dictionary<string, object>();
            var emptyNameErrors = new List<string>();
            foreach (var propertyError in modelState.Where(x => x.Value.Errors.Any()))
            {
                if (string.IsNullOrEmpty(propertyError.Key))
                    //The modelState doesn't seem to combine empty named items so we do it for it
                    emptyNameErrors.AddRange(propertyError.Value.Errors.Select(x => x.ErrorMessage));
                else
                    dict[propertyError.Key] = new { errors = propertyError.Value.Errors.Select(x => x.ErrorMessage) };
            }

            if (emptyNameErrors.Any())
                dict[string.Empty] = new { errors = emptyNameErrors };

            return new JsonResult(new { errorsDict = dict });
        }

        /// <summary>
        /// This returns and errorsDict with any errors in the status transferred
        /// It looks for errors that have member names corresponding to the properties in the displayDto.
        /// This means that errors assciated with a field on display will show next to the name. 
        /// Other errors will be shown in the ValidationSummary
        /// Should only be called if there is an error
        /// </summary>
        /// <param name="errorHolder">The interface that holds the errors</param>
        /// <param name="displayDto">Dto that the error messages came from</param>
        /// <returns>It returns a JsonResult with one parameter called errorsDict which contains key value pairs.
        /// The key is the name of the property which had the error, or is empty string if global error.
        /// The value is an array of error strings for that property key</returns>
        public static JsonResult ReturnErrorsAsJson<T>(this IStatusGeneric errorHolder, T displayDto)
        {
            if (errorHolder.IsValid)
                throw new ArgumentException("You should only call ReturnErrorsAsJson when there are errors in the status", "errorHolder");

            var modelState = new ModelStateDictionary();
            errorHolder.CopyErrorsToModelState(modelState, displayDto);
            return modelState.ReturnModelErrorsAsJson();
        }

        private static IList<string> PropertyNamesInDto<T>(T objectToCheck)
        {
            return
                objectToCheck.GetType()
                             .GetProperties(BindingFlags.Public | BindingFlags.Instance)
                             .Select(x => x.Name)
                             .ToList();
        }
    }
}
