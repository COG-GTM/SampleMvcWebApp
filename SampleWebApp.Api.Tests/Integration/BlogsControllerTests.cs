using System.Net;
using System.Net.Http.Json;
using FluentAssertions;
using SampleWebApp.Core.DTOs;
using Xunit;

namespace SampleWebApp.Api.Tests.Integration
{
    public class BlogsControllerTests : IClassFixture<CustomWebApplicationFactory>
    {
        private readonly HttpClient _client;

        public BlogsControllerTests(CustomWebApplicationFactory factory)
        {
            _client = factory.CreateClient();
        }

        [Fact]
        public async Task GetBlogs_ShouldReturnOkWithBlogs()
        {
            var response = await _client.GetAsync("/api/blogs");

            response.StatusCode.Should().Be(HttpStatusCode.OK);
            var blogs = await response.Content.ReadFromJsonAsync<IEnumerable<BlogDto>>();
            blogs.Should().NotBeNull();
            blogs.Should().HaveCountGreaterThan(0);
        }

        [Fact]
        public async Task GetBlogById_WithValidId_ShouldReturnOkWithBlog()
        {
            var response = await _client.GetAsync("/api/blogs/1");

            response.StatusCode.Should().Be(HttpStatusCode.OK);
            var blog = await response.Content.ReadFromJsonAsync<BlogDto>();
            blog.Should().NotBeNull();
            blog!.BlogId.Should().Be(1);
        }

        [Fact]
        public async Task GetBlogById_WithInvalidId_ShouldReturnNotFound()
        {
            var response = await _client.GetAsync("/api/blogs/999");

            response.StatusCode.Should().Be(HttpStatusCode.NotFound);
        }
    }
}
