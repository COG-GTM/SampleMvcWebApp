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
using System;
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
        public DbSet<Blog> Blogs { get; set; }
        public DbSet<Post> Posts { get; set; }
        public DbSet<Tag> Tags { get; set; }

        public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options) { }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            // Configure the many-to-many relationship between Post and Tag
            modelBuilder.Entity<Post>()
                .HasMany(p => p.Tags)
                .WithMany(t => t.Posts);

            modelBuilder.Entity<Post>()
                .HasOne(p => p.Blogger)
                .WithMany(b => b.Posts)
                .HasForeignKey(p => p.BlogId);

            // Configure Tag slug uniqueness
            modelBuilder.Entity<Tag>()
                .HasIndex(t => t.Slug)
                .IsUnique();
        }

        public override int SaveChanges()
        {
            HandleChangeTracking();
            var validationErrors = GetValidationErrors();
            if (validationErrors.Any())
            {
                throw new ValidationException(string.Join("; ", validationErrors));
            }
            return base.SaveChanges();
        }

        public override Task<int> SaveChangesAsync(CancellationToken cancellationToken = default)
        {
            HandleChangeTracking();
            var validationErrors = GetValidationErrors();
            if (validationErrors.Any())
            {
                throw new ValidationException(string.Join("; ", validationErrors));
            }
            return base.SaveChangesAsync(cancellationToken);
        }

        private void HandleChangeTracking()
        {
            foreach (var entity in ChangeTracker.Entries()
                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified))
            {
                var trackUpdateEntity = entity.Entity as TrackUpdate;
                if (trackUpdateEntity != null)
                    trackUpdateEntity.UpdateTrackingInfo();
            }
        }

        private List<string> GetValidationErrors()
        {
            var errors = new List<string>();

            // Validate Tag slug uniqueness
            foreach (var entry in ChangeTracker.Entries<Tag>()
                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified))
            {
                var tag = entry.Entity;
                var existingTag = Tags.AsNoTracking()
                    .FirstOrDefault(t => t.TagId != tag.TagId && t.Slug == tag.Slug);
                if (existingTag != null)
                {
                    errors.Add(string.Format("The Slug on tag '{0}' must be unique and is already being used.", tag.Name));
                }
            }

            // Run IValidatableObject validation
            foreach (var entry in ChangeTracker.Entries()
                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified))
            {
                if (entry.Entity is IValidatableObject validatable)
                {
                    var context = new ValidationContext(entry.Entity);
                    var results = new List<ValidationResult>();
                    if (!Validator.TryValidateObject(entry.Entity, context, results, true))
                    {
                        errors.AddRange(results.Select(r => r.ErrorMessage));
                    }
                }
            }

            return errors;
        }
    }
}
