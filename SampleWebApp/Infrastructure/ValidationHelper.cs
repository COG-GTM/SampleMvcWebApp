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
using System.Net;
using System.Reflection;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;
using StatusGeneric;

namespace SampleWebApp.Infrastructure
{
    public static class ValidationHelper
    {
        /// <summary>
        /// This copies the errors in the status to the ModelState. Errors with member names that are in the
        /// display dto are shown by the property, all others are shown in the validation summary
        /// </summary>
        public static void CopyErrorsToModelState<T>(this IStatusGeneric errorHolder, ModelStateDictionary modelState, T displayDto) 
        {
            if (errorHolder.IsValid) return;

            var namesThatWeShouldInclude = PropertyNamesInDto(displayDto);
            foreach (var error in errorHolder.Errors.Select(x => x.ErrorResult))
            {
                if (!error.MemberNames.Any())
                    modelState.AddModelError("", error.ErrorMessage);
                else
                    foreach (var errorKeyName in error.MemberNames)
                        modelState.AddModelError(
                            (namesThatWeShouldInclude.Any(x => x == errorKeyName) ? errorKeyName : ""),
                            error.ErrorMessage);
            }
        }

        /// <summary>
        /// This copies the errors in the status to the ModelState as top-level (validation summary) errors
        /// </summary>
        public static void CopyErrorsToModelState(this IStatusGeneric errorHolder, ModelStateDictionary modelState)
        {
            if (errorHolder.IsValid) return;

            foreach (var error in errorHolder.Errors)
                modelState.AddModelError("", error.ErrorResult.ErrorMessage);
        }

        /// <summary>
        /// Returns the errors as HTML-encoded text, one per line
        /// </summary>
        public static string ErrorsAsHtml(this IStatusGeneric errorHolder)
        {
            return string.Join("<br/>", errorHolder.Errors.Select(x => WebUtility.HtmlEncode(x.ErrorResult.ErrorMessage)));
        }

        /// <summary>
        /// This returns the model errors as a json object, for use in ajax calls
        /// </summary>
        public static JsonResult ReturnModelErrorsAsJson(this ModelStateDictionary modelState)
        {
            if (modelState.IsValid)
                throw new ArgumentException("You should only call this if there are model errors to return.");

            var dict = new Dictionary<string, object>();
            var emptyNameErrors = new List<string>();
            foreach (var propertyError in modelState.Where(x => x.Value.Errors.Any()))
            {
                if (string.IsNullOrEmpty(propertyError.Key))
                    emptyNameErrors.AddRange(propertyError.Value.Errors.Select(x => x.ErrorMessage));
                else
                    dict[propertyError.Key] = new { errors = propertyError.Value.Errors.Select(x => x.ErrorMessage) };
            }
            if (emptyNameErrors.Any())
                dict[string.Empty] = new { errors = emptyNameErrors };

            return new JsonResult(new { errorsDict = dict });
        }

        /// <summary>
        /// This returns the errors in the status as a json object, for use in ajax calls
        /// </summary>
        public static JsonResult ReturnErrorsAsJson<T>(this IStatusGeneric errorHolder, T displayDto)
        {
            if (errorHolder.IsValid)
                throw new ArgumentException("You should only call ReturnErrorsAsJson when there are errors in the status", nameof(errorHolder));

            var modelState = new ModelStateDictionary();
            errorHolder.CopyErrorsToModelState(modelState, displayDto);
            return modelState.ReturnModelErrorsAsJson();
        }

        private static IList<string> PropertyNamesInDto<T> ( T objectToCheck)
        {
            return objectToCheck.GetType()
                             .GetProperties(BindingFlags.Public | BindingFlags.Instance)
                             .Select(x => x.Name)
                             .ToList();
        }
    }
}
