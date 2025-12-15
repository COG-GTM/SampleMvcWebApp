#region licence
// The MIT License (MIT)
// 
// Filename: SampleWebAppDbCore.cs
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
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using DataLayer.Core.DataClasses.Concrete;
using DataLayer.Core.DataClasses.Concrete.Helpers;
using Microsoft.EntityFrameworkCore;

namespace DataLayer.Core.DataClasses
{
    public class SampleWebAppDbCore : DbContext
    {
        public const string NameOfConnectionString = "SampleWebAppDb";

        private static readonly string DefaultConnectionString = 
            @"Data Source=(localdb)\mssqllocaldb;Initial Catalog=SampleWebAppDb;MultipleActiveResultSets=True;Integrated Security=SSPI;Trusted_Connection=True";

        private readonly string _connectionString;

        public DbSet<Blog> Blogs { get; set; }
        public DbSet<Post> Posts { get; set; }
        public DbSet<Tag> Tags { get; set; }

        public SampleWebAppDbCore() : base()
        {
            _connectionString = DefaultConnectionString;
        }

        public SampleWebAppDbCore(DbContextOptions<SampleWebAppDbCore> options) : base(options)
        {
        }

        public SampleWebAppDbCore(string connectionString) : base()
        {
            _connectionString = connectionString;
        }

        protected override void OnConfiguring(DbContextOptionsBuilder optionsBuilder)
        {
            if (!optionsBuilder.IsConfigured)
            {
                optionsBuilder.UseSqlServer(_connectionString ?? DefaultConnectionString);
            }
        }

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            modelBuilder.Entity<Tag>()
                .HasIndex(t => t.Slug)
                .IsUnique();

            modelBuilder.Entity<Post>()
                .HasOne(p => p.Blogger)
                .WithMany(b => b.Posts)
                .HasForeignKey(p => p.BlogId);

            modelBuilder.Entity<Post>()
                .HasMany(p => p.Tags)
                .WithMany(t => t.Posts);
        }

        public override int SaveChanges()
        {
            HandleChangeTracking();
            return base.SaveChanges();
        }

        public override int SaveChanges(bool acceptAllChangesOnSuccess)
        {
            HandleChangeTracking();
            return base.SaveChanges(acceptAllChangesOnSuccess);
        }

        public override Task<int> SaveChangesAsync(CancellationToken cancellationToken = default)
        {
            HandleChangeTracking();
            return base.SaveChangesAsync(cancellationToken);
        }

        public override Task<int> SaveChangesAsync(bool acceptAllChangesOnSuccess, CancellationToken cancellationToken = default)
        {
            HandleChangeTracking();
            return base.SaveChangesAsync(acceptAllChangesOnSuccess, cancellationToken);
        }

        private void HandleChangeTracking()
        {
            foreach (var entity in ChangeTracker.Entries()
                                                .Where(e => e.State == EntityState.Added || e.State == EntityState.Modified))
            {
                var trackUpdateClass = entity.Entity as TrackUpdate;
                if (trackUpdateClass == null) continue;
                trackUpdateClass.UpdateTrackingInfo();
            }
        }
    }
}
