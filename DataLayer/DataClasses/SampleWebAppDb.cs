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
using System.Linq;
using System.Runtime.CompilerServices;
using System.Threading;
using System.Threading.Tasks;
using DataLayer.DataClasses.Concrete;
using DataLayer.DataClasses.Concrete.Helpers;
using Microsoft.EntityFrameworkCore;

[assembly: InternalsVisibleTo("Tests")]

namespace DataLayer.DataClasses
{

    public class SampleWebAppDb : DbContext
    {
        public DbSet<Blog> Blogs { get; set; }
        public DbSet<Post> Posts { get; set; }
        public DbSet<Tag> Tags { get; set; }

        public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options) {}

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            modelBuilder.Entity<Tag>()
                .HasIndex(x => x.Slug)
                .IsUnique();

            modelBuilder.Entity<Post>()
                .HasOne(x => x.Blogger)
                .WithMany(x => x.Posts)
                .HasForeignKey(x => x.BlogId);

            modelBuilder.Entity<Post>()
                .HasMany(x => x.Tags)
                .WithMany(x => x.Posts);
        }

        /// <summary>
        /// This has been overridden to handle:
        /// a) Updating of modified items (see p194 in DbContext book)
        /// b) Checking that a Tag's Slug is unique
        /// </summary>
        public override int SaveChanges(bool acceptAllChangesOnSuccess)
        {
            var tagsToCheck = HandleChangeTracking();
            foreach (var tag in tagsToCheck)
            {
                if (Tags.Any(x => x.TagId != tag.TagId && x.Slug == tag.Slug))
                    throw new SlugNotUniqueException(tag);
            }
            return base.SaveChanges(acceptAllChangesOnSuccess);
        }

        /// <summary>
        /// Same for async
        /// </summary>
        public override async Task<int> SaveChangesAsync(bool acceptAllChangesOnSuccess,
            CancellationToken cancellationToken = default)
        {
            var tagsToCheck = HandleChangeTracking();
            foreach (var tag in tagsToCheck)
            {
                if (await Tags.AnyAsync(x => x.TagId != tag.TagId && x.Slug == tag.Slug, cancellationToken))
                    throw new SlugNotUniqueException(tag);
            }
            return await base.SaveChangesAsync(acceptAllChangesOnSuccess, cancellationToken);
        }


        //--------------------------------------------------
        //private helpers

        /// <summary>
        /// This handles going through all the entities that have changed and seeing if they need any special handling.
        /// It returns the added/modified tags whose Slug must be checked for uniqueness.
        /// </summary>
        private List<Tag> HandleChangeTracking()
        {
            ChangeTracker.DetectChanges();

            var tagsToCheck = new List<Tag>();
            var slugsSeen = new HashSet<string>();
            foreach (var entity in ChangeTracker.Entries()
                                                .Where(
                                                    e =>
                                                    e.State == EntityState.Added || e.State == EntityState.Modified))
            {
                if (entity.Entity is TrackUpdate trackUpdateClass)
                {
                    trackUpdateClass.UpdateTrackingInfo();
                    entity.Property(nameof(TrackUpdate.LastUpdated)).IsModified = entity.State == EntityState.Modified;
                }

                if (entity.Entity is Tag tag)
                {
                    if (!slugsSeen.Add(tag.Slug))
                        throw new SlugNotUniqueException(tag);
                    tagsToCheck.Add(tag);
                }
            }
            return tagsToCheck;
        }

    }
}
