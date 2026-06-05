using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Threading.Tasks;
using GenericLibsBase.Core;
using Microsoft.EntityFrameworkCore;

namespace GenericServices
{
    /// <summary>
    /// Provides EF Core save helpers that run DataAnnotations / IValidatableObject validation
    /// (which EF Core does not do automatically) and return an <see cref="ISuccessOrErrors"/>.
    /// </summary>
    public static class SaveChangesExtensions
    {
        public static ISuccessOrErrors SaveChangesWithChecking(this IGenericServicesDbContext context)
        {
            var errors = CollectValidationErrors(context);
            if (errors.Count > 0)
                return BuildErrorStatus(errors);

            context.SaveChanges();
            return SuccessOrErrors.Success("Successfully saved the data.");
        }

        public static async Task<ISuccessOrErrors> SaveChangesWithCheckingAsync(this IGenericServicesDbContext context)
        {
            var errors = CollectValidationErrors(context);
            if (errors.Count > 0)
                return BuildErrorStatus(errors);

            await context.SaveChangesAsync().ConfigureAwait(false);
            return SuccessOrErrors.Success("Successfully saved the data.");
        }

        private static List<ValidationResult> CollectValidationErrors(IGenericServicesDbContext context)
        {
            var errors = new List<ValidationResult>();
            var changed = context.ChangeTracker.Entries()
                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified)
                .ToList();

            foreach (var entry in changed)
            {
                var entity = entry.Entity;
                var validationContext = new ValidationContext(entity, null, null);
                var entityErrors = new List<ValidationResult>();
                Validator.TryValidateObject(entity, validationContext, entityErrors, true);
                errors.AddRange(entityErrors);
                errors.AddRange(context.ExtraValidation(entry));
            }

            return errors;
        }

        private static ISuccessOrErrors BuildErrorStatus(IEnumerable<ValidationResult> errors)
        {
            var status = new SuccessOrErrors();
            status.AddValidationResults(errors);
            return status;
        }
    }
}
