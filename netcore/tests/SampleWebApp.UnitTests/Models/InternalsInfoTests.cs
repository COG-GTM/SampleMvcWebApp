using FluentAssertions;
using Moq;
using SampleWebApp.Models;
using Xunit;

namespace SampleWebApp.UnitTests.Models;

public class InternalsInfoTests
{
    [Fact]
    public void Constructor_WithProvider_PopulatesProperties()
    {
        var mock = new Mock<IInternalsInfoProvider>();
        mock.Setup(p => p.GetMaxWorkerThreads()).Returns(16);
        mock.Setup(p => p.GetAvailableWorkerThreads()).Returns(14);
        mock.Setup(p => p.GetAvailableMemoryMbytes()).Returns(2048);
        mock.Setup(p => p.GetHeapMemoryUsedKbytes()).Returns(256);

        var info = new InternalsInfo(mock.Object);

        info.WorkerThreads.Should().Be(16);
        info.AvailableThreads.Should().Be(14);
        info.AvailableMbytes.Should().Be(2048);
        info.HeapMemoryUsedKbytes.Should().Be(256);
    }

    [Fact]
    public void Constructor_WithNullProvider_Throws()
    {
        var act = () => new InternalsInfo(null!);

        act.Should().Throw<ArgumentNullException>();
    }

    [Fact]
    public void DefaultConstructor_UsesRealProvider()
    {
        var info = new InternalsInfo();

        info.WorkerThreads.Should().BeGreaterThan(0);
        info.AvailableThreads.Should().BeGreaterOrEqualTo(0);
    }
}
