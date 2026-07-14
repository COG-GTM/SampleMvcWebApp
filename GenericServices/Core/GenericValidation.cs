using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using GenericLibsBase.Core;
using Microsoft.EntityFrameworkCore;

namespace GenericServices.Core
{
    /// <summary>
    /// Reproduces the validation EF6 used to run automatically on <c>SaveChanges</c>:
    /// DataAnnotations + <see cref="IValidatableObject"/> on every Added/Modified entity,
    /// plus any database-level checks exposed via <see cref="IValidateOnSave"/>.
    /// </summary>
    internal static class GenericValidation
    {
        public static ISuccessOrErrors Validate(DbContext context)
        {
            var status = new SuccessOrErrors();
            var customValidator = context as IValidateOnSave;

            var changedEntities = context.ChangeTracker.Entries()
                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified)
                .ToList();

            foreach (var entry in changedEntities)
            {
                var entity = entry.Entity;

                var validationContext = new ValidationContext(entity, serviceProvider: null, items: null);
                var results = new List<ValidationResult>();
                Validator.TryValidateObject(entity, validationContext, results, validateAllProperties: true);

                if (customValidator != null)
                {
                    var extra = customValidator.ValidateEntity(entity);
                    if (extra != null)
                        results.AddRange(extra);
                }

                foreach (var result in results)
                {
                    var memberName = result.MemberNames == null ? null : result.MemberNames.FirstOrDefault();
                    if (string.IsNullOrEmpty(memberName))
                        status.AddSingleError(SafeFormat(result.ErrorMessage));
                    else
                        status.AddNamedParameterError(memberName, SafeFormat(result.ErrorMessage));
                }
            }

            return status.IsValid ? status.SetSuccessMessage("Successfully validated.") : status;
        }

        //Error messages may legitimately contain braces, so avoid string.Format re-parsing them.
        private static string SafeFormat(string message)
        {
            return message ?? string.Empty;
        }
    }
}
