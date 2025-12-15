using DataLayer.Core.DataClasses;
using DataLayer.Core.DataClasses.Concrete;
using Microsoft.EntityFrameworkCore;
using System.ComponentModel.DataAnnotations;

namespace Tests.Core;

public class DataLayerCoreTests
{
    private static DbContextOptions<SampleWebAppDbCore> GetInMemoryOptions(string dbName)
    {
        return new DbContextOptionsBuilder<SampleWebAppDbCore>()
            .UseInMemoryDatabase(databaseName: dbName)
            .Options;
    }

    [Fact]
    public void SampleWebAppDbCore_CanBeCreated()
    {
        var options = GetInMemoryOptions("TestDb_CanBeCreated");
        using var context = new SampleWebAppDbCore(options);
        Assert.NotNull(context);
    }

    [Fact]
    public void SampleWebAppDbCore_HasBlogsDbSet()
    {
        var options = GetInMemoryOptions("TestDb_HasBlogsDbSet");
        using var context = new SampleWebAppDbCore(options);
        Assert.NotNull(context.Blogs);
    }

    [Fact]
    public void SampleWebAppDbCore_HasPostsDbSet()
    {
        var options = GetInMemoryOptions("TestDb_HasPostsDbSet");
        using var context = new SampleWebAppDbCore(options);
        Assert.NotNull(context.Posts);
    }

    [Fact]
    public void SampleWebAppDbCore_HasTagsDbSet()
    {
        var options = GetInMemoryOptions("TestDb_HasTagsDbSet");
        using var context = new SampleWebAppDbCore(options);
        Assert.NotNull(context.Tags);
    }

    [Fact]
    public void Blog_CanBeAddedAndRetrieved()
    {
        var options = GetInMemoryOptions("TestDb_BlogCanBeAdded");
        using (var context = new SampleWebAppDbCore(options))
        {
            var blog = new Blog
            {
                Name = "Test Blog",
                EmailAddress = "test@example.com"
            };
            context.Blogs.Add(blog);
            context.SaveChanges();
        }

        using (var context = new SampleWebAppDbCore(options))
        {
            var blog = context.Blogs.FirstOrDefault();
            Assert.NotNull(blog);
            Assert.Equal("Test Blog", blog.Name);
            Assert.Equal("test@example.com", blog.EmailAddress);
        }
    }

    [Fact]
    public void Tag_CanBeAddedAndRetrieved()
    {
        var options = GetInMemoryOptions("TestDb_TagCanBeAdded");
        using (var context = new SampleWebAppDbCore(options))
        {
            var tag = new Tag
            {
                Name = "Test Tag",
                Slug = "test-tag"
            };
            context.Tags.Add(tag);
            context.SaveChanges();
        }

        using (var context = new SampleWebAppDbCore(options))
        {
            var tag = context.Tags.FirstOrDefault();
            Assert.NotNull(tag);
            Assert.Equal("Test Tag", tag.Name);
            Assert.Equal("test-tag", tag.Slug);
        }
    }

    [Fact]
    public void Post_CanBeAddedWithBlogRelationship()
    {
        var options = GetInMemoryOptions("TestDb_PostWithBlog");
        using (var context = new SampleWebAppDbCore(options))
        {
            var blog = new Blog
            {
                Name = "Test Blog",
                EmailAddress = "test@example.com"
            };
            context.Blogs.Add(blog);
            context.SaveChanges();

            var tag = new Tag
            {
                Name = "Test Tag",
                Slug = "testtag"
            };
            context.Tags.Add(tag);
            context.SaveChanges();

            var post = new Post
            {
                Title = "Test Post",
                Content = "Test Content",
                BlogId = blog.BlogId,
                Tags = new List<Tag> { tag }
            };
            context.Posts.Add(post);
            context.SaveChanges();
        }

        using (var context = new SampleWebAppDbCore(options))
        {
            var post = context.Posts.Include(p => p.Blogger).FirstOrDefault();
            Assert.NotNull(post);
            Assert.Equal("Test Post", post.Title);
            Assert.NotNull(post.Blogger);
            Assert.Equal("Test Blog", post.Blogger.Name);
        }
    }

