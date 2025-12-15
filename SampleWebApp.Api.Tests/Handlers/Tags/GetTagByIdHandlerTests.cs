using FluentAssertions;
using SampleWebApp.Api.Tests.Fixtures;
using SampleWebApp.Core.Handlers.Tags.Queries;
using Xunit;

namespace SampleWebApp.Api.Tests.Handlers.Tags
{
    public class GetTagByIdHandlerTests : IDisposable
    {
        private readonly TestDbContextFixture _dbFixture;
        private readonly AutoMapperFixture _mapperFixture;
        private readonly GetTagByIdHandler _handler;

        public GetTagByIdHandlerTests()
        {
            _dbFixture = new TestDbContextFixture();
            _mapperFixture = new AutoMapperFixture();
            _handler = new GetTagByIdHandler(_dbFixture.Context, _mapperFixture.Mapper);
        }

        [Fact]
        public async Task Handle_WithValidId_ShouldReturnTag()
        {
            var query = new GetTagByIdQuery { TagId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.TagId.Should().Be(1);
            result.Name.Should().Be("Programming");
        }

        [Fact]
        public async Task Handle_WithInvalidId_ShouldReturnNull()
        {
            var query = new GetTagByIdQuery { TagId = 999 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().BeNull();
        }

        [Fact]
        public async Task Handle_ShouldIncludePostCount()
        {
            var query = new GetTagByIdQuery { TagId = 1 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.PostCount.Should().Be(2);
        }

        [Fact]
        public async Task Handle_ShouldMapAllProperties()
        {
            var query = new GetTagByIdQuery { TagId = 2 };

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result!.TagId.Should().Be(2);
            result.Name.Should().Be("Web Development");
            result.Slug.Should().Be("web-development");
            result.PostCount.Should().Be(2);
        }

        public void Dispose()
        {
            _dbFixture.Dispose();
        }
    }
}
