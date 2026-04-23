using System;
using System.Threading;

namespace SampleWebApp.Models;

/// <summary>
/// Snapshot of runtime internals used by the Home/Internals diagnostics page.
/// </summary>
/// <remarks>
/// The original MVC5 implementation relied on the Windows-only
/// <c>PerformanceCounter("Memory", "Available MBytes")</c>. To keep the
/// migrated controller cross-platform the "available memory" number is now
/// sourced from <see cref="GC.GetGCMemoryInfo"/> which is available on all
/// .NET 6 runtimes.
/// </remarks>
public sealed class InternalsInfo
{
    public int WorkerThreads { get; }

    public int AvailableThreads { get; }

    public int AvailableMbytes { get; }

    public int HeapMemoryUsedKbytes { get; }

    public InternalsInfo()
        : this(new InternalsInfoProvider())
    {
    }

    public InternalsInfo(IInternalsInfoProvider provider)
    {
        ArgumentNullException.ThrowIfNull(provider);

        WorkerThreads = provider.GetMaxWorkerThreads();
        AvailableThreads = provider.GetAvailableWorkerThreads();
        AvailableMbytes = provider.GetAvailableMemoryMbytes();
        HeapMemoryUsedKbytes = provider.GetHeapMemoryUsedKbytes();
    }
}

/// <summary>
/// Abstraction over runtime counters so that <see cref="InternalsInfo"/> is
/// fully unit-testable without depending on the live thread pool or GC state.
/// </summary>
public interface IInternalsInfoProvider
{
    int GetMaxWorkerThreads();
    int GetAvailableWorkerThreads();
    int GetAvailableMemoryMbytes();
    int GetHeapMemoryUsedKbytes();
}

/// <summary>Default <see cref="IInternalsInfoProvider"/> backed by BCL APIs.</summary>
public sealed class InternalsInfoProvider : IInternalsInfoProvider
{
    public int GetMaxWorkerThreads()
    {
        ThreadPool.GetMaxThreads(out int workerThreads, out _);
        return workerThreads;
    }

    public int GetAvailableWorkerThreads()
    {
        ThreadPool.GetAvailableThreads(out int availableThreads, out _);
        return availableThreads;
    }

    public int GetAvailableMemoryMbytes()
    {
        GCMemoryInfo info = GC.GetGCMemoryInfo();
        long availableBytes = Math.Max(0, info.TotalAvailableMemoryBytes - info.MemoryLoadBytes);
        return (int)Math.Min(int.MaxValue, availableBytes / (1024L * 1024L));
    }

    public int GetHeapMemoryUsedKbytes()
    {
        return (int)(GC.GetTotalMemory(forceFullCollection: false) / 1000L);
    }
}
