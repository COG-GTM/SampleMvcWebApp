using FluentAssertions;
using SampleWebApp.Api.Tests.Fixtures;
using SampleWebApp.Core.Handlers.Posts.Queries;
using Xunit;

namespace SampleWebApp.Api.Tests.Handlers.Posts
{
    public class GetPostsHandlerTests : IDisposable
    {
        private readonly TestDbContextFixture _dbFixture;
        private readonly AutoMapperFixture _mapperFixture;
        private readonly GetPostsHandler _handler;

        public GetPostsHandlerTests()
        {
            _dbFixture = new TestDbContextFixture();
            _mapperFixture = new AutoMapperFixture();
            _handler = new GetPostsHandler(_dbFixture.Context, _mapperFixture.Mapper);
        }

        [Fact]
        public async Task Handle_WithoutBlogIdFilter_ShouldReturnAllPosts()
        {
            var query = new GetPostsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result.Should().HaveCount(3);
        }

        [Fact]
        public async Task Handle_WithBlogIdFilter_ShouldReturnFilteredPosts()
        {
            var query = new GetPostsQuery { BlogId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result.Should().HaveCount(2);
            result.All(p => p.BlogId == 1).Should().BeTrue();
        }

        [Fact]
        public async Task Handle_WithBlogIdFilter_ShouldReturnOnlyMatchingBlogPosts()
        {
            var query = new GetPostsQuery { BlogId = 2 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result.Should().HaveCount(1);
            result.First().Title.Should().Be("Physics Fundamentals");
        }

        [Fact]
        public async Task Handle_ShouldReturnPostsOrderedByLastUpdatedDescending()
        {
            var query = new GetPostsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var postList = result.ToList();

            postList[0].Title.Should().Be("ASP.NET Core Basics");
            postList[1].Title.Should().Be("Introduction to C#");
            postList[2].Title.Should().Be("Physics Fundamentals");
        }

        [Fact]
        public async Task Handle_ShouldIncludeBloggerName()
        {
            var query = new GetPostsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var techPost = result.First(p => p.Title == "Introduction to C#");

            techPost.BloggerName.Should().Be("Tech Blog");
        }

        [Fact]
        public async Task Handle_ShouldIncludeTagNames()
        {
            var query = new GetPostsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var techPost = result.First(p => p.Title == "Introduction to C#");

            techPost.TagNames.Should().HaveCount(2);
            techPost.TagNames.Should().Contain("Programming");
            techPost.TagNames.Should().Contain("Web Development");
        }

        [Fact]
        public async Task Handle_ShouldMapAllProperties()
        {
            var query = new GetPostsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var post = result.First(p => p.Title == "Introduction to C#");

            post.PostId.Should().Be(1);
            post.BlogId.Should().Be(1);
            post.Title.Should().Be("Introduction to C#");
            post.BloggerName.Should().Be("Tech Blog");
            post.LastUpdated.Should().NotBe(default);
        }

        [Fact]
        public async Task Handle_WithNonExistentBlogId_ShouldReturnEmptyList()
        {
            var query = new GetPostsQuery { BlogId = 999 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result.Should().BeEmpty();
        }

        public void Dispose()
        {
            _dbFixture.Dispose();
        }
    }
}
