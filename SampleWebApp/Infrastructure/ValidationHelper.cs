using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;

namespace SampleWebApp.Infrastructure
{
    public static class ValidationHelper
    {
        public static Microsoft.AspNetCore.Mvc.JsonResult ReturnModelErrorsAsJson(this ModelStateDictionary modelState)
        {
            if (modelState.IsValid)
                throw new ArgumentException("You should only call this if there are model errors to return.");

            var dict = new Dictionary<string, object>();
            var emptyNameErrors = new List<string>();
            foreach (var propertyError in modelState.Where(x => x.Value!.Errors.Any()))
            {
                if (string.IsNullOrEmpty(propertyError.Key))
                    emptyNameErrors.AddRange(propertyError.Value!.Errors.Select(x => x.ErrorMessage));
                else
                    dict[propertyError.Key] = new { errors = propertyError.Value!.Errors.Select(x => x.ErrorMessage) };
            }

            if (emptyNameErrors.Any())
                dict[string.Empty] = new { errors = emptyNameErrors };

            return new Microsoft.AspNetCore.Mvc.JsonResult(new { errorsDict = dict });
        }
    }
}
