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
using System.Linq;
using Microsoft.AspNetCore.Mvc.ModelBinding;

namespace Tests.Helpers
{
    public static class ModelStateTester
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

        /// <summary>
        /// The old version drove System.Web.Mvc's DefaultModelBinder (which no longer exists). This rebuilds the
        /// same result the ASP.NET Core MVC validation pipeline produces: the property-level DataAnnotations are
        /// validated first and, only if there are no property errors, the object-level IValidatableObject.Validate
        /// is run. Errors are written into an ASP.NET Core <see cref="ModelStateDictionary"/> keyed by member name,
        /// with top-level (no member) errors under the "" key.
        /// </summary>
        public static ModelStateDictionary ReturnModelState(this TestModel model)
        {
            var modelState = new ModelStateDictionary();

            var hasPropertyErrors = false;
            foreach (var property in model.GetType().GetProperties())
            {
                var value = property.GetValue(model);
                var context = new ValidationContext(model) { MemberName = property.Name };

                //Each DataAnnotations attribute is evaluated independently (as the old MVC validators did).
                //Validator.TryValidateProperty is not used because it short-circuits after a failing
                //RequiredAttribute, which would hide the other attribute errors the tests expect.
                foreach (var attribute in property.GetCustomAttributes(true).OfType<ValidationAttribute>())
                {
                    var result = attribute.GetValidationResult(value, context);
                    if (result != ValidationResult.Success)
                    {
                        hasPropertyErrors = true;
                        modelState.AddModelError(property.Name, result.ErrorMessage);
                    }
                }
            }

            //ASP.NET Core (like the old MVC binder) only runs IValidatableObject.Validate when there are no
            //property-level attribute errors.
            if (!hasPropertyErrors)
            {
                foreach (var result in model.Validate(new ValidationContext(model)))
                {
                    var members = result.MemberNames?.ToList() ?? new List<string>();
                    if (members.Count == 0)
                        modelState.AddModelError("", result.ErrorMessage);
                    else
                        foreach (var member in members)
                            modelState.AddModelError(member, result.ErrorMessage);
                }
            }

            return modelState;
        }
    }
}
