using System;
using System.Linq;
using DataLayer.Startup;
using DataLayer.Startup.Internal;
using Xunit;

namespace Tests
{
    public class Group01DataLayerTests
    {
        [Fact]
        public void XmlFileLoadsSimpleDataset()
        {
            //ATTEMPT
            var bloggers = LoadDbDataFromXml
                .FormBlogsWithPosts("DataLayer.Startup.Internal.BlogsContentSimple.xml")
                .ToList();

            //VERIFY
            Assert.Equal(2, bloggers.Count);
            Assert.Equal(3, bloggers.SelectMany(x => x.Posts).Count());
            Assert.Equal(3, bloggers.SelectMany(x => x.Posts.SelectMany(y => y.Tags)).Distinct().Count());
        }

        [Fact]
        public void XmlFileLoadMissingFileThrows()
        {
            Assert.Throws<NullReferenceException>(
                () => LoadDbDataFromXml.FormBlogsWithPosts("badname.xml"));
        }

        [Fact]
        public void ResetBlogsSmallSeedsExpectedCounts()
        {
            using var db = TestHelpers.CreateInMemoryDb();

            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Small);

            Assert.Equal(2, db.Blogs.Count());
            Assert.Equal(3, db.Posts.Count());
            Assert.Equal(3, db.Tags.Count());
        }

        [Fact]
        public void ResetBlogsMediumSeedsExpectedCounts()
        {
            using var db = TestHelpers.CreateInMemoryDb();

            DataLayerInitialise.ResetBlogs(db, TestDataSelection.Medium);

            Assert.Equal(4, db.Blogs.Count());
            Assert.Equal(17, db.Posts.Count());
            Assert.Equal(8, db.Tags.Count());
        }
    }
}
