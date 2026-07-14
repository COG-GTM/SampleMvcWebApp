using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Threading.Tasks;
using DataLayer.DataClasses.Concrete;
using DataLayer.DataClasses.Concrete.Helpers;
using GenericServices;
using Microsoft.EntityFrameworkCore;

[assembly: InternalsVisibleTo("Tests")]

namespace DataLayer.DataClasses
{
    /// <summary>
    /// EF Core replacement for the original EF6 context. It keeps the same public shape
    /// (DbSets, SaveChanges override, database-level Tag uniqueness check) so the rest of
    /// the application is unchanged.
    /// </summary>
    public class SampleWebAppDb : DbContext, IGenericServicesDbContext, IValidateOnSave
    {
        internal const string NameOfConnectionString = "SampleWebAppDb";

        /// <summary>
        /// Fallback configuration used when the context is created without DI (tests, EF tooling,
        /// the parameterless constructor). The web host configures the context through DI instead.
        /// </summary>
        public static Action<DbContextOptionsBuilder> DefaultOptionsAction { get; set; }

        public DbSet<Blog> Blogs { get; set; }
        public DbSet<Post> Posts { get; set; }
        public DbSet<Tag> Tags { get; set; }

        public SampleWebAppDb()
        {
        }

        public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options)
        {
        }

        protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
        {
            if (optionsBuilder.IsConfigured)
                return;

            if (DefaultOptionsAction != null)
                DefaultOptionsAction(optionsBuilder);
            else
                optionsBuilder.UseSqlite("Data Source=SampleWebAppDb.db");
        }

        public override int SaveChanges()
        {
            HandleChangeTracking();
            return base.SaveChanges();
        }

        public override Task<int> SaveChangesAsync(CancellationToken cancellationToken = default)
        {
            HandleChangeTracking();
            return base.SaveChangesAsync(cancellationToken);
        }

        /// <summary>
        /// Database-level validation. Replaces the EF6 <c>ValidateEntity</c> override: ensures a
        /// Tag's Slug is unique (excluding the Tag being edited).
        /// </summary>
        public IEnumerable<ValidationResult> ValidateEntity(object entity)
        {
            var tagToCheck = entity as Tag;
            if (tagToCheck == null)
                yield break;

            if (Tags.Any(x => x.TagId != tagToCheck.TagId && x.Slug == tagToCheck.Slug))
                yield return new ValidationResult(
                    string.Format("The Slug on tag '{0}' must be unique and is already being used.", tagToCheck.Name),
                    new[] { "Slug" });
        }

        //--------------------------------------------------
        //private helpers

        /// <summary>
        /// Stamps <see cref="TrackUpdate.LastUpdated"/> on every added/modified entity that
        /// tracks updates.
        /// </summary>
        private void HandleChangeTracking()
        {
            foreach (var entry in ChangeTracker.Entries()
                         .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified))
            {
                var trackUpdateClass = entry.Entity as TrackUpdate;
                if (trackUpdateClass == null)
                    continue;
                trackUpdateClass.UpdateTrackingInfo();
            }
        }
    }
}
