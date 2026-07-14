using System;
using System.Threading;
using System.Threading.Tasks;
using GenericLibsBase.Core;
using GenericServices.Core;
using Microsoft.EntityFrameworkCore;

namespace GenericServices
{
    /// <summary>
    /// Provides the GenericServices "save with checking" behaviour on top of EF Core:
    /// it validates the tracked changes first (as EF6 did automatically) and turns any
    /// validation or concurrency failure into an <see cref="ISuccessOrErrors"/> result
    /// rather than throwing.
    /// </summary>
    public static class SaveChangesExtensions
    {
        public static ISuccessOrErrors SaveChangesWithChecking(this IGenericServicesDbContext context)
        {
            var dbContext = AsDbContext(context);

            var validation = GenericValidation.Validate(dbContext);
            if (!validation.IsValid)
                return validation;

            try
            {
                dbContext.SaveChanges();
            }
            catch (DbUpdateConcurrencyException ex)
            {
                return ConcurrencyError(ex);
            }

            return SuccessOrErrors.Success("Successfully saved.");
        }

        public static async Task<ISuccessOrErrors> SaveChangesWithCheckingAsync(
            this IGenericServicesDbContext context, CancellationToken cancellationToken = default)
        {
            var dbContext = AsDbContext(context);

            var validation = GenericValidation.Validate(dbContext);
            if (!validation.IsValid)
                return validation;

            try
            {
                await dbContext.SaveChangesAsync(cancellationToken).ConfigureAwait(false);
            }
            catch (DbUpdateConcurrencyException ex)
            {
                return ConcurrencyError(ex);
            }

            return SuccessOrErrors.Success("Successfully saved.");
        }

        private static DbContext AsDbContext(IGenericServicesDbContext context)
        {
            var dbContext = context as DbContext;
            if (dbContext == null)
                throw new InvalidOperationException(
                    "SaveChangesWithChecking requires the context to derive from EF Core's DbContext.");
            return dbContext;
        }

        private static ISuccessOrErrors ConcurrencyError(DbUpdateConcurrencyException ex)
        {
            return new SuccessOrErrors().AddSingleError(
                "Another user changed this data at the same time. Please retry your edit. ({0})",
                ex.Message);
        }
    }
}
