using FluentAssertions;
using Microsoft.AspNetCore.Mvc;
using Moq;
using SampleWebApp.Controllers;
using SampleWebApp.Models;
using Xunit;

namespace SampleWebApp.UnitTests.Controllers;

public class HomeControllerTests
{
    private readonly Mock<IInternalsInfoProvider> _mockProvider;
    private readonly HomeController _controller;

    public HomeControllerTests()
    {
        _mockProvider = new Mock<IInternalsInfoProvider>();
        _mockProvider.Setup(p => p.GetMaxWorkerThreads()).Returns(32);
        _mockProvider.Setup(p => p.GetAvailableWorkerThreads()).Returns(30);
        _mockProvider.Setup(p => p.GetAvailableMemoryMbytes()).Returns(1024);
        _mockProvider.Setup(p => p.GetHeapMemoryUsedKbytes()).Returns(512);

        _controller = new HomeController(_mockProvider.Object);
    }

    [Fact]
    public void Index_ReturnsViewResult()
    {
        var result = _controller.Index();

        result.Should().BeOfType<ViewResult>();
    }

    [Fact]
    public void About_ReturnsViewWithMessage()
    {
        var result = _controller.About();

        var viewResult = result.Should().BeOfType<ViewResult>().Subject;
        string message = _controller.ViewBag.Message;
        message.Should().Be("Your application description page.");
    }

    [Fact]
    public void Contact_ReturnsViewResult()
    {
        var result = _controller.Contact();

        result.Should().BeOfType<ViewResult>();
    }

    [Fact]
    public void Internals_ReturnsViewWithModel()
    {
        var result = _controller.Internals();

        var viewResult = result.Should().BeOfType<ViewResult>().Subject;
        var model = viewResult.Model.Should().BeOfType<InternalsInfo>().Subject;
        model.WorkerThreads.Should().Be(32);
        model.AvailableThreads.Should().Be(30);
        model.AvailableMbytes.Should().Be(1024);
        model.HeapMemoryUsedKbytes.Should().Be(512);
    }

    [Fact]
    public void CodeView_ReturnsViewResult()
    {
        var result = _controller.CodeView();

        result.Should().BeOfType<ViewResult>();
    }

    [Fact]
    public void Constructor_ThrowsOnNullProvider()
    {
        var act = () => new HomeController(null!);

        act.Should().Throw<ArgumentNullException>();
    }
}
