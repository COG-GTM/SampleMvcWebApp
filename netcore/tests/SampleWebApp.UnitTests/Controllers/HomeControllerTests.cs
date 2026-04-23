using FluentAssertions;
using Microsoft.AspNetCore.Mvc;
using Moq;
using SampleWebApp.Controllers;
using SampleWebApp.Models;
using Xunit;

namespace SampleWebApp.UnitTests.Controllers;

public class HomeControllerTests
{
    private readonly Mock<IInternalsInfoProvider> _providerMock = new(MockBehavior.Strict);

    private HomeController CreateSut() => new(_providerMock.Object);

    [Fact]
    public void Constructor_NullProvider_ThrowsArgumentNullException()
    {
        Action act = () => _ = new HomeController(null!);

        act.Should()
            .Throw<ArgumentNullException>()
            .WithParameterName("internalsInfoProvider");
    }

    [Fact]
    public void Index_Returns_DefaultView()
    {
        var sut = CreateSut();

        var result = sut.Index();

        result.Should().BeOfType<ViewResult>()
            .Which.ViewName.Should().BeNull("the controller relies on view discovery by action name");
    }

    [Fact]
    public void About_Sets_Message_And_Returns_DefaultView()
    {
        var sut = CreateSut();

        var result = sut.About();

        var viewResult = result.Should().BeOfType<ViewResult>().Subject;
        viewResult.ViewName.Should().BeNull();
        ((string?)viewResult.ViewData["Message"]).Should().Be("Your application description page.");
    }

    [Fact]
    public void Contact_Returns_DefaultView()
    {
        var sut = CreateSut();

        var result = sut.Contact();

        result.Should().BeOfType<ViewResult>()
            .Which.ViewName.Should().BeNull();
    }

    [Fact]
    public void Internals_Returns_ViewWithInternalsInfoModel_UsingProvider()
    {
        _providerMock.Setup(p => p.GetMaxWorkerThreads()).Returns(32);
        _providerMock.Setup(p => p.GetAvailableWorkerThreads()).Returns(16);
        _providerMock.Setup(p => p.GetAvailableMemoryMbytes()).Returns(2048);
        _providerMock.Setup(p => p.GetHeapMemoryUsedKbytes()).Returns(4096);

        var sut = CreateSut();

        var result = sut.Internals();

        var viewResult = result.Should().BeOfType<ViewResult>().Subject;
        var model = viewResult.Model.Should().BeOfType<InternalsInfo>().Subject;
        model.WorkerThreads.Should().Be(32);
        model.AvailableThreads.Should().Be(16);
        model.AvailableMbytes.Should().Be(2048);
        model.HeapMemoryUsedKbytes.Should().Be(4096);
        _providerMock.VerifyAll();
    }

    [Fact]
    public void CodeView_Returns_DefaultView()
    {
        var sut = CreateSut();

        var result = sut.CodeView();

        result.Should().BeOfType<ViewResult>()
            .Which.ViewName.Should().BeNull();
    }
}
