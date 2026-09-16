using System;
using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using System.Threading;
using DataLayer.DataClasses.Concrete;
using Xunit;

namespace DataLayer.Domain.Tests
{
    /// <summary>
    /// Pins the data annotations and ToString() output the MVC5 views and EF6 model rely on, so
    /// the ported entities cannot drift from the .NET Framework originals unnoticed.
    /// </summary>
    public class EntityMetadataTests
    {
        private static bool TryValidate(object entity, out IList<ValidationResult> results)
        {
            var list = new List<ValidationResult>();
            var isValid = Validator.TryValidateObject(entity, new ValidationContext(entity), list, true);
            results = list;
            return isValid;
        }

        [Fact]
        public void BlogRequiresNameAndValidEmailAddress()
        {
            IList<ValidationResult> results;

            Assert.True(TryValidate(new Blog { Name = "Jon", EmailAddress = "jon@example.com" }, out results));

            Assert.False(TryValidate(new Blog { Name = "J", EmailAddress = "jon@example.com" }, out results));
            Assert.Contains(results, x => x.MemberNames.Contains("Name"));

            Assert.False(TryValidate(new Blog { Name = "Jon", EmailAddress = "not-an-email" }, out results));
            Assert.Contains(results, x => x.MemberNames.Contains("EmailAddress"));
        }

        [Fact]
        public void TagSlugRejectsSpacesAndNonAlphanumericCharacters()
        {
            IList<ValidationResult> results;

            Assert.True(TryValidate(new Tag { Name = "Good tag", Slug = "good_tag" }, out results));

            Assert.False(TryValidate(new Tag { Name = "Bad tag", Slug = "bad tag" }, out results));
            var error = Assert.Single(results);
            Assert.Equal("The slug must not contain spaces or non-alphanumeric characters.", error.ErrorMessage);
        }

        [Fact]
        public void ToStringFormatsAreUnchanged()
        {
            var blog = new Blog { BlogId = 1, Name = "Jon", EmailAddress = "jon@example.com" };
            Assert.Equal("BlogId: 1, Name: Jon, EmailAddress: jon@example.com, NumPosts: null", blog.ToString());

            var tag = new Tag { TagId = 2, Name = "Good tag", Slug = "good-tag" };
            Assert.Equal("TagId: 2, Name: Good tag, Slug: good-tag", tag.ToString());

            var post = new Post { PostId = 3, Title = "A post", BlogId = 1, Blogger = blog, Tags = new List<Tag> { tag } };
            Assert.Equal("PostId: 3, Title: A post, BlogId: 1, Blogger: Jon, AllocatedTags: 1", post.ToString());
        }

        [Fact]
        public void TrackUpdateStampsLastUpdatedOnConstructionAndOnUpdate()
        {
            var before = DateTime.UtcNow;
            var post = new Post { Title = "A post", Content = "Content" };

            Assert.InRange(post.LastUpdated, before.AddSeconds(-1), DateTime.UtcNow.AddSeconds(1));

            var firstStamp = post.LastUpdated;
            Thread.Sleep(20);
            post.UpdateTrackingInfo();

            Assert.True(post.LastUpdated > firstStamp);
        }

        [Fact]
        public void LastUpdatedSetterIsNotPubliclyWritable()
        {
            var setter = typeof(Post).GetProperty("LastUpdated").GetSetMethod(true);

            Assert.NotNull(setter);
            Assert.True(setter.IsFamily, "LastUpdated must stay protected so only the change tracker writes it.");
        }
    }
}
