#region licence
// The MIT License (MIT)
// 
// Filename: SampleWebAppDb.cs
// Date Created: 2014/05/20
// 
// Copyright (c) 2014 Jon Smith (www.selectiveanalytics.com & www.thereformedprogrammer.net)
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
#endregion
using System.ComponentModel.DataAnnotations;
using DataLayer.DataClasses.Concrete;
using DataLayer.DataClasses.Concrete.Helpers;
using Microsoft.EntityFrameworkCore;
using StatusGeneric;

namespace DataLayer.DataClasses
{

    public class SampleWebAppDb : DbContext
    {
        public const string NameOfConnectionString = "SampleWebAppDb";

        public DbSet<Blog> Blogs { get; set; }
        public DbSet<Post> Posts { get; set; }
        public DbSet<Tag> Tags { get; set; }

        public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options) { }

        /// <summary>
        /// This has been overridden to handle:
        /// a) Updating of modified items (see p194 in DbContext book)
        /// The parameterless SaveChanges/SaveChangesAsync overloads funnel into these.
        /// </summary>
        public override int SaveChanges(bool acceptAllChangesOnSuccess)
        {
            HandleChangeTracking();
            return base.SaveChanges(acceptAllChangesOnSuccess);
        }

        public override Task<int> SaveChangesAsync(bool acceptAllChangesOnSuccess,
            CancellationToken cancellationToken = default)
        {
            HandleChangeTracking();
            return base.SaveChangesAsync(acceptAllChangesOnSuccess, cancellationToken);
        }

        /// <summary>
        /// Validates all Added/Modified entities (DataAnnotations + IValidatableObject, which includes
        /// the Tag slug uniqueness check) and only saves if there are no errors.
        /// Validation errors are returned in the status, never thrown.
        /// </summary>
        public IStatusGeneric SaveChangesWithChecking()
        {
            var status = ValidateTrackedEntities();
            if (status.IsValid)
                SaveChanges();
            return status;
        }

        public async Task<IStatusGeneric> SaveChangesWithCheckingAsync()
        {
            var status = ValidateTrackedEntities();
            if (status.IsValid)
                await SaveChangesAsync();
            return status;
        }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            modelBuilder.Entity<Tag>()
                .HasIndex(t => t.Slug)
                .IsUnique();

            modelBuilder.Entity<Post>()
                .HasOne(p => p.Blogger)
                .WithMany(b => b.Posts)
                .HasForeignKey(p => p.BlogId)
                .IsRequired();

            //Keeps the join table name/columns that EF6 created for the Post <-> Tag many-to-many
            modelBuilder.Entity<Post>()
                .HasMany(p => p.Tags)
                .WithMany(t => t.Posts)
                .UsingEntity<Dictionary<string, object>>(
                    "TagPosts",
                    r => r.HasOne<Tag>().WithMany().HasForeignKey("Tag_TagId"),
                    l => l.HasOne<Post>().WithMany().HasForeignKey("Post_PostId"),
                    j => j.HasKey("Tag_TagId", "Post_PostId"));
        }

        //--------------------------------------------------
        //private helpers

        private IStatusGeneric ValidateTrackedEntities()
        {
            var status = new StatusGenericHandler();
            var serviceProvider = new DbContextServiceProvider(this);
            var entriesToCheck = ChangeTracker.Entries()
                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified)
                .Select(e => e.Entity)
                .Where(e => !(e is Dictionary<string, object>))        //skip many-to-many join rows
                .ToList();
            foreach (var entity in entriesToCheck)
            {
                var results = new List<ValidationResult>();
                if (!Validator.TryValidateObject(entity, new ValidationContext(entity, serviceProvider, null), results, true))
                    status.AddValidationResults(results);
            }
            return status;
        }

        private void HandleChangeTracking()
        {
            foreach (var entry in ChangeTracker.Entries<TrackUpdate>()
                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified))
            {
                entry.Entity.UpdateTrackingInfo();
            }
        }

        private class DbContextServiceProvider : IServiceProvider
        {
            private readonly SampleWebAppDb _db;

            public DbContextServiceProvider(SampleWebAppDb db)
            {
                _db = db;
            }

            public object GetService(Type serviceType)
            {
                return serviceType == typeof(DbContext) || serviceType == typeof(SampleWebAppDb) ? _db : null;
            }
        }
    }
}
