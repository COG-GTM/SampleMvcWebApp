using FluentAssertions;
using SampleWebApp.Api.Tests.Fixtures;
using SampleWebApp.Core.Handlers.Tags.Queries;
using Xunit;

namespace SampleWebApp.Api.Tests.Handlers.Tags
{
    public class GetTagsHandlerTests : IDisposable
    {
        private readonly TestDbContextFixture _dbFixture;
        private readonly AutoMapperFixture _mapperFixture;
        private readonly GetTagsHandler _handler;

        public GetTagsHandlerTests()
        {
            _dbFixture = new TestDbContextFixture();
            _mapperFixture = new AutoMapperFixture();
            _handler = new GetTagsHandler(_dbFixture.Context, _mapperFixture.Mapper);
        }

        [Fact]
        public async Task Handle_ShouldReturnAllTags()
        {
            var query = new GetTagsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);

            result.Should().NotBeNull();
            result.Should().HaveCount(3);
        }

        [Fact]
        public async Task Handle_ShouldReturnTagsOrderedByName()
        {
            var query = new GetTagsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var tagList = result.ToList();

            tagList[0].Name.Should().Be("Programming");
            tagList[1].Name.Should().Be("Science");
            tagList[2].Name.Should().Be("Web Development");
        }

        [Fact]
        public async Task Handle_ShouldIncludePostCount()
        {
            var query = new GetTagsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var programmingTag = result.First(t => t.Name == "Programming");
            var scienceTag = result.First(t => t.Name == "Science");

            programmingTag.PostCount.Should().Be(2);
            scienceTag.PostCount.Should().Be(1);
        }

        [Fact]
        public async Task Handle_ShouldMapAllProperties()
        {
            var query = new GetTagsQuery();

            var result = await _handler.Handle(query, CancellationToken.None);
            var programmingTag = result.First(t => t.Name == "Programming");

            programmingTag.TagId.Should().Be(1);
            programmingTag.Name.Should().Be("Programming");
            programmingTag.Slug.Should().Be("programming");
        }

        public void Dispose()
        {
            _dbFixture.Dispose();
        }
    }
}
