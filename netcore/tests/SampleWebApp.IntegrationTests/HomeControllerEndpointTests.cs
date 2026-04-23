using System.Net;
using FluentAssertions;
using Microsoft.AspNetCore.Mvc.Testing;
using Xunit;

namespace SampleWebApp.IntegrationTests;

/// <summary>
/// Spins up an in-memory <c>TestServer</c> for the migrated .NET 6 web app
/// and exercises every <c>HomeController</c> endpoint the way a real browser
/// would.
/// </summary>
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
    public async Task About_ContainsDescriptionMessage()
    {
        using HttpClient client = _factory.CreateClient();

        string body = await client.GetStringAsync("/Home/About");

        body.Should().Contain("Your application description page.");
    }

    [Fact]
    public async Task Internals_ContainsRuntimeMetricsSection()
    {
        using HttpClient client = _factory.CreateClient();

        string body = await client.GetStringAsync("/Home/Internals");

        body.Should().Contain("Worker threads");
        body.Should().Contain("Available threads");
        body.Should().Contain("Available memory");
        body.Should().Contain("Heap memory used");
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