    [Fact]
    public void Post_LastUpdatedIsSetOnCreation()
    {
        var options = GetInMemoryOptions("TestDb_PostLastUpdated");
        using var context = new SampleWebAppDbCore(options);
        
        var blog = new Blog
        {
            Name = "Test Blog",
            EmailAddress = "test@example.com"
        };
        context.Blogs.Add(blog);
        context.SaveChanges();

        var tag = new Tag
        {
            Name = "Test Tag",
            Slug = "testtag"
        };
        context.Tags.Add(tag);
        context.SaveChanges();

        var beforeCreate = DateTime.UtcNow;
        var post = new Post
        {
            Title = "Test Post",
            Content = "Test Content",
            BlogId = blog.BlogId,
            Tags = new List<Tag> { tag }
        };
        context.Posts.Add(post);
        context.SaveChanges();
        var afterCreate = DateTime.UtcNow;

        Assert.True(post.LastUpdated >= beforeCreate.AddSeconds(-1));
        Assert.True(post.LastUpdated <= afterCreate.AddSeconds(1));
    }

    [Fact]
    public void Post_ValidationFailsWithExclamationInTitle()
    {
        var post = new Post
        {
            Title = "Test Post!",
            Content = "Test Content",
            Tags = new List<Tag> { new Tag { Name = "Tag", Slug = "tag" } }
        };

        var validationContext = new ValidationContext(post);
        var validationResults = new List<ValidationResult>();
        var isValid = Validator.TryValidateObject(post, validationContext, validationResults, true);

        var titleError = validationResults.FirstOrDefault(r => r.MemberNames.Contains("Title"));
        Assert.NotNull(titleError);
        Assert.Contains("!", titleError.ErrorMessage);
    }

    [Fact]
    public void Post_ValidationFailsWithQuestionMarkAtEnd()
    {
        var post = new Post
        {
            Title = "Test Post?",
            Content = "Test Content",
            Tags = new List<Tag> { new Tag { Name = "Tag", Slug = "tag" } }
        };

        var validationContext = new ValidationContext(post);
        var validationResults = new List<ValidationResult>();
        Validator.TryValidateObject(post, validationContext, validationResults, true);

        var titleError = validationResults.FirstOrDefault(r => r.MemberNames.Contains("Title"));
        Assert.NotNull(titleError);
        Assert.Contains("?", titleError.ErrorMessage);
    }

    [Fact]
    public void Post_ValidationFailsWithNoTags()
    {
        var post = new Post
        {
            Title = "Test Post",
            Content = "Test Content",
            Tags = new List<Tag>()
        };

        var validationContext = new ValidationContext(post);
        var validationResults = new List<ValidationResult>();
        Validator.TryValidateObject(post, validationContext, validationResults, true);

        var tagError = validationResults.FirstOrDefault(r => r.MemberNames.Contains("AllocatedTags"));
        Assert.NotNull(tagError);
        Assert.Contains("at least one Tag", tagError.ErrorMessage);
    }

    [Fact]
    public void Tag_SlugValidationFailsWithSpaces()
    {
        var tag = new Tag
        {
            Name = "Test Tag",
            Slug = "test tag"
        };

        var validationContext = new ValidationContext(tag);
        var validationResults = new List<ValidationResult>();
        var isValid = Validator.TryValidateObject(tag, validationContext, validationResults, true);

        Assert.False(isValid);
        var slugError = validationResults.FirstOrDefault(r => r.MemberNames.Contains("Slug"));
        Assert.NotNull(slugError);
    }

    [Fact]
    public void Blog_NameRequiredValidation()
    {
        var blog = new Blog
        {
            Name = null!,
            EmailAddress = "test@example.com"
        };

        var validationContext = new ValidationContext(blog);
        var validationResults = new List<ValidationResult>();
        var isValid = Validator.TryValidateObject(blog, validationContext, validationResults, true);

        Assert.False(isValid);
        var nameError = validationResults.FirstOrDefault(r => r.MemberNames.Contains("Name"));
        Assert.NotNull(nameError);
    }

    [Fact]
    public void Blog_EmailValidation()
    {
        var blog = new Blog
        {
            Name = "Test Blog",
            EmailAddress = "invalid-email"
        };

        var validationContext = new ValidationContext(blog);
        var validationResults = new List<ValidationResult>();
        var isValid = Validator.TryValidateObject(blog, validationContext, validationResults, true);

        Assert.False(isValid);
        var emailError = validationResults.FirstOrDefault(r => r.MemberNames.Contains("EmailAddress"));
        Assert.NotNull(emailError);
    }

    [Fact]
    public void SampleWebAppDbCore_ConnectionStringConstantIsCorrect()
    {
        Assert.Equal("SampleWebAppDb", SampleWebAppDbCore.NameOfConnectionString);
    }
}
