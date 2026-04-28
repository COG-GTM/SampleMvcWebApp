using System.Net;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;
using Xunit;

namespace SampleWebApp.IntegrationTests;

public class HomeControllerEndpointTests : IClassFixture<WebApplicationFactory<Program>>
{
    private readonly WebApplicationFactory<Program> _factory;

    public HomeControllerEndpointTests(WebApplicationFactory<Program> factory)
    {
        _factory = factory;
    }

    public static IEnumerable<object[]> Endpoints => new[]
    {
        new object[] { "/" },
        new object[] { "/Home" },
        new object[] { "/Home/Index" },
        new object[] { "/Home/About" },
        new object[] { "/Home/Contact" },
        new object[] { "/Home/Internals" },
        new object[] { "/Home/CodeView" }
    };

    [Theory]
    [MemberData(nameof(Endpoints))]
    public async Task Get_Endpoint_ReturnsSuccessAndHtml(string url)
    {
        using HttpClient client = _factory.CreateClient();

        using HttpResponseMessage response = await client.GetAsync(url);

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        response.Content.Headers.ContentType!.MediaType.Should().Be("text/html");
        string body = await response.Content.ReadAsStringAsync();
        body.Should().NotBeNullOrWhiteSpace();
    }

    [Fact]
    public async Task HealthEndpoint_ReturnsHealthy()
    {
        using HttpClient client = _factory.CreateClient();

        using HttpResponseMessage response = await client.GetAsync("/health");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        string body = await response.Content.ReadAsStringAsync();
        body.Should().Contain("Healthy");
    }

    [Fact]
    public async Task HealthLive_ReturnsHealthy()
    {
        using HttpClient client = _factory.CreateClient();

        using HttpResponseMessage response = await client.GetAsync("/health/live");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
    }

    [Fact]
    public async Task UnknownEndpoint_Returns404()
    {
        using HttpClient client = _factory.CreateClient(new WebApplicationFactoryClientOptions
        {
            AllowAutoRedirect = false
        });

        using HttpResponseMessage response = await client.GetAsync("/Home/DoesNotExist");

        response.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }
}
