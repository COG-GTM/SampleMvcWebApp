using System.Collections.Generic;
using System.ComponentModel.DataAnnotations;
using System.Linq;
using DataLayer.DataClasses.Concrete;
using Xunit;

namespace DataLayer.Domain.Tests
{
    /// <summary>
    /// Mirrors the IValidatableObject expectations in
    /// Tests/UnitTests/Group01DataLayer/Test13Validation.cs. The legacy tests reach the same
    /// rules through EF6's SaveChanges validation against SQL Server; here the rules are
    /// exercised directly, so the assertions are on the identical error strings.
    /// </summary>
    public class PostValidationTests
    {
        private static Post NewPost(string title = "Test post", string content = "Nothing special")
        {
            return new Post
            {
                Blogger = new Blog { Name = "Test blogger", EmailAddress = "test@example.com" },
                Title = title,
                Content = content,
                Tags = new List<Tag> { new Tag { Name = "Test tag", Slug = "test-tag" } }
            };
        }

        private static IList<ValidationResult> Validate(Post post)
        {
            return post.Validate(new ValidationContext(post)).ToList();
        }

        [Fact]
        public void ValidPostProducesNoErrors()
        {
            Assert.Empty(Validate(NewPost()));
        }

        [Fact]
        public void TitleWithExclamationMarkIsRejected()
        {
            var errors = Validate(NewPost(title: "Test post!"));

            var error = Assert.Single(errors);
            Assert.Equal("Sorry, but you can't get too excited and include a ! in the title.", error.ErrorMessage);
            Assert.Equal(new[] { "Title" }, error.MemberNames);
        }

        [Fact]
        public void TitleEndingInQuestionMarkIsRejected()
        {
            var errors = Validate(NewPost(title: "Test post?"));

            var error = Assert.Single(errors);
            Assert.Equal("Sorry, but you can't ask a question, i.e. the title can't end with '?'.", error.ErrorMessage);
        }

        [Theory]
        [InlineData(" sheep.", "Sorry. Not allowed to end a sentance with 'sheep'.")]
        [InlineData(" lamb.", "Sorry. Not allowed to end a sentance with 'lamb'.")]
        [InlineData(" cow.", "Sorry. Not allowed to end a sentance with 'cow'.")]
        [InlineData(" calf.", "Sorry. Not allowed to end a sentance with 'calf'.")]
        public void BannedContentEndingsAreRejected(string ending, string expectedMessage)
        {
            var errors = Validate(NewPost(content: "Should not end sentence with" + ending));

            var error = Assert.Single(errors);
            Assert.Equal(expectedMessage, error.ErrorMessage);
            Assert.Empty(error.MemberNames);
        }

        [Fact]
        public void ContentWithTwoBannedEndingsProducesTwoErrors()
        {
            var errors = Validate(NewPost(content: "One sheep. Then a lamb."));

            Assert.Equal(2, errors.Count);
        }

        [Fact]
        public void PostWithEmptyTagCollectionIsRejected()
        {
            var post = NewPost();
            post.Tags = new List<Tag>();

            var error = Assert.Single(Validate(post));
            Assert.Equal("The post must have at least one Tag.", error.ErrorMessage);
            Assert.Equal(new[] { "AllocatedTags" }, error.MemberNames);
        }

        [Fact]
        public void PostWithNullTagCollectionIsNotChecked()
        {
            var post = NewPost();
            post.Tags = null;

            Assert.Empty(Validate(post));
        }

        [Fact]
        public void DataAnnotationsStillEnforceTitleLengthAndRequiredFields()
        {
            var post = NewPost(title: "x");
            var results = new List<ValidationResult>();

            var isValid = Validator.TryValidateObject(post, new ValidationContext(post), results, true);

            Assert.False(isValid);
            Assert.Contains(results, x => x.MemberNames.Contains("Title"));
        }
    }
}
