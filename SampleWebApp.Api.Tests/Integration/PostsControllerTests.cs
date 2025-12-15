using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using SampleWebApp.Core.DTOs;
using Xunit;

namespace SampleWebApp.Api.Tests.Integration
{
    public class PostsControllerTests : IClassFixture<CustomWebApplicationFactory>
    {
        private readonly HttpClient _client;

        public PostsControllerTests(CustomWebApplicationFactory factory)
        {
            _client = factory.CreateClient();
        }

        [Fact]
        public async Task GetPosts_ShouldReturnOkWithPosts()
        {
            var response = await _client.GetAsync("/api/posts");

            response.StatusCode.Should().Be(HttpStatusCode.OK);
            var posts = await response.Content.ReadFromJsonAsync<IEnumerable<SimplePostDto>>();
            posts.Should().NotBeNull();
            posts.Should().HaveCountGreaterThan(0);
        }

        [Fact]
        public async Task GetPosts_WithBlogIdFilter_ShouldReturnFilteredPosts()
        {
            var response = await _client.GetAsync("/api/posts?blogId=1");

            response.StatusCode.Should().Be(HttpStatusCode.OK);
            var posts = await response.Content.ReadFromJsonAsync<IEnumerable<SimplePostDto>>();
            posts.Should().NotBeNull();
            posts!.All(p => p.BlogId == 1).Should().BeTrue();
        }

        [Fact]
        public async Task GetPostById_WithValidId_ShouldReturnOkWithPost()
        {
            var response = await _client.GetAsync("/api/posts/1");

            response.StatusCode.Should().Be(HttpStatusCode.OK);
            var post = await response.Content.ReadFromJsonAsync<DetailPostDto>();
            post.Should().NotBeNull();
            post!.PostId.Should().Be(1);
        }

        [Fact]
        public async Task GetPostById_WithInvalidId_ShouldReturnNotFound()
        {
            var response = await _client.GetAsync("/api/posts/999");

            response.StatusCode.Should().Be(HttpStatusCode.NotFound);
        }

        [Fact]
        public async Task GetPostById_ShouldIncludeTags()
        {
            var response = await _client.GetAsync("/api/posts/1");

            response.StatusCode.Should().Be(HttpStatusCode.OK);
            var post = await response.Content.ReadFromJsonAsync<DetailPostDto>();
            post.Should().NotBeNull();
            post!.Tags.Should().NotBeEmpty();
        }
    }
}
