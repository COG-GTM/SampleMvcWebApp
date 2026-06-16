using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using GenericLibsBase.Core;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ModelBinding;

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
        public static void CopyErrorsToModelState<T>(this ISuccessOrErrors errorHolder, ModelStateDictionary modelState, T displayDto)
        {
            if (errorHolder.IsValid) return;

            var namesThatWeShouldInclude = PropertyNamesInDto(displayDto);
            foreach (var error in errorHolder.Errors)
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
        public static void CopyErrorsToModelState(this ISuccessOrErrors errorHolder, ModelStateDictionary modelState)
        {
            if (errorHolder.IsValid) return;

            foreach (var error in errorHolder.Errors)
                modelState.AddModelError("", error.ErrorMessage);
        }

        /// <summary>
        /// This returns the ModelState errors as a json object containing the PropertyName and the error messages.
        /// Must only be called if there are model errors.
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
        /// This returns an errorsDict with any errors in ISuccessOrErrors transferred.
        /// Should only be called if there is an error
        /// </summary>
        public static JsonResult ReturnErrorsAsJson<T>(this ISuccessOrErrors errorHolder, T displayDto)
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
