using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Runtime.CompilerServices;
using DataLayer.DataClasses.Concrete;
using DataLayer.DataClasses.Concrete.Helpers;
using GenericServices;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.ChangeTracking;

[assembly: InternalsVisibleTo("Tests")]

namespace DataLayer.DataClasses
{
    public class SampleWebAppDb : DbContext, IGenericServicesDbContext
    {
        public DbSet<Blog> Blogs { get; set; }
        public DbSet<Post> Posts { get; set; }
        public DbSet<Tag> Tags { get; set; }

        public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options) { }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            modelBuilder.Entity<Post>()
                .HasOne(p => p.Blogger)
                .WithMany(b => b.Posts)
                .HasForeignKey(p => p.BlogId);

            modelBuilder.Entity<Post>()
                .HasMany(p => p.Tags)
                .WithMany(t => t.Posts);
        }

        /// <summary>
        /// Overridden to update the LastUpdated tracking information before saving (see TrackUpdate).
        /// </summary>
        public override int SaveChanges()
        {
            HandleChangeTracking();
            return base.SaveChanges();
        }

        public override System.Threading.Tasks.Task<int> SaveChangesAsync(
            System.Threading.CancellationToken cancellationToken = default)
        {
            HandleChangeTracking();
            return base.SaveChangesAsync(cancellationToken);
        }

        /// <summary>
        /// Database level validation that cannot be expressed via DataAnnotations.
        /// Enforces uniqueness of a Tag's Slug.
        /// </summary>
        public IEnumerable<ValidationResult> ExtraValidation(EntityEntry entry)
        {
            if (entry.Entity is Tag tagToCheck &&
                (entry.State == EntityState.Added || entry.State == EntityState.Modified))
            {
                if (Tags.Any(x => x.TagId != tagToCheck.TagId && x.Slug == tagToCheck.Slug))
                    yield return new ValidationResult(
                        string.Format("The Slug on tag '{0}' must be unique and is already being used.", tagToCheck.Name),
                        new[] { "Slug" });
            }
        }

        //--------------------------------------------------
        //private helpers

        private void HandleChangeTracking()
        {
            foreach (var entity in ChangeTracker.Entries()
                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified))
            {
                var trackUpdateClass = entity.Entity as TrackUpdate;
                if (trackUpdateClass == null)
                    continue;
                trackUpdateClass.UpdateTrackingInfo();
            }
        }
    }
}
