using System.Threading;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.ChangeTracking;

namespace GenericServices
{
    /// <summary>
    /// Abstraction over the EF Core <see cref="DbContext"/> used by the service layer and DTOs.
    /// The signatures deliberately match <see cref="DbContext"/> so a context can implement this
    /// interface without any extra code.
    /// </summary>
    public interface IGenericServicesDbContext
    {
        DbSet<TEntity> Set<TEntity>() where TEntity : class;

        EntityEntry<TEntity> Entry<TEntity>(TEntity entity) where TEntity : class;

        EntityEntry Entry(object entity);

        int SaveChanges();

        Task<int> SaveChangesAsync(CancellationToken cancellationToken = default);
    }
}
