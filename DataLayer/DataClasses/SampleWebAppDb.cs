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
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using DataLayer.DataClasses.Concrete;
using DataLayer.DataClasses.Concrete.Helpers;
using Microsoft.EntityFrameworkCore;

namespace DataLayer.DataClasses
{

    public class SampleWebAppDb : DbContext
    {
        internal const string NameOfConnectionString = "SampleWebAppDb";

        public DbSet<Blog> Blogs { get; set; }
        public DbSet<Post> Posts { get; set; }
        public DbSet<Tag> Tags { get; set; }

        public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options) { }

        /// <summary>
        /// This has been overridden to handle:
        /// a) Updating of modified items (see p194 in DbContext book)
        /// b) The database-level Tag.Slug uniqueness check that EF6 did via ValidateEntity
        /// </summary>
        public override int SaveChanges(bool acceptAllChangesOnSuccess)
        {
            HandleChangeTracking();
            CheckForUniqueSlugs();
            return base.SaveChanges(acceptAllChangesOnSuccess);
        }

        /// <summary>
        /// Same for async
        /// </summary>
        public override Task<int> SaveChangesAsync(bool acceptAllChangesOnSuccess, CancellationToken cancellationToken = default)
        {
            HandleChangeTracking();
            CheckForUniqueSlugs();
            return base.SaveChangesAsync(acceptAllChangesOnSuccess, cancellationToken);
        }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            //Enforce the Tag.Slug uniqueness at the database level (EF6 did this via ValidateEntity)
            modelBuilder.Entity<Tag>()
                .HasIndex(t => t.Slug)
                .IsUnique();

            //Post -> Blog (one Blogger has many Posts)
            modelBuilder.Entity<Post>()
                .HasOne(p => p.Blogger)
                .WithMany(b => b.Posts)
                .HasForeignKey(p => p.BlogId);

            //Post <-> Tag many-to-many (EF Core creates the join table automatically)
            modelBuilder.Entity<Post>()
                .HasMany(p => p.Tags)
                .WithMany(t => t.Posts);

            base.OnModelCreating(modelBuilder);
        }

        //--------------------------------------------------
        //private helpers

        /// <summary>
        /// This handles going through all the entities that have changed and seeing if they need any special handling.
        /// </summary>
        private void HandleChangeTracking()
        {
            foreach (var entity in ChangeTracker.Entries()
                                                .Where(
                                                    e =>
                                                    e.State == EntityState.Added || e.State == EntityState.Modified))
            {
                var trackUpdateClass = entity.Entity as TrackUpdate;
                if (trackUpdateClass == null) continue;
                trackUpdateClass.UpdateTrackingInfo();
            }
        }

        /// <summary>
        /// This reimplements the EF6 ValidateEntity Slug-uniqueness check: for any added/modified Tag,
        /// ensure no other Tag already uses the same Slug (excluding the Tag itself). It throws on a
        /// direct <see cref="SaveChanges()"/> so callers that bypass GenericServices still get protection.
        /// GenericServices callers surface the same problem gracefully via <see cref="GetSlugUniquenessErrors"/>
        /// wired into its BeforeSaveChanges hook, so the throw is not reached on that path.
        /// </summary>
        private void CheckForUniqueSlugs()
        {
            var firstError = GetSlugUniquenessErrors().FirstOrDefault();
            if (firstError != null)
                throw new ValidationException(firstError);
        }

        /// <summary>
        /// Returns a user-friendly error message for every added/modified Tag whose Slug collides with
        /// another Tag's Slug. Empty when all Slugs are unique. Used both by the throwing
        /// <see cref="CheckForUniqueSlugs"/> and by GenericServices' BeforeSaveChanges hook so a duplicate
        /// Slug is reported as a validation error rather than an unhandled exception.
        /// </summary>
        public IReadOnlyList<string> GetSlugUniquenessErrors()
        {
            var errors = new List<string>();
            var changedTags = ChangeTracker.Entries<Tag>()
                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified)
                .Select(e => e.Entity)
                .ToList();

            foreach (var tagToCheck in changedTags)
            {
                if (Tags.Any(x => x.TagId != tagToCheck.TagId && x.Slug == tagToCheck.Slug))
                    errors.Add(string.Format("The Slug on tag '{0}' must be unique and is already being used.", tagToCheck.Name));
            }

            return errors;
        }
    }
}
