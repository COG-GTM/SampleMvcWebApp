using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using SampleWebApp.Core.DTOs;
using Xunit;

namespace SampleWebApp.Api.Tests.Integration
{
    public class TagsControllerTests : IClassFixture<CustomWebApplicationFactory>
    {
        private readonly HttpClient _client;

        public TagsControllerTests(CustomWebApplicationFactory factory)
        {
            _client = factory.CreateClient();
        }

        [Fact]
        public async Task GetTags_ShouldReturnOkWithTags()
        {
            var response = await _client.GetAsync("/api/tags");

            response.StatusCode.Should().Be(HttpStatusCode.OK);
            var tags = await response.Content.ReadFromJsonAsync<IEnumerable<TagDto>>();
            tags.Should().NotBeNull();
            tags.Should().HaveCountGreaterThan(0);
        }

        [Fact]
        public async Task GetTagById_WithValidId_ShouldReturnOkWithTag()
        {
            var response = await _client.GetAsync("/api/tags/1");

            response.StatusCode.Should().Be(HttpStatusCode.OK);
            var tag = await response.Content.ReadFromJsonAsync<TagDto>();
            tag.Should().NotBeNull();
            tag!.TagId.Should().Be(1);
        }

        [Fact]
        public async Task GetTagById_WithInvalidId_ShouldReturnNotFound()
        {
            var response = await _client.GetAsync("/api/tags/999");

            response.StatusCode.Should().Be(HttpStatusCode.NotFound);
        }
    }
}
