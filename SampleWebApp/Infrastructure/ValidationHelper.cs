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
        /// This transfers error messages from the DtoValidation methods to the MVC modelState error dictionary.
        /// It looks for errors that have member names corresponding to the properties in the displayDto.
        /// This means that errors assciated with a field on display will show next to the name. 
        /// Other errors will be shown in the ValidationSummary
        /// </summary>
        /// <param name="status">The interface that holds the errors</param>
        /// <param name="modelState">The MVC modelState to add errors to</param>
        /// <param name="displayDto">This is the Dto that will be used to display the error messages</param>
        public static void CopyErrorsToModelState<T>(this IStatusGeneric status, ModelStateDictionary modelState, T displayDto) 
        {
            if (status.IsValid) return;

            var namesThatWeShouldInclude = PropertyNamesInDto(displayDto);
            foreach (var error in status.Errors.Select(x => x.ErrorResult))
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
        /// This copies errors for general display where we are not returning to a page with the fields on them
        /// </summary>
        /// <param name="status"></param>
        /// <param name="modelState"></param>
        public static void CopyErrorsToModelState(this IStatusGeneric status, ModelStateDictionary modelState)
        {
            if (status.IsValid) return;

            foreach (var error in status.Errors)
                    modelState.AddModelError("", error.ErrorResult.ErrorMessage);
        }

        /// <summary>
        /// This returns the errors as html-encoded strings separated by line breaks, or empty string if no errors
        /// </summary>
        /// <param name="status"></param>
        /// <returns></returns>
        public static string ErrorsAsHtml(this IStatusGeneric status)
        {
            if (status.IsValid) return string.Empty;

            return string.Join("<br/>", status.Errors.Select(x => WebUtility.HtmlEncode(x.ErrorResult.ErrorMessage)));
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
        /// This returns and errorsDict with any errors in IStatusGeneric transferred
        /// It looks for errors that have member names corresponding to the properties in the displayDto.
        /// This means that errors assciated with a field on display will show next to the name. 
        /// Other errors will be shown in the ValidationSummary
        /// Should only be called if there is an error
        /// </summary>
        /// <param name="status">The interface that holds the errors</param>
        /// <param name="displayDto">Dto that the error messages came from</param>
        /// <returns>It returns a JsonResult with one parameter called errorsDict which contains key value pairs.
        /// The key is the name of the property which had the error, or is empty string if global error.
        /// The value is an array of error strings for that property key</returns>
        public static JsonResult ReturnErrorsAsJson<T>(this IStatusGeneric status, T displayDto)
        {
            if (status.IsValid)
                throw new ArgumentException("You should only call ReturnErrorsAsJson when there are errors in the status", nameof(status));

            var modelState = new ModelStateDictionary();
            status.CopyErrorsToModelState(modelState, displayDto);
            return modelState.ReturnModelErrorsAsJson();
        }

        private static IList<string> PropertyNamesInDto<T> ( T objectToCheck)
        {
            return
                objectToCheck.GetType()
                             .GetProperties(BindingFlags.Public | BindingFlags.Instance)
                             .Select(x => x.Name)
                             .ToList();
        }

    }
}
