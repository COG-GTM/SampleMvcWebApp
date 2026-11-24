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

        /// <summary>
        /// Validates the model the same way as ASP.NET Core MVC: property attributes first, and
        /// IValidatableObject.Validate only if all the properties are valid
        /// </summary>
        public static ModelStateDictionary ReturnModelState(this TestModel model)
        {
            var modelState = new ModelStateDictionary();
            var results = new List<ValidationResult>();
            var context = new ValidationContext(model);
            foreach (var property in typeof(TestModel).GetProperties())
                foreach (var attribute in property.GetCustomAttributes(typeof(ValidationAttribute), true).Cast<ValidationAttribute>())
                {
                    var result = attribute.GetValidationResult(property.GetValue(model),
                        new ValidationContext(model) { MemberName = property.Name, DisplayName = property.Name });
                    if (result != ValidationResult.Success)
                        results.Add(result);
                }
            if (!results.Any())
                results.AddRange(model.Validate(context));

            foreach (var result in results)
                modelState.AddModelError(result.MemberNames.FirstOrDefault() ?? "", result.ErrorMessage);
            return modelState;
        }
    }
}
