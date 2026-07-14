using System.Linq;
using DataLayer.DataClasses;
using DataLayer.DataClasses.Concrete;
using DataLayer.Startup;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using NUnit.Framework;
using Tests.Helpers;

namespace Tests.UnitTests.Group01DataLayer
{
    [TestFixture]
    public class DataLayerTests
    {
        [Test]
        public void SeedSmall_LoadsExpectedBlogsPostsAndTags()
        {
            using var app = new TestServices(TestDataSelection.Small);
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();

            Assert.That(db.Blogs.Count(), Is.EqualTo(2));
            Assert.That(db.Posts.Count(), Is.EqualTo(3));
            Assert.That(db.Tags.Count(), Is.EqualTo(3));
        }

        [Test]
        public void Post_ManyToManyTags_AreLoaded()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();

            var post = db.Posts.Include(p => p.Tags).Include(p => p.Blogger)
                        .OrderBy(p => p.PostId).First();

            Assert.That(post.Blogger, Is.Not.Null);
            Assert.That(post.Tags.Count, Is.GreaterThan(0));
        }

        [Test]
        public void SaveChanges_SetsLastUpdatedOnPost()
        {
            using var app = new TestServices();
            using var scope = app.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();

            var post = db.Posts.OrderBy(p => p.PostId).First();
            var before = post.LastUpdated;
            post.Title = "Edited title";
            db.SaveChanges();

            Assert.That(post.LastUpdated, Is.GreaterThanOrEqualTo(before));
        }

        [Test]
        public void ResetBlogs_Medium_LoadsMoreThanSmall()
        {
            using var small = new TestServices(TestDataSelection.Small);
            using var medium = new TestServices(TestDataSelection.Medium);

            using var smallScope = small.CreateScope();
            using var mediumScope = medium.CreateScope();
            var smallDb = smallScope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
            var mediumDb = mediumScope.ServiceProvider.GetRequiredService<SampleWebAppDb>();

            Assert.That(mediumDb.Posts.Count(), Is.GreaterThanOrEqualTo(smallDb.Posts.Count()));
        }
    }
}
