using System;
using System.Linq;
using DataLayer.Startup.Internal;
using Xunit;

namespace DataLayer.Domain.Tests
{
    /// <summary>
    /// Mirrors Tests/UnitTests/Group01DataLayer/Test10SetupBlogs.cs (NUnit, .NET Framework 4.5.1),
    /// minus the cases that need a SQL Server database. Both builds embed the same two XML files.
    /// </summary>
    public class SeedDataLoaderTests
    {
        private const string SimpleFile = "DataLayer.Startup.Internal.BlogsContentSimple.xml";
        private const string MediumFile = "DataLayer.Startup.Internal.BlogsContextMedium.xml";

        [Fact]
        public void SimpleFileLoadsTwoBloggersThreePostsThreeTags()
        {
            var bloggers = LoadDbDataFromXml.FormBlogsWithPosts(SimpleFile).ToList();

            Assert.Equal(2, bloggers.Count);
            Assert.Equal(3, bloggers.SelectMany(x => x.Posts).Count());
            Assert.Equal(3, bloggers.SelectMany(x => x.Posts.SelectMany(y => y.Tags)).Distinct().Count());
        }

        [Fact]
        public void MediumFileLoadsFourBloggersSeventeenPostsEightTags()
        {
            var bloggers = LoadDbDataFromXml.FormBlogsWithPosts(MediumFile).ToList();

            Assert.Equal(4, bloggers.Count);
            Assert.Equal(17, bloggers.SelectMany(x => x.Posts).Count());
            Assert.Equal(8, bloggers.SelectMany(x => x.Posts.SelectMany(y => y.Tags)).Distinct().Count());
        }

        [Fact]
        public void MissingFileThrowsNullReferenceWithSameMessage()
        {
            var ex = Assert.Throws<NullReferenceException>(() => LoadDbDataFromXml.FormBlogsWithPosts("badname.xml"));

            Assert.StartsWith("Could not find the xml file you asked for.", ex.Message);
        }

        [Fact]
        public void EveryPostIsLinkedBackToItsBloggerAndHasAtLeastOneTag()
        {
            var bloggers = LoadDbDataFromXml.FormBlogsWithPosts(MediumFile).ToList();

            foreach (var blogger in bloggers)
            {
                Assert.All(blogger.Posts, post =>
                {
                    Assert.Same(blogger, post.Blogger);
                    Assert.NotEmpty(post.Tags);
                });
            }
        }

        [Fact]
        public void TagsWithTheSameSlugAreSharedInstancesAcrossPosts()
        {
            var tags = LoadDbDataFromXml.FormBlogsWithPosts(MediumFile)
                .SelectMany(x => x.Posts)
                .SelectMany(x => x.Tags)
                .ToList();

            var distinctBySlug = tags.Select(x => x.Slug).Distinct().Count();
            var distinctByReference = tags.Distinct().Count();

            Assert.Equal(distinctBySlug, distinctByReference);
        }

        [Fact]
        public void PostContentLinesAreTrimmed()
        {
            var contents = LoadDbDataFromXml.FormBlogsWithPosts(SimpleFile)
                .SelectMany(x => x.Posts)
                .Select(x => x.Content)
                .ToList();

            Assert.NotEmpty(contents);
            foreach (var line in contents.SelectMany(x => x.Split('\n')))
            {
                Assert.Equal(line.Trim(), line);
            }
        }
    }
}
