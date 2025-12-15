using FluentAssertions;
using SampleWebApp.Api.Tests.Fixtures;
using SampleWebApp.Core.Handlers.Blogs.Queries;
using Xunit;

namespace SampleWebApp.Api.Tests.Handlers.Blogs
{
    public class GetBlogsHandlerTests : IDisposable
    {
        private readonly TestDbContextFixture _dbFixture;
        private readonly AutoMapperFixture _mapperFixture;
        private readonly GetBlogsHandler _handler;

        public GetBlogsHandlerTests()
        {
            _dbFixture = new TestDbContextFixture();
            _mapperFixture = new AutoMapperFixture();
            _handler = new GetBlogsHandler(_dbFixture.Context, _mapperFixture.Mapper);
        }

        [Fact]
        public async Task Handle_ShouldReturnAllBlogs()
        {
            var query = new GetBlogsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result.Should().HaveCount(2);
        }

        [Fact]
        public async Task Handle_ShouldReturnBlogsOrderedByNameDescending()
        {
            var query = new GetBlogsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var blogList = result.ToList();

            blogList[0].Name.Should().Be("Tech Blog");
            blogList[1].Name.Should().Be("Science Blog");
        }

        [Fact]
        public async Task Handle_ShouldIncludePostsCount()
        {
            var query = new GetBlogsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var techBlog = result.First(b => b.Name == "Tech Blog");
            var scienceBlog = result.First(b => b.Name == "Science Blog");

            techBlog.PostsCount.Should().Be(2);
            scienceBlog.PostsCount.Should().Be(1);
        }

        [Fact]
        public async Task Handle_ShouldMapAllProperties()
        {
            var query = new GetBlogsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var techBlog = result.First(b => b.Name == "Tech Blog");

            techBlog.BlogId.Should().Be(1);
            techBlog.Name.Should().Be("Tech Blog");
            techBlog.EmailAddress.Should().Be("tech@example.com");
        }

        public void Dispose()
        {
            _dbFixture.Dispose();
        }
    }
}
