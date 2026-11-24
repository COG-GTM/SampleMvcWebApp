using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace DataLayer.DataClasses
{
    public static class SaveChangesWithCheckingExtensions
    {
        /// <summary>
        /// Validates all added/modified entities (data annotations + IValidatableObject) and only saves if they are all valid
        /// </summary>
        public static IStatusGeneric SaveChangesWithChecking(this DbContext context)
        {
            var status = ValidateTrackedEntities(context);
            if (status.IsValid)
                context.SaveChanges();
            return status;
        }

        public static async Task<IStatusGeneric> SaveChangesWithCheckingAsync(this DbContext context)
        {
            var status = ValidateTrackedEntities(context);
            if (status.IsValid)
                await context.SaveChangesAsync();
            return status;
        }

        private static IStatusGeneric ValidateTrackedEntities(DbContext context)
        {
            var status = new StatusGenericHandler();
            var provider = new DbContextServiceProvider(context);
            foreach (var entry in context.ChangeTracker.Entries()
                         .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified).ToList())
            {
                var results = new List<ValidationResult>();
                Validator.TryValidateObject(entry.Entity, new ValidationContext(entry.Entity, provider, null), results, true);
                status.AddValidationResults(results);
            }
            return status;
        }

        private class DbContextServiceProvider : IServiceProvider
        {
            private readonly DbContext _context;

            public DbContextServiceProvider(DbContext context) { _context = context; }

            public object GetService(Type serviceType)
            {
                return serviceType.IsInstanceOfType(_context) ? _context : null;
            }
        }
    }
}
