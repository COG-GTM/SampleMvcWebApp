using Microsoft.EntityFrameworkCore;
using SampleWebApp.Core.Entities;

namespace SampleWebApp.Api.Data
{
    public class SampleWebAppDbContext : DbContext
    {
        public SampleWebAppDbContext(DbContextOptions<SampleWebAppDbContext> options)
            : base(options)
        {
        }

        public DbSet<Blog> Blogs { get; set; } = null!;
        public DbSet<Post> Posts { get; set; } = null!;
        public DbSet<Tag> Tags { get; set; } = null!;

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            base.OnModelCreating(modelBuilder);

            modelBuilder.Entity<Blog>(entity =>
            {
                entity.HasKey(e => e.BlogId);
                entity.Property(e => e.Name).IsRequired().HasMaxLength(64);
                entity.Property(e => e.EmailAddress).IsRequired().HasMaxLength(256);
            });

            modelBuilder.Entity<Post>(entity =>
            {
                entity.HasKey(e => e.PostId);
                entity.Property(e => e.Title).IsRequired().HasMaxLength(128);
                entity.Property(e => e.Content).IsRequired();

                entity.HasOne(p => p.Blogger)
                    .WithMany(b => b.Posts)
                    .HasForeignKey(p => p.BlogId);

                entity.HasMany(p => p.Tags)
                    .WithMany(t => t.Posts);
            });

            modelBuilder.Entity<Tag>(entity =>
            {
                entity.HasKey(e => e.TagId);
                entity.Property(e => e.Slug).IsRequired().HasMaxLength(64);
                entity.Property(e => e.Name).IsRequired().HasMaxLength(128);
            });
        }
    }
}
