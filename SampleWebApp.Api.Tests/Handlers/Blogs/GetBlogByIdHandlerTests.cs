using FluentAssertions;
using SampleWebApp.Api.Tests.Fixtures;
using SampleWebApp.Core.Handlers.Blogs.Queries;
using Xunit;

namespace SampleWebApp.Api.Tests.Handlers.Blogs
{
    public class GetBlogByIdHandlerTests : IDisposable
    {
        private readonly TestDbContextFixture _dbFixture;
        private readonly AutoMapperFixture _mapperFixture;
        private readonly GetBlogByIdHandler _handler;

        public GetBlogByIdHandlerTests()
        {
            _dbFixture = new TestDbContextFixture();
            _mapperFixture = new AutoMapperFixture();
            _handler = new GetBlogByIdHandler(_dbFixture.Context, _mapperFixture.Mapper);
        }

        [Fact]
        public async Task Handle_WithValidId_ShouldReturnBlog()
        {
            var query = new GetBlogByIdQuery { BlogId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.BlogId.Should().Be(1);
            result.Name.Should().Be("Tech Blog");
        }

        [Fact]
        public async Task Handle_WithInvalidId_ShouldReturnNull()
        {
            var query = new GetBlogByIdQuery { BlogId = 999 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().BeNull();
        }

        [Fact]
        public async Task Handle_ShouldIncludePostsCount()
        {
            var query = new GetBlogByIdQuery { BlogId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.PostsCount.Should().Be(2);
        }

        [Fact]
        public async Task Handle_ShouldMapAllProperties()
        {
            var query = new GetBlogByIdQuery { BlogId = 2 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.BlogId.Should().Be(2);
            result.Name.Should().Be("Science Blog");
            result.EmailAddress.Should().Be("science@example.com");
            result.PostsCount.Should().Be(1);
        }

        public void Dispose()
        {
            _dbFixture.Dispose();
        }
    }
}
