#region licence
// The MIT License (MIT)
//
// Filename: ModelStateExtensions.cs
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
using Microsoft.AspNetCore.Mvc.ModelBinding;
using StatusGeneric;

namespace SampleWebApp.Infrastructure
{
    /// <summary>
    /// Replaces the EF6-GenericServices <c>CopyErrorsToModelState</c> extension. The maintained
    /// <c>EfCore.GenericServices.AspNetCore</c> package has no version compatible with
    /// <c>EfCore.GenericServices</c> 10.x, so this maps an <see cref="IStatusGeneric"/>'s errors
    /// (as produced by <c>ICrudServices</c> and <c>IPostCrudHelper</c>) onto the MVC ModelState.
    /// </summary>
    public static class ModelStateExtensions
    {
        public static void CopyErrorsToModelState(this ModelStateDictionary modelState, IStatusGeneric status)
        {
            foreach (var error in status.Errors)
            {
                var memberNames = error.ErrorResult.MemberNames?.ToList();
                if (memberNames != null && memberNames.Any())
                {
                    foreach (var member in memberNames)
                        modelState.AddModelError(member, error.ErrorResult.ErrorMessage);
                }
                else
                {
                    modelState.AddModelError(string.Empty, error.ErrorResult.ErrorMessage);
                }
            }
        }
    }
}
