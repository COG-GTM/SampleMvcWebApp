using FluentAssertions;
using SampleWebApp.Api.Tests.Fixtures;
using SampleWebApp.Core.Handlers.Posts.Queries;
using Xunit;

namespace SampleWebApp.Api.Tests.Handlers.Posts
{
    public class GetPostByIdHandlerTests : IDisposable
    {
        private readonly TestDbContextFixture _dbFixture;
        private readonly AutoMapperFixture _mapperFixture;
        private readonly GetPostByIdHandler _handler;

        public GetPostByIdHandlerTests()
        {
            _dbFixture = new TestDbContextFixture();
            _mapperFixture = new AutoMapperFixture();
            _handler = new GetPostByIdHandler(_dbFixture.Context, _mapperFixture.Mapper);
        }

        [Fact]
        public async Task Handle_WithValidId_ShouldReturnPost()
        {
            var query = new GetPostByIdQuery { PostId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.PostId.Should().Be(1);
            result.Title.Should().Be("Introduction to C#");
        }

        [Fact]
        public async Task Handle_WithInvalidId_ShouldReturnNull()
        {
            var query = new GetPostByIdQuery { PostId = 999 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().BeNull();
        }

        [Fact]
        public async Task Handle_ShouldIncludeBloggerName()
        {
            var query = new GetPostByIdQuery { PostId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.BloggerName.Should().Be("Tech Blog");
        }

        [Fact]
        public async Task Handle_ShouldIncludeTags()
        {
            var query = new GetPostByIdQuery { PostId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.Tags.Should().HaveCount(2);
            result.Tags.Select(t => t.Name).Should().Contain("Programming");
            result.Tags.Select(t => t.Name).Should().Contain("Web Development");
        }

        [Fact]
        public async Task Handle_ShouldMapAllProperties()
        {
            var query = new GetPostByIdQuery { PostId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.PostId.Should().Be(1);
            result.Title.Should().Be("Introduction to C#");
            result.Content.Should().Be("This is a post about C# programming.");
            result.BloggerName.Should().Be("Tech Blog");
            result.BlogId.Should().Be(1);
            result.LastUpdated.Should().NotBe(default);
        }

        [Fact]
        public async Task Handle_ShouldReturnDetailedTagInfo()
        {
            var query = new GetPostByIdQuery { PostId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            var programmingTag = result!.Tags.First(t => t.Name == "Programming");
            programmingTag.TagId.Should().Be(1);
            programmingTag.Slug.Should().Be("programming");
        }

        [Fact]
        public async Task Handle_WithDifferentPost_ShouldReturnCorrectData()
        {
            var query = new GetPostByIdQuery { PostId = 3 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.PostId.Should().Be(3);
            result.Title.Should().Be("Physics Fundamentals");
            result.BloggerName.Should().Be("Science Blog");
            result.Tags.Should().HaveCount(1);
            result.Tags.First().Name.Should().Be("Science");
        }

        public void Dispose()
        {
            _dbFixture.Dispose();
        }
    }
}
