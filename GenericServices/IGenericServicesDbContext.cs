using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.ChangeTracking;
using Microsoft.EntityFrameworkCore.Infrastructure;

namespace GenericServices
{
    /// <summary>
    /// Abstraction over the application's EF Core DbContext that the service layer depends on.
    /// Compatibility replacement for the original GenericServices IGenericServicesDbContext.
    /// </summary>
    public interface IGenericServicesDbContext
    {
        DbSet<TEntity> Set<TEntity>() where TEntity : class;

        EntityEntry<TEntity> Entry<TEntity>(TEntity entity) where TEntity : class;

        ChangeTracker ChangeTracker { get; }

        DatabaseFacade Database { get; }

        int SaveChanges();

        Task<int> SaveChangesAsync(CancellationToken cancellationToken = default);

        /// <summary>
        /// Hook for database level validation that cannot be expressed via DataAnnotations
        /// (e.g. uniqueness checks). Called for every Added/Modified entity before saving.
        /// Return an empty sequence if there is nothing extra to validate.
        /// </summary>
        IEnumerable<ValidationResult> ExtraValidation(EntityEntry entry);
    }
}
