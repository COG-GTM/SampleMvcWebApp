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
using StatusGeneric;

namespace DataLayer.DataClasses
{

    public class SampleWebAppDb : DbContext
    {
        internal const string NameOfConnectionString = "SampleWebAppDb";

        public DbSet<Blog> Blogs { get; set; }
        public DbSet<Post> Posts { get; set; }
        public DbSet<Tag> Tags { get; set; }

        public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options) {}

        /// <summary>
        /// Convenience constructor for tests and tools that only have a connection string.
        /// The application itself should use AddDataLayer/AddDbContext.
        /// </summary>
        internal SampleWebAppDb(string connectionString)
            : base(new DbContextOptionsBuilder<SampleWebAppDb>().UseSqlServer(connectionString).Options) {}

        /// <summary>
        /// This has been overridden to handle:
        /// a) Updating of modified items that inherit from TrackUpdate
        /// b) The validation that EF6 used to run automatically, which EF Core does not do at all
        /// </summary>
        public override int SaveChanges(bool acceptAllChangesOnSuccess)
        {
            HandleChangeTracking();
            ThrowIfInvalid(ValidateChangedEntities());
            return base.SaveChanges(acceptAllChangesOnSuccess);
        }

        /// <summary>
        /// Same for async
        /// </summary>
        public override async Task<int> SaveChangesAsync(bool acceptAllChangesOnSuccess,
            CancellationToken cancellationToken = default)
        {
            HandleChangeTracking();
            ThrowIfInvalid(await ValidateChangedEntitiesAsync(cancellationToken).ConfigureAwait(false));
            return await base.SaveChangesAsync(acceptAllChangesOnSuccess, cancellationToken).ConfigureAwait(false);
        }

        /// <summary>
        /// This validates every added/modified entity and only saves if there are no errors.
        /// It replaces the EF6-era GenericServices SaveChangesWithChecking method.
        /// </summary>
        /// <returns>A status containing any validation errors. No save happens if the status has errors.</returns>
        public IStatusGeneric SaveChangesWithValidation()
        {
            var status = new StatusGenericHandler();
            HandleChangeTracking();
            status.AddValidationResults(ValidateChangedEntities());
            if (status.IsValid)
                SaveChanges();
            return status;
        }

        /// <summary>
        /// Same for async
        /// </summary>
        public async Task<IStatusGeneric> SaveChangesWithValidationAsync(CancellationToken cancellationToken = default)
        {
            var status = new StatusGenericHandler();
            HandleChangeTracking();
            status.AddValidationResults(await ValidateChangedEntitiesAsync(cancellationToken).ConfigureAwait(false));
            if (status.IsValid)
                await SaveChangesAsync(cancellationToken).ConfigureAwait(false);
            return status;
        }

        /// <summary>
        /// This runs the validation that EF6 used to run inside SaveChanges: the data annotations and
        /// IValidatableObject on every added/modified entity, plus the database-level checks.
        /// </summary>
        public IReadOnlyList<ValidationResult> ValidateChangedEntities()
        {
            var errors = new List<ValidationResult>();
            foreach (var entity in EntitiesToValidate())
            {
                errors.AddRange(ValidateEntity(entity));
                if (entity is Tag tag && Tags.Any(x => x.TagId != tag.TagId && x.Slug == tag.Slug))
                    errors.Add(DuplicateSlugError(tag));
            }
            return errors;
        }

        /// <summary>
        /// Same for async
        /// </summary>
        public async Task<IReadOnlyList<ValidationResult>> ValidateChangedEntitiesAsync(
            CancellationToken cancellationToken = default)
        {
            var errors = new List<ValidationResult>();
            foreach (var entity in EntitiesToValidate())
            {
                errors.AddRange(ValidateEntity(entity));
                if (entity is Tag tag && await Tags
                        .AnyAsync(x => x.TagId != tag.TagId && x.Slug == tag.Slug, cancellationToken)
                        .ConfigureAwait(false))
                    errors.Add(DuplicateSlugError(tag));
            }
            return errors;
        }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            //The uniqueness of a Tag's Slug is also checked before save so that the user gets a friendly error
            modelBuilder.Entity<Tag>()
                .HasIndex(x => x.Slug)
                .IsUnique();

            //EF6 auto-created a join table called TagPosts with Tag_TagId/Post_PostId columns.
            //EF Core's convention would call it PostTag with PostsPostId/TagsTagId, so it is set explicitly.
            modelBuilder.Entity<Post>()
                .HasMany(x => x.Tags)
                .WithMany(x => x.Posts)
                .UsingEntity("TagPosts",
                    l => l.HasOne(typeof(Tag)).WithMany().HasForeignKey("Tag_TagId"),
                    r => r.HasOne(typeof(Post)).WithMany().HasForeignKey("Post_PostId"));

            base.OnModelCreating(modelBuilder);
        }

        //--------------------------------------------------
        //private helpers

        /// <summary>
        /// This handles going through all the entities that have changed and seeing if they need any special handling.
        /// </summary>
        private void HandleChangeTracking()
        {
            foreach (var entry in ChangeTracker.Entries<TrackUpdate>()
                         .Where(e => e.State is EntityState.Added or EntityState.Modified))
            {
                entry.Entity.UpdateTrackingInfo();
            }
        }

        private List<object> EntitiesToValidate()
        {
            //ToList is needed, otherwise a "collection has changed" exception can happen
            return ChangeTracker.Entries()
                .Where(e => e.State is EntityState.Added or EntityState.Modified)
                .Select(e => e.Entity)
                .ToList();
        }

        private static IEnumerable<ValidationResult> ValidateEntity(object entity)
        {
            var errors = new List<ValidationResult>();
            Validator.TryValidateObject(entity, new ValidationContext(entity), errors, true);
            return errors;
        }

        private static ValidationResult DuplicateSlugError(Tag tag)
        {
            return new ValidationResult(
                string.Format("The Slug on tag '{0}' must be unique and is already being used.", tag.Name),
                new[] { "Slug" });
        }

        private static void ThrowIfInvalid(IReadOnlyList<ValidationResult> errors)
        {
            if (!errors.Any())
                return;

            //EF6 threw a DbEntityValidationException here. Callers that want the errors without an
            //exception should use SaveChangesWithValidation.
            throw new ValidationException(
                string.Join("\n", errors.Select(x => x.ErrorMessage)), null, null);
        }
    }
}
