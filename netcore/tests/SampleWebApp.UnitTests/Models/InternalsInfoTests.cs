using FluentAssertions;
using Moq;
using SampleWebApp.Models;
using Xunit;

namespace SampleWebApp.UnitTests.Models;

public class InternalsInfoTests
{
    [Fact]
    public void Constructor_NullProvider_ThrowsArgumentNullException()
    {
        Action act = () => _ = new InternalsInfo((IInternalsInfoProvider)null!);

        act.Should().Throw<ArgumentNullException>();
    }

    [Fact]
    public void Constructor_PopulatesAllPropertiesFromProvider()
    {
        var provider = new Mock<IInternalsInfoProvider>(MockBehavior.Strict);
        provider.Setup(p => p.GetMaxWorkerThreads()).Returns(100);
        provider.Setup(p => p.GetAvailableWorkerThreads()).Returns(50);
        provider.Setup(p => p.GetAvailableMemoryMbytes()).Returns(1024);
        provider.Setup(p => p.GetHeapMemoryUsedKbytes()).Returns(2048);

        var info = new InternalsInfo(provider.Object);

        info.WorkerThreads.Should().Be(100);
        info.AvailableThreads.Should().Be(50);
        info.AvailableMbytes.Should().Be(1024);
        info.HeapMemoryUsedKbytes.Should().Be(2048);
        provider.VerifyAll();
    }

    [Fact]
    public void DefaultConstructor_UsesRealProvider_ProducesSaneNumbers()
    {
        var info = new InternalsInfo();

        info.WorkerThreads.Should().BePositive();
        info.AvailableThreads.Should().BeInRange(0, info.WorkerThreads);
        info.AvailableMbytes.Should().BeGreaterOrEqualTo(0);
        info.HeapMemoryUsedKbytes.Should().BeGreaterThan(0);
    }
}

public class InternalsInfoProviderTests
{
    private readonly InternalsInfoProvider _sut = new();

    [Fact]
    public void GetMaxWorkerThreads_ReturnsPositiveCount()
    {
        _sut.GetMaxWorkerThreads().Should().BePositive();
    }

    [Fact]
    public void GetAvailableWorkerThreads_IsWithinMax()
    {
        int max = _sut.GetMaxWorkerThreads();
        int available = _sut.GetAvailableWorkerThreads();

        available.Should().BeInRange(0, max);
    }

    [Fact]
    public void GetAvailableMemoryMbytes_IsNonNegative()
    {
        _sut.GetAvailableMemoryMbytes().Should().BeGreaterOrEqualTo(0);
    }

    [Fact]
    public void GetHeapMemoryUsedKbytes_IsPositive()
    {
        _sut.GetHeapMemoryUsedKbytes().Should().BeGreaterThan(0);
    }
}
