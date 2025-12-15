using Microsoft.EntityFrameworkCore;
using SampleWebApp.Api.Data;
using SampleWebApp.Core.Entities;

namespace SampleWebApp.Api.Tests.Fixtures
{
    public class TestDbContextFixture : IDisposable
    {
        public SampleWebAppDbContext Context { get; private set; }

        public TestDbContextFixture()
        {
            var options = new DbContextOptionsBuilder<SampleWebAppDbContext>()
                .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
                .Options;

            Context = new SampleWebAppDbContext(options);
            SeedData();
        }

        private void SeedData()
        {
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

            var tag3 = new Tag
            {
                TagId = 3,
                Name = "Science",
                Slug = "science"
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
                Tags = new List<Tag> { tag1, tag2 }
            };

            var post3 = new Post
            {
                PostId = 3,
                Title = "Physics Fundamentals",
                Content = "Understanding the basics of physics.",
                BlogId = 2,
                Blogger = blog2,
                LastUpdated = DateTime.UtcNow.AddDays(-2),
                Tags = new List<Tag> { tag3 }
            };

            blog1.Posts = new List<Post> { post1, post2 };
            blog2.Posts = new List<Post> { post3 };

            tag1.Posts = new List<Post> { post1, post2 };
            tag2.Posts = new List<Post> { post1, post2 };
            tag3.Posts = new List<Post> { post3 };

            Context.Blogs.AddRange(blog1, blog2);
            Context.Tags.AddRange(tag1, tag2, tag3);
            Context.Posts.AddRange(post1, post2, post3);
            Context.SaveChanges();
        }

        public void Dispose()
        {
            Context.Dispose();
        }
    }
}
