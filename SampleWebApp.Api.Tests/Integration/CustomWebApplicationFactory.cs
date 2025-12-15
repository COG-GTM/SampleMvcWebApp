using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using SampleWebApp.Api.Data;
using SampleWebApp.Core.Entities;

namespace SampleWebApp.Api.Tests.Integration
{
    public class CustomWebApplicationFactory : WebApplicationFactory<Program>
    {
        private readonly string _databaseName = $"IntegrationTestDb_{Guid.NewGuid()}";

        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.ConfigureServices(services =>
            {
                var descriptor = services.SingleOrDefault(
                    d => d.ServiceType == typeof(DbContextOptions<SampleWebAppDbContext>));

                if (descriptor != null)
                {
                    services.Remove(descriptor);
                }

                services.AddDbContext<SampleWebAppDbContext>(options =>
                {
                    options.UseInMemoryDatabase(_databaseName);
                });

                services.AddScoped<DbContext>(provider => 
                    provider.GetRequiredService<SampleWebAppDbContext>());

                var sp = services.BuildServiceProvider();

                using (var scope = sp.CreateScope())
                {
                    var scopedServices = scope.ServiceProvider;
                    var db = scopedServices.GetRequiredService<SampleWebAppDbContext>();

                    db.Database.EnsureCreated();
                    SeedTestData(db);
                }
            });
        }

        private static void SeedTestData(SampleWebAppDbContext context)
        {
            if (context.Blogs.Any())
            {
                return;
            }

            var blog1 = new Blog
            {
                BlogId = 1,
                Name = "Tech Blog",
                EmailAddress = "tech@example.com"
            };

            var blog2 = new Blog
            {
                BlogId = 2,
                Name = "Science Blog",
                EmailAddress = "science@example.com"
            };

            var tag1 = new Tag
            {
                TagId = 1,
                Name = "Programming",
                Slug = "programming"
            };

            var tag2 = new Tag
            {
                TagId = 2,
                Name = "Web Development",
                Slug = "web-development"
            };

            var post1 = new Post
            {
                PostId = 1,
                Title = "Introduction to C#",
                Content = "This is a post about C# programming.",
                BlogId = 1,
                Blogger = blog1,
                LastUpdated = DateTime.UtcNow.AddDays(-1),
                Tags = new List<Tag> { tag1, tag2 }
            };

            var post2 = new Post
            {
                PostId = 2,
                Title = "ASP.NET Core Basics",
                Content = "Learn the basics of ASP.NET Core.",
                BlogId = 1,
                Blogger = blog1,
                LastUpdated = DateTime.UtcNow,
                Tags = new List<Tag> { tag1 }
            };

            blog1.Posts = new List<Post> { post1, post2 };
            tag1.Posts = new List<Post> { post1, post2 };
            tag2.Posts = new List<Post> { post1 };

            context.Blogs.AddRange(blog1, blog2);
            context.Tags.AddRange(tag1, tag2);
            context.Posts.AddRange(post1, post2);
            context.SaveChanges();
        }
    }
}
